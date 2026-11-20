package com.mogutofu.mcai.client;

import com.mogutofu.mcai.client.net.AttachmentAbortPayload;
import com.mogutofu.mcai.client.net.AttachmentChunkPayload;
import com.mogutofu.mcai.client.net.AttachmentRequestPayload;
import com.mogutofu.mcai.client.net.AttachmentTooLargePayload;
import com.mogutofu.mcai.client.net.NetProtocol;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Locale;
import java.util.UUID;

/**
 * 收到 AttachmentRequest 后真正干活的地方（客户端主线程）。
 *
 * <ol>
 *   <li>截图：{@code Screenshot.takeScreenshot(minecraft.getMainRenderTarget())} 拿 NativeImage，
 *       {@code asByteArray()} 得到 PNG 字节，close 掉 NativeImage；</li>
 *   <li>文件：先 {@code Files.size} 校验上限再 {@code Files.readAllBytes}；</li>
 *   <li>按 24KB 分块发 AttachmentChunk，失败发 AttachmentAbort。</li>
 * </ol>
 *
 * <p>只读本机文件、只把字节发给服务器，不联网、不碰 DSH。</p>
 */
public final class AttachmentTransfer {

    private AttachmentTransfer() {
    }

    public static void handle(AttachmentRequestPayload request) {
        if (request == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null || minecraft.player == null || minecraft.getConnection() == null) {
            return; // 已经不在服务器里了，请求作废
        }
        try {
            if (request.kind == NetProtocol.KIND_SCREENSHOT) {
                captureScreenshot(minecraft, request);
            } else if (request.kind == NetProtocol.KIND_FILE) {
                readFile(request);
            } else {
                abort(request.transferId, "未知的附件类型：" + request.kind);
            }
        } catch (Throwable error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 处理附件请求失败", error);
            abort(request.transferId, describe(error));
        }
    }

    /** 服务端拒绝附件时的提示（index 4，预留消息）。 */
    public static void onTooLarge(AttachmentTooLargePayload message) {
        if (message == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        McaiClientMod.LOGGER.info("[mcai-client] 服务端拒绝附件：{}", message.reason);
        if (minecraft != null && minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("[mcai-client] 服务端拒绝附件：" + message.reason)
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ── 截图 ──────────────────────────────────────────────────────────

    private static void captureScreenshot(Minecraft minecraft, AttachmentRequestPayload request) throws Exception {
        byte[] data = capturePng(minecraft);
        if (data == null) {
            abort(request.transferId, "截图失败：当前客户端版本暂时读不到游戏画面");
            return;
        }
        if (data.length <= 0) {
            abort(request.transferId, "截图失败：PNG 数据为空");
            return;
        }
        if (data.length > request.maxBytes) {
            abort(request.transferId, "截图超过 " + NetProtocol.megabytes(request.maxBytes)
                    + " 限制（" + data.length + " 字节）");
            return;
        }
        McaiClientMod.LOGGER.info("[mcai-client] 已截图 {} 字节", data.length);
        sendChunks(request.transferId, data, "screenshot.png", "image/png", true);
    }

    /**
     * 截当前画面并编码成 PNG 字节；失败返回 null。
     *
     * <p>TODO(26.x)：26.x 把渲染换成了 GPU 抽象层（GpuDevice / CommandEncoder），
     * 旧的 {@code Screenshot.takeScreenshot(RenderTarget)} 与
     * {@code NativeImage.asByteArray()} 都没了；确认新 API 后补实现。</p>
     */
    private static byte[] capturePng(Minecraft minecraft) {
        return null;
    }

    // ── 本地文件 ──────────────────────────────────────────────────────

    private static void readFile(AttachmentRequestPayload request) throws Exception {
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
            abort(request.transferId, "文件超过 " + NetProtocol.megabytes(request.maxBytes)
                    + " 限制（" + size + " 字节）");
            return;
        }
        byte[] data = Files.readAllBytes(path);
        if (data.length > request.maxBytes) {
            abort(request.transferId, "文件超过 " + NetProtocol.megabytes(request.maxBytes)
                    + " 限制（" + data.length + " 字节）");
            return;
        }
        String fileName = path.getFileName() == null ? "file.bin" : path.getFileName().toString();
        String mediaType = mediaTypeOf(fileName);
        McaiClientMod.LOGGER.info("[mcai-client] 已读取文件 {}（{} 字节，{}）", request.path, data.length, mediaType);
        sendChunks(request.transferId, data, fileName, mediaType, mediaType.startsWith("image/"));
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

    // ── 分块发送 ──────────────────────────────────────────────────────

    private static void sendChunks(UUID transferId, byte[] data, String fileName, String mediaType, boolean image) {
        int chunkCount = data.length == 0 ? 1 : (data.length + NetProtocol.CHUNK_SIZE - 1) / NetProtocol.CHUNK_SIZE;
        for (int index = 0; index < chunkCount; index++) {
            int from = index * NetProtocol.CHUNK_SIZE;
            int to = Math.min(data.length, from + NetProtocol.CHUNK_SIZE);
            byte[] part = Arrays.copyOfRange(data, from, to);
            try {
                ClientNet.sendToServer(new AttachmentChunkPayload(transferId, index, chunkCount, fileName, mediaType,
                        image, data.length, part));
            } catch (RuntimeException error) {
                McaiClientMod.LOGGER.warn("[mcai-client] 发送第 {} 块失败", index, error);
                abort(transferId, "发送分块失败（第 " + index + " 块）：" + describe(error));
                return;
            }
        }
        McaiClientMod.LOGGER.info("[mcai-client] 已发送附件 {}：{} 字节 / {} 块", fileName, data.length, chunkCount);
    }

    private static void abort(UUID transferId, String reason) {
        String safeReason = reason == null || reason.isEmpty() ? "未知原因" : reason;
        McaiClientMod.LOGGER.info("[mcai-client] 附件取消：{}", safeReason);
        try {
            if (ClientNet.isReady()) {
                ClientNet.sendToServer(new AttachmentAbortPayload(transferId, safeReason));
            }
        } catch (RuntimeException error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 发送 AttachmentAbort 失败", error);
        }
    }

    private static String describe(Throwable error) {
        if (error == null) {
            return "未知错误";
        }
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        if (message == null || message.trim().isEmpty()) {
            return type;
        }
        return type + "：" + message;
    }
}
