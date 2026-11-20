package com.mogutofu.mcai.bukkit;

import com.mogutofu.mcai.bridge.BridgeProtocol;
import com.mogutofu.mcai.bridge.BridgeTokens;
import com.mogutofu.mcai.core.Attachment;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * {@code mcai:bridge} 通道的服务端：收 HELLO / ABORT / DONE，发 INFO / REQUEST。
 *
 * <p>整体流程：</p>
 * <ol>
 *   <li>客户端登录后发 HELLO（raw plugin message，不依赖模组通道注册）；</li>
 *   <li>插件回 INFO（端口/host/上限），HTTP 上传端口必须已经监听；</li>
 *   <li>玩家 {@code /ai screenshot|file} 时，插件发 INFO（一次性 token）+ REQUEST；</li>
 *   <li>客户端把字节 POST 到上传端口，{@link BridgeUploadServer} 校验后回调这里；</li>
 *   <li>这里切回主线程，把字节交给 {@code AiClient}（和模组版完全同一条 HTTP 契约）。</li>
 * </ol>
 *
 * <p>任何一步失败都只影响这一次附件：文字 {@code /ai} 照旧，绝不崩服。</p>
 */
public final class BridgeChannel implements PluginMessageListener, BridgeUploadServer.Listener {

    private final McaiPlugin plugin;
    private final BridgeConfig config;
    private final BridgeTokens tokens = new BridgeTokens();
    private final PendingUploads pending = new PendingUploads();

    /** 已经发过 HELLO 的玩家（UUID → loader 名）。 */
    private final Set<UUID> bridged = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());
    private final Map<UUID, String> loaders = new ConcurrentHashMap<UUID, String>();

    private BridgeUploadServer server;

    BridgeChannel(McaiPlugin plugin, BridgeConfig config) {
        this.plugin = plugin;
        this.config = config;
    }

    // ── 生命周期 ─────────────────────────────────────────────────────

    /**
     * 启动桥接：先绑 HTTP 端口，成功了才注册通道（端口失败 = 桥接整体禁用）。
     *
     * @return 是否可用
     */
    boolean start() {
        if (!config.enabled) {
            plugin.getLogger().info("附件桥接已关闭（config.yml attachment.enabled=false），"
                    + "/ai screenshot、/ai file 会降级为只发文字");
            return false;
        }
        if (!config.httpTransport()) {
            plugin.getLogger().warning("附件桥接 transport=" + config.transport + " 不支持，只支持 http；"
                    + "桥接已禁用，文字 /ai 不受影响");
            return false;
        }
        BridgeUploadServer created = new BridgeUploadServer(config, tokens, this);
        try {
            created.start();
        } catch (Throwable error) {
            plugin.getLogger().severe("附件上传端口 " + config.bind + ":" + config.port
                    + " 绑定失败：" + error + "；桥接已禁用，文字 /ai、/weather、/vote 不受影响。"
                    + "换个端口（config.yml attachment.port）或检查防火墙/端口占用。");
            return false;
        }
        server = created;
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, BridgeProtocol.CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, BridgeProtocol.CHANNEL, this);
        plugin.getLogger().info("附件桥接已启用（通道 " + BridgeProtocol.CHANNEL + "，协议 "
                + BridgeProtocol.VERSION + "，上传端口 " + config.port
                + "，advertise-host=" + (config.advertiseHost.isEmpty() ? "(客户端连接地址)" : config.advertiseHost)
                + "）");
        return true;
    }

    void stop() {
        if (server != null) {
            server.stop();
            server = null;
        }
        try {
            plugin.getServer().getMessenger().unregisterIncomingPluginChannel(plugin, BridgeProtocol.CHANNEL);
            plugin.getServer().getMessenger().unregisterOutgoingPluginChannel(plugin, BridgeProtocol.CHANNEL);
        } catch (Throwable ignored) {
            // 已经注销过
        }
        bridged.clear();
        loaders.clear();
        pending.clear();
        tokens.clear();
    }

    boolean isAvailable() {
        return server != null && server.isRunning();
    }

    /** 这个玩家能不能走桥接（发过 HELLO 且端口在监听）。 */
    boolean isBridged(Player player) {
        return player != null && isAvailable() && bridged.contains(player.getUniqueId());
    }

    /** 每 tick 的收尾：超时会话降级 + 过期 token 清理。 */
    void tick() {
        long now = System.currentTimeMillis();
        List<PendingUploads.Pending> expired = pending.expired(now);
        for (PendingUploads.Pending item : expired) {
            plugin.getLogger().info("附件传输超时（15 秒）：" + item.playerName + " transfer=" + item.transferId);
            fallbackToText(item, "附件传输超时（15 秒），已改为只发送消息。");
        }
        tokens.purge(now);
    }

    void forget(Player player) {
        if (player == null) {
            return;
        }
        UUID playerId = player.getUniqueId();
        bridged.remove(playerId);
        loaders.remove(playerId);
        tokens.revokePlayer(playerId);
        PendingUploads.Pending pendingUpload = pending.removeByPlayer(playerId);
        if (pendingUpload != null) {
            plugin.getLogger().info("玩家下线，丢弃等待中的附件（" + player.getName() + "）");
        }
    }

    // ── 发请求（/ai screenshot、/ai file 调用） ──────────────────────

    /**
     * 让客户端发一次附件。
     *
     * @return {@code false} 表示这个玩家没有桥接，调用方应降级为只发文字
     */
    boolean requestAttachment(Player player, boolean image, String path, String text) {
        if (!isBridged(player)) {
            return false;
        }
        UUID playerId = player.getUniqueId();
        UUID transferId = UUID.randomUUID();
        long now = System.currentTimeMillis();
        int maxBytes = image ? config.maxImageBytes : config.maxFileBytes;
        pending.add(playerId, player.getName(), transferId, text, image, now);
        String token = tokens.issue(playerId, transferId, config.tokenTtlSeconds, now);
        try {
            sendInfo(player, token);
            player.sendPluginMessage(plugin, BridgeProtocol.CHANNEL,
                    BridgeProtocol.request(transferId, image ? BridgeProtocol.KIND_SCREENSHOT : BridgeProtocol.KIND_FILE,
                            path == null ? "" : path, maxBytes));
            return true;
        } catch (RuntimeException error) {
            pending.take(transferId);
            plugin.getLogger().warning("向 " + player.getName() + " 发送附件请求失败：" + error);
            return false;
        }
    }

    private void sendInfo(Player player, String token) {
        player.sendPluginMessage(plugin, BridgeProtocol.CHANNEL, BridgeProtocol.info(
                config.effectiveAdvertisePort(),
                config.advertiseHost,
                token,
                config.tokenTtlSeconds,
                config.maxImageBytes,
                config.maxFileBytes));
    }

    // ── 通道消息 ─────────────────────────────────────────────────────

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!BridgeProtocol.CHANNEL.equals(channel) || player == null || message == null) {
            return;
        }
        try {
            byte type = BridgeProtocol.typeOf(message);
            switch (type) {
                case BridgeProtocol.TYPE_HELLO:
                    onHello(player, BridgeProtocol.parseHello(message));
                    break;
                case BridgeProtocol.TYPE_ABORT:
                    onAbort(player, BridgeProtocol.parseAbort(message));
                    break;
                case BridgeProtocol.TYPE_DONE:
                    onDone(player, BridgeProtocol.parseDone(message));
                    break;
                default:
                    plugin.getLogger().warning("收到未知的 mcai:bridge 帧类型 " + type + "（玩家 " + player.getName() + "）");
                    break;
            }
        } catch (IllegalArgumentException bad) {
            plugin.getLogger().warning("收到的 mcai:bridge 帧不合法（玩家 " + player.getName() + "）："
                    + bad.getMessage());
        } catch (RuntimeException error) {
            plugin.getLogger().warning("处理 mcai:bridge 消息失败（玩家 " + player.getName() + "）：" + error);
        }
    }

    private void onHello(Player player, BridgeProtocol.Hello hello) {
        if (!isAvailable()) {
            // 端口没起来：不回 INFO，客户端会保持「无桥接」，/ai screenshot 走文字降级
            return;
        }
        bridged.add(player.getUniqueId());
        loaders.put(player.getUniqueId(), hello.loader + "/" + hello.clientVersion);
        plugin.getLogger().info("客户端 " + player.getName() + " 接入附件桥接（" + hello.loader
                + " " + hello.clientVersion + "，协议 " + BridgeProtocol.VERSION + "）");
        // 先给一条不可用于上传的 INFO（token 绑定一个不会出现的 transferId），
        // 真正的 token 在每次 REQUEST 前随 INFO 下发。
        String placeholder = tokens.issue(player.getUniqueId(), UUID.randomUUID(),
                config.tokenTtlSeconds, System.currentTimeMillis());
        try {
            sendInfo(player, placeholder);
        } catch (RuntimeException error) {
            plugin.getLogger().warning("回复 INFO 失败（玩家 " + player.getName() + "）：" + error);
        }
    }

    private void onAbort(Player player, BridgeProtocol.Abort abort) {
        PendingUploads.Pending item = pending.take(abort.transferId);
        if (item == null || !item.playerId.equals(player.getUniqueId())) {
            return;
        }
        plugin.getLogger().info("客户端放弃附件（" + player.getName() + "）：" + abort.reason);
        String reason = abort.reason == null || abort.reason.trim().isEmpty() ? "未知原因" : abort.reason.trim();
        fallbackToText(item, "附件失败：" + reason + "；已改为只发送消息。");
    }

    private void onDone(Player player, BridgeProtocol.Done done) {
        if (done.ok) {
            // 正常路径由 HTTP 完成回调负责；这里只记一行日志
            plugin.getLogger().info("客户端确认附件上传完成（" + player.getName() + "，transfer="
                    + done.transferId + "）");
            return;
        }
        PendingUploads.Pending item = pending.take(done.transferId);
        if (item == null || !item.playerId.equals(player.getUniqueId())) {
            return;
        }
        String reason = done.error == null || done.error.trim().isEmpty() ? "未知原因" : done.error.trim();
        plugin.getLogger().info("客户端上传失败（" + player.getName() + "）：" + reason);
        fallbackToText(item, "附件上传失败：" + reason + "；已改为只发送消息。");
    }

    // ── HTTP 回调（HTTP 线程）────────────────────────────────────────

    @Override
    public PendingUploads.Pending pendingFor(UUID transferId) {
        return pending.byTransfer(transferId);
    }

    @Override
    public void onUpload(final UUID playerId, final UUID transferId, final boolean image,
                         final String fileName, final String mediaType, final byte[] data) {
        if (!plugin.isEnabled()) {
            return;
        }
        // 字节已经在 HTTP 线程读完了，回主线程再动 Bukkit API / AiClient
        Bukkit.getScheduler().runTask(plugin,
                () -> deliver(playerId, transferId, image, fileName, mediaType, data));
    }

    private void deliver(UUID playerId, UUID transferId, boolean image, String fileName,
                         String mediaType, byte[] data) {
        PendingUploads.Pending item = pending.take(transferId);
        if (item == null || !item.playerId.equals(playerId)) {
            plugin.getLogger().warning("HTTP 收到附件但没有匹配的请求（transfer=" + transferId + "），已丢弃");
            return;
        }
        plugin.getLogger().info("附件已就绪：" + fileName + "（" + data.length + " 字节，" + mediaType
                + "，玩家 " + item.playerName + "）");
        Player player = Bukkit.getPlayer(playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage("[小铜] 收到附件了，正在看…");
        }
        plugin.askAi(playerId, item.playerName, item.text, new Attachment(fileName, mediaType, image, data));
    }

    /**
     * 附件失败/超时 → 按 1.3.0 的行为「只发文字」，并提示一句。
     */
    private void fallbackToText(PendingUploads.Pending item, String notice) {
        Player player = Bukkit.getPlayer(item.playerId);
        if (player != null && player.isOnline()) {
            player.sendMessage("[小铜] " + notice);
        }
        plugin.askAi(item.playerId, item.playerName, item.text, null);
    }

    @Override
    public void log(String message) {
        plugin.getLogger().info("[bridge] " + message);
    }

    // ── 供命令/调试 ──────────────────────────────────────────────────

    BridgeConfig config() {
        return config;
    }

    PendingUploads pendingUploads() {
        return pending;
    }

    int bridgedCount() {
        return bridged.size();
    }

    String loaderOf(UUID playerId) {
        return loaders.get(playerId);
    }
}
