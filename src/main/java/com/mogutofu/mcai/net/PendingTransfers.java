package com.mogutofu.mcai.net;

import com.mogutofu.mcai.AiAttachment;
import com.mogutofu.mcai.AiHttpClient;
import com.mogutofu.mcai.ChatFeedback;
import com.mogutofu.mcai.McaiMod;
import net.minecraft.ChatFormatting;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 服务端侧的附件接收状态机。
 *
 * <p>每个玩家同一时间只保留一个待传输请求；新请求覆盖旧请求（旧请求发「已取消」）。
 * 所有方法都在服务端主线程上被调用（消息 handler 走 enqueueWork，tick 事件本来就在主线程）。</p>
 *
 * <p>流程：指令 -> start*() 记录 Pending 并给客户端发 AttachmentRequest ->
 * 客户端分块回 AttachmentChunk -> 收满后拼接、校验 totalBytes -> 交给 {@link AiHttpClient}
 * 带附件请求 DSH。15 秒没动静、客户端 abort、分块校验失败都降级为只发消息。</p>
 */
public final class PendingTransfers {

    private static final Map<UUID, Pending> BY_PLAYER = new ConcurrentHashMap<>();

    private PendingTransfers() {
    }

    public static void startScreenshot(MinecraftServer server, ServerPlayer player, String message) {
        start(server, player, NetProtocol.KIND_SCREENSHOT, message, "", NetProtocol.MAX_SCREENSHOT_BYTES);
    }

    public static void startFile(MinecraftServer server, ServerPlayer player, String message, String path) {
        start(server, player, NetProtocol.KIND_FILE, message, path, NetProtocol.MAX_FILE_BYTES);
    }

    private static void start(MinecraftServer server, ServerPlayer player, byte kind, String message, String path, int maxBytes) {
        if (player == null) {
            return;
        }
        Pending previous = BY_PLAYER.remove(player.getUUID());
        if (previous != null) {
            previous.done = true;
            ChatFeedback.notice(player, "上一个附件请求已取消", ChatFormatting.GRAY);
        }

        UUID transferId = UUID.randomUUID();
        Pending pending = new Pending(player.getUUID(), transferId, kind,
                message == null ? "" : message, path == null ? "" : path, maxBytes, System.currentTimeMillis());
        BY_PLAYER.put(player.getUUID(), pending);

        boolean sent;
        try {
            sent = McaiNetwork.sendAttachmentRequest(player, new AttachmentRequest(transferId, kind, pending.path, maxBytes));
        } catch (RuntimeException error) {
            McaiMod.LOGGER.warn("[mcai] 给 {} 发送附件请求失败", player.getGameProfile().getName(), error);
            sent = false;
        }
        if (!sent) {
            fail(serverOf(player), pending, "无法向客户端发送附件请求");
        }
    }

    /** 收到一个分块（已经切回主线程）。 */
    public static void onChunk(ServerPlayer sender, AttachmentChunk message) {
        if (sender == null || message == null) {
            return;
        }
        Pending pending = BY_PLAYER.get(sender.getUUID());
        if (pending == null || pending.done || !pending.transferId.equals(message.transferId)) {
            return; // 未知/过期 transferId 直接丢弃
        }

        MinecraftServer server = serverOf(sender);

        if (pending.chunkCount < 0) {
            // 第一个分块带全部元数据
            if (message.totalBytes < 0 || message.totalBytes > pending.maxBytes) {
                McaiNetwork.sendTooLarge(sender, new AttachmentTooLarge(pending.transferId,
                        "附件超过 " + NetProtocol.megabytes(pending.maxBytes) + " 限制"));
                fail(server, pending, "附件超过大小限制（" + NetProtocol.megabytes(pending.maxBytes) + "），已取消");
                return;
            }
            int expectedChunks = expectedChunks(message.totalBytes);
            if (message.chunkCount != expectedChunks || message.chunkCount < 1 || message.chunkCount > NetProtocol.MAX_CHUNKS) {
                fail(server, pending, "附件分块数量不合法（" + message.chunkCount + "，应为 " + expectedChunks + "）");
                return;
            }
            pending.chunkCount = message.chunkCount;
            pending.totalBytes = message.totalBytes;
            pending.fileName = message.fileName == null ? "" : message.fileName;
            pending.mediaType = message.mediaType == null ? "" : message.mediaType;
            pending.image = message.image;
        } else {
            if (message.chunkCount != pending.chunkCount
                    || message.totalBytes != pending.totalBytes
                    || message.image != pending.image
                    || !Objects.equals(message.fileName, pending.fileName)
                    || !Objects.equals(message.mediaType, pending.mediaType)) {
                fail(server, pending, "附件分块信息前后不一致，已取消");
                return;
            }
        }

        if (message.chunkIndex < 0 || message.chunkIndex >= pending.chunkCount) {
            fail(server, pending, "附件分块序号不合法（" + message.chunkIndex + "）");
            return;
        }
        if (message.data.length > NetProtocol.CHUNK_SIZE) {
            fail(server, pending, "附件分块过大（" + message.data.length + " 字节）");
            return;
        }

        pending.chunks.put(message.chunkIndex, message.data);
        pending.receivedBytes += message.data.length;
        if (pending.receivedBytes > pending.totalBytes) {
            fail(server, pending, "附件大小校验失败");
            return;
        }
        if (pending.chunks.size() < pending.chunkCount) {
            return;
        }

        // 收齐了：按 index 拼接
        byte[] data = new byte[pending.totalBytes];
        int offset = 0;
        for (int index = 0; index < pending.chunkCount; index++) {
            byte[] part = pending.chunks.get(index);
            if (part == null) {
                fail(server, pending, "附件分块缺失（index " + index + "）");
                return;
            }
            System.arraycopy(part, 0, data, offset, part.length);
            offset += part.length;
        }
        if (offset != pending.totalBytes) {
            fail(server, pending, "附件大小校验失败（" + offset + " / " + pending.totalBytes + " 字节）");
            return;
        }

        BY_PLAYER.remove(pending.playerId, pending);
        pending.done = true;

        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(pending.playerId);
        if (player == null) {
            return;
        }

        String fileName = sanitizeFileName(pending.fileName, pending.kind);
        String mediaType = normalizeMediaType(pending.mediaType, pending.kind);
        boolean image = pending.kind == NetProtocol.KIND_SCREENSHOT
                || (pending.image && mediaType.startsWith("image/"));
        McaiMod.LOGGER.info("[mcai] 已收齐 {} 的{}附件：{}（{}，{} 字节，{} 块）",
                player.getGameProfile().getName(),
                pending.kind == NetProtocol.KIND_SCREENSHOT ? "截图" : "文件",
                fileName, mediaType, data.length, pending.chunkCount);
        ChatFeedback.thinking(player);
        AiHttpClient.ask(server, player, pending.message, new AiAttachment(image, fileName, mediaType, data));
    }

    /** 客户端读取失败/放弃。 */
    public static void onAbort(ServerPlayer sender, AttachmentAbort message) {
        if (sender == null || message == null) {
            return;
        }
        Pending pending = BY_PLAYER.get(sender.getUUID());
        if (pending == null || pending.done || !pending.transferId.equals(message.transferId)) {
            return;
        }
        String reason = message.reason == null || message.reason.trim().isEmpty()
                ? "未知原因" : message.reason.trim();
        fail(serverOf(sender), pending, "客户端读取附件失败：" + reason);
    }

    /** 每个服务端 tick 调一次，检查 15 秒超时。 */
    public static void onServerTick() {
        if (BY_PLAYER.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Pending pending : BY_PLAYER.values()) {
            if (pending.done) {
                continue;
            }
            if (now - pending.startedAt >= NetProtocol.ATTACHMENT_TIMEOUT_MS) {
                fail(ServerLifecycleHooks.getCurrentServer(), pending,
                        "等待客户端附件超时（" + (NetProtocol.ATTACHMENT_TIMEOUT_MS / 1000L) + " 秒）");
            }
        }
    }

    public static void onPlayerLogout(UUID playerId) {
        if (playerId == null) {
            return;
        }
        Pending pending = BY_PLAYER.remove(playerId);
        if (pending != null) {
            pending.done = true;
        }
    }

    public static void clear() {
        for (Pending pending : BY_PLAYER.values()) {
            pending.done = true;
        }
        BY_PLAYER.clear();
    }

    // ── 内部 ──────────────────────────────────────────────────────────

    private static void fail(MinecraftServer server, Pending pending, String reason) {
        if (pending == null || pending.done) {
            return;
        }
        pending.done = true;
        BY_PLAYER.remove(pending.playerId, pending);

        ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(pending.playerId);
        if (player == null) {
            return;
        }
        McaiMod.LOGGER.info("[mcai] 附件传输取消：{}（{}）", reason, player.getGameProfile().getName());
        ChatFeedback.degradeToText(server, player, pending.message, reason);
    }

    private static MinecraftServer serverOf(ServerPlayer player) {
        if (player == null) {
            return null;
        }
        MinecraftServer server = player.getServer();
        return server != null ? server : ServerLifecycleHooks.getCurrentServer();
    }

    private static int expectedChunks(int totalBytes) {
        if (totalBytes <= 0) {
            return 1;
        }
        return (totalBytes + NetProtocol.CHUNK_SIZE - 1) / NetProtocol.CHUNK_SIZE;
    }

    private static String sanitizeFileName(String raw, byte kind) {
        if (kind == NetProtocol.KIND_SCREENSHOT) {
            return "screenshot.png";
        }
        String name = raw == null ? "" : raw;
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder builder = new StringBuilder(name.length());
        for (int index = 0; index < name.length() && builder.length() < 160; index++) {
            char character = name.charAt(index);
            if (character >= 0x20 && character != 0x7f && character != '"' && character != '\\' && character != '/') {
                builder.append(character);
            }
        }
        String cleaned = builder.toString().trim();
        return cleaned.isEmpty() ? "attachment.bin" : cleaned;
    }

    private static String normalizeMediaType(String raw, byte kind) {
        if (kind == NetProtocol.KIND_SCREENSHOT) {
            return "image/png";
        }
        String type = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        if (type.matches("[a-z0-9][a-z0-9.+\\-]*/[a-z0-9][a-z0-9.+\\-]{0,63}")) {
            return type;
        }
        return "application/octet-stream";
    }

    private static final class Pending {
        final UUID playerId;
        final UUID transferId;
        final byte kind;
        final String message;
        final String path;
        final int maxBytes;
        final long startedAt;
        final Map<Integer, byte[]> chunks = new HashMap<>();
        int chunkCount = -1;
        int totalBytes = -1;
        int receivedBytes;
        boolean image;
        String fileName = "";
        String mediaType = "";
        volatile boolean done;

        Pending(UUID playerId, UUID transferId, byte kind, String message, String path, int maxBytes, long startedAt) {
            this.playerId = playerId;
            this.transferId = transferId;
            this.kind = kind;
            this.message = message;
            this.path = path;
            this.maxBytes = maxBytes;
            this.startedAt = startedAt;
        }
    }
}
