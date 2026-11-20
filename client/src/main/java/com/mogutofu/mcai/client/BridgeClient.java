package com.mogutofu.mcai.client;

import com.mogutofu.mcai.bridge.BridgeProtocol;
import com.mogutofu.mcai.bridge.BridgeUpload;
import com.mojang.blaze3d.platform.NativeImage;
import io.netty.buffer.Unpooled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.game.ServerboundCustomPayloadPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.event.EventNetworkChannel;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Bukkit 附件桥接的客户端（Forge 1.20.1）。
 *
 * <p>只服务一种场景：服务器只装了 Bukkit 插件、没有 mcai 服务端模组。此时客户端仍然：</p>
 * <ol>
 *   <li>登录后主动发 {@code minecraft:register}（Paper 1.20.1 只在客户端声明过通道后才会
 *       下发插件消息）+ raw {@code mcai:bridge} HELLO；</li>
 *   <li>收到 INFO 记下上传端口 / advertise-host / 一次性 token；</li>
 *   <li>收到 REQUEST 后在本机截图或读文件，HTTP POST 到插件端口，再回 DONE / ABORT。</li>
 * </ol>
 *
 * <p>原有 {@code mcai:net} 通道一行没动：桥接只是新增分支，模组服照旧走
 * {@link AttachmentTransfer}。</p>
 */
public final class BridgeClient {

    /** 通道固定 mcai:bridge。 */
    public static final ResourceLocation CHANNEL = new ResourceLocation("mcai", "bridge");

    /** 客户端版本号，用于 HELLO 里告诉插件（也方便看日志）。 */
    public static final String CLIENT_VERSION = "1.4.0";

    private static final String REGISTER_CHANNEL = "minecraft:register";
    private static final byte[] REGISTER_PAYLOAD =
            (BridgeProtocol.CHANNEL + "\u0000").getBytes(StandardCharsets.UTF_8);

    /** 2 秒内没收到 INFO 就认为服务端没有桥接（和任务约定一致）。 */
    private static final long INFO_WAIT_MS = 2_000L;

    private static final ExecutorService UPLOAD_POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mcai-bridge-upload");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile EventNetworkChannel channel;
    private static volatile Connection connection;
    private static volatile boolean ready;

    private static volatile int port;
    private static volatile String advertiseHost = "";
    private static volatile String token = "";
    private static volatile int maxImageBytes = 8 * 1024 * 1024;
    private static volatile int maxFileBytes = 16 * 1024 * 1024;
    private static volatile int timeoutSeconds = 30;
    private static volatile long infoAtMillis;

    private BridgeClient() {
    }

    /** 由 {@link McaiClientMod} 在客户端构造时通过 DistExecutor 调用。 */
    public static synchronized void register() {
        if (ready) {
            return;
        }
        EventNetworkChannel created = NetworkRegistry.newEventChannel(
                CHANNEL,
                () -> String.valueOf(BridgeProtocol.VERSION),
                NetworkRegistry.acceptMissingOr(String.valueOf(BridgeProtocol.VERSION)),
                NetworkRegistry.acceptMissingOr(String.valueOf(BridgeProtocol.VERSION)));
        created.addListener((Consumer<NetworkEvent.ClientCustomPayloadEvent>) BridgeClient::onPayload);
        channel = created;
        ready = true;
        McaiClientMod.LOGGER.info("[mcai-client] 已注册附件桥接通道 {}（协议 {}）",
                CHANNEL, BridgeProtocol.VERSION);
    }

    public static boolean isReady() {
        return ready;
    }

    /** 登录成功后：声明监听通道 + 发 HELLO。由 {@link ClientEvents} 调用。 */
    public static void onLogin(Connection connection) {
        BridgeClient.connection = connection;
        if (!ready) {
            McaiClientMod.LOGGER.warn("[mcai-client] 桥接通道未注册，跳过 HELLO");
            return;
        }
        try {
            if (connection == null) {
                return;
            }
            // Paper 1.20.1 的 CraftPlayer#sendPluginMessage 只发给「客户端声明过」的通道；
            // 原版协议里由 minecraft:register 声明。这里和 HELLO 一起发，缺一不可。
            sendRaw(new ResourceLocation(REGISTER_CHANNEL), REGISTER_PAYLOAD);
            sendRaw(CHANNEL, BridgeProtocol.hello("forge", CLIENT_VERSION));
            McaiClientMod.LOGGER.info("[mcai-client] 已发送附件桥接 HELLO（forge {}）", CLIENT_VERSION);
        } catch (Throwable error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 发送附件桥接 HELLO 失败：{}", error.toString());
        }
    }

    // ── 收 ────────────────────────────────────────────────────────────

    private static void onPayload(NetworkEvent.ClientCustomPayloadEvent event) {
        try {
            FriendlyByteBuf payload = event.getPayload();
            byte[] frame = new byte[payload.readableBytes()];
            payload.readBytes(frame);
            event.getSource().get().setPacketHandled(true);

            byte type = BridgeProtocol.typeOf(frame);
            if (type == BridgeProtocol.TYPE_INFO) {
                BridgeProtocol.Info info = BridgeProtocol.parseInfo(frame);
                Minecraft.getInstance().execute(() -> applyInfo(info));
            } else if (type == BridgeProtocol.TYPE_REQUEST) {
                BridgeProtocol.Request request = BridgeProtocol.parseRequest(frame);
                Minecraft.getInstance().execute(() -> handleRequest(request));
            } else {
                McaiClientMod.LOGGER.warn("[mcai-client] 忽略未知桥接帧类型 {}", type);
            }
        } catch (IllegalArgumentException broken) {
            McaiClientMod.LOGGER.warn("[mcai-client] 桥接帧不合法：{}", broken.getMessage());
        } catch (RuntimeException error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 处理桥接帧失败", error);
        }
    }

    private static void applyInfo(BridgeProtocol.Info info) {
        port = info.port;
        advertiseHost = info.advertiseHost == null ? "" : info.advertiseHost;
        token = info.token == null ? "" : info.token;
        maxImageBytes = info.maxImageBytes > 0 ? info.maxImageBytes : 8 * 1024 * 1024;
        maxFileBytes = info.maxFileBytes > 0 ? info.maxFileBytes : 16 * 1024 * 1024;
        infoAtMillis = System.currentTimeMillis();
        McaiClientMod.LOGGER.info("[mcai-client] 附件桥接可用：端口 {}，advertise-host='{}'，上限 {}/{} 字节",
                port, advertiseHost, maxImageBytes, maxFileBytes);
    }

    // ── 处理 REQUEST（客户端线程） ────────────────────────────────────

    private static void handleRequest(BridgeProtocol.Request request) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            abort(request.transferId, "已经不在服务器里了");
            return;
        }
        if (port <= 0 || token.isEmpty()) {
            abort(request.transferId, "没有收到桥接 INFO（端口/token 缺失）");
            return;
        }
        try {
            if (request.kind == BridgeProtocol.KIND_SCREENSHOT) {
                captureScreenshot(request);
            } else if (request.kind == BridgeProtocol.KIND_FILE) {
                readFile(request);
            } else {
                abort(request.transferId, "未知的附件类型：" + request.kind);
            }
        } catch (Throwable error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 桥接附件处理失败", error);
            abort(request.transferId, describe(error));
        }
    }

    private static void captureScreenshot(BridgeProtocol.Request request) throws Exception {
        Minecraft minecraft = Minecraft.getInstance();
        NativeImage image = Screenshot.takeScreenshot(minecraft.getMainRenderTarget());
        if (image == null) {
            abort(request.transferId, "截图失败：没有拿到画面");
            return;
        }
        byte[] data;
        try {
            data = image.asByteArray();
        } finally {
            image.close();
        }
        if (data == null || data.length == 0) {
            abort(request.transferId, "截图失败：PNG 数据为空");
            return;
        }
        if (data.length > request.maxBytes) {
            abort(request.transferId, "截图超过上限（" + data.length + " > " + request.maxBytes + " 字节）");
            return;
        }
        McaiClientMod.LOGGER.info("[mcai-client] 桥接截图 {} 字节，准备上传", data.length);
        uploadAsync(request, true, "screenshot.png", "image/png", data);
    }

    private static void readFile(BridgeProtocol.Request request) throws Exception {
        Path path;
        try {
            path = Paths.get(request.path);
        } catch (InvalidPathException invalid) {
            abort(request.transferId, "路径不合法：" + invalid.getMessage());
            return;
        }
        if (!Files.isRegularFile(path)) {
            abort(request.transferId, "文件不存在或不是普通文件：" + request.path);
            return;
        }
        long size = Files.size(path);
        if (size > request.maxBytes) {
            abort(request.transferId, "文件超过上限（" + size + " > " + request.maxBytes + " 字节）");
            return;
        }
        byte[] data = Files.readAllBytes(path);
        if (data.length > request.maxBytes) {
            abort(request.transferId, "文件超过上限（" + data.length + " > " + request.maxBytes + " 字节）");
            return;
        }
        String fileName = path.getFileName() == null ? "file.bin" : path.getFileName().toString();
        String mediaType = mediaTypeOf(fileName);
        McaiClientMod.LOGGER.info("[mcai-client] 桥接读取文件 {}（{} 字节，{}）", request.path, data.length, mediaType);
        uploadAsync(request, mediaType.startsWith("image/"), fileName, mediaType, data);
    }

    // ── 上传（后台线程） ──────────────────────────────────────────────

    private static void uploadAsync(final BridgeProtocol.Request request, final boolean image,
                                    final String fileName, final String mediaType, final byte[] data) {
        final String host = BridgeUpload.resolveHost(advertiseHost, serverHost());
        final int targetPort = port;
        final String targetToken = token;
        final int timeout = timeoutSeconds;
        UPLOAD_POOL.execute(() -> {
            BridgeUpload.Result result = BridgeUpload.post(host, targetPort, targetToken, request.transferId,
                    image, fileName, mediaType, data, timeout);
            Minecraft minecraft = Minecraft.getInstance();
            minecraft.execute(() -> {
                if (result.ok) {
                    McaiClientMod.LOGGER.info("[mcai-client] 附件上传成功（{} {} 字节 → {}:{}）",
                            fileName, data.length, host, targetPort);
                    sendRaw(CHANNEL, BridgeProtocol.done(request.transferId, true, ""));
                } else {
                    McaiClientMod.LOGGER.warn("[mcai-client] 附件上传失败（HTTP {}）：{}", result.status, result.error);
                    if (result.status == 403 || result.status == 409) {
                        // token/pending 失配：请求新的 INFO 后由服务端重新发起
                        refreshInfo();
                    }
                    abort(request.transferId, result.error);
                }
            });
        });
    }

    /** token 失效时悄悄要一份新的 INFO，下一次 /ai screenshot 就能用。 */
    private static void refreshInfo() {
        try {
            sendRaw(CHANNEL, BridgeProtocol.hello("forge", CLIENT_VERSION));
        } catch (RuntimeException ignored) {
            // 下次再说
        }
    }

    // ── 发包 / 收尾 ──────────────────────────────────────────────────

    private static void sendRaw(ResourceLocation target, byte[] bytes) {
        Connection current = connection;
        if (current == null) {
            throw new IllegalStateException("不在服务器里");
        }
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.wrappedBuffer(bytes));
        current.send(new ServerboundCustomPayloadPacket(target, buffer));
    }

    private static void abort(UUID transferId, String reason) {
        String safe = reason == null || reason.trim().isEmpty() ? "未知原因" : reason.trim();
        McaiClientMod.LOGGER.info("[mcai-client] 桥接附件取消：{}", safe);
        try {
            sendRaw(CHANNEL, BridgeProtocol.abort(transferId, safe));
        } catch (RuntimeException error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 发送 ABORT 失败：{}", error.toString());
        }
    }

    /** 客户端连接的服务器地址（advertise-host 为空时用它）。 */
    private static String serverHost() {
        try {
            Connection current = connection;
            if (current != null && current.getRemoteAddress() instanceof InetSocketAddress) {
                InetSocketAddress inet = (InetSocketAddress) current.getRemoteAddress();
                if (inet.getAddress() != null) {
                    return inet.getAddress().getHostAddress();
                }
                return inet.getHostString();
            }
        } catch (Throwable ignored) {
            // 单机/局域网
        }
        return "127.0.0.1";
    }

    static String hostOnly(String address) {
        if (address == null) {
            return "";
        }
        String value = address.trim();
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            return close > 0 ? value.substring(1, close) : value;
        }
        int colon = value.lastIndexOf(':');
        if (colon > 0 && value.indexOf(':') == colon) {
            String maybePort = value.substring(colon + 1);
            if (!maybePort.isEmpty() && maybePort.chars().allMatch(Character::isDigit)) {
                return value.substring(0, colon);
            }
        }
        return value;
    }

    private static String mediaTypeOf(String fileName) {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".png")) {
            return "image/png";
        }
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) {
            return "image/jpeg";
        }
        if (lower.endsWith(".webp")) {
            return "image/webp";
        }
        if (lower.endsWith(".gif")) {
            return "image/gif";
        }
        return "application/octet-stream";
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.trim().isEmpty() ? type : type + "：" + message;
    }

    /** INFO 上次到达的时间（调试用）。 */
    static long infoAgeMillis() {
        return infoAtMillis == 0 ? Long.MAX_VALUE : System.currentTimeMillis() - infoAtMillis;
    }

    /** 2 秒内没 INFO 视为无桥接；仅日志用。 */
    static boolean infoWithinWait() {
        return infoAgeMillis() <= INFO_WAIT_MS;
    }
}
