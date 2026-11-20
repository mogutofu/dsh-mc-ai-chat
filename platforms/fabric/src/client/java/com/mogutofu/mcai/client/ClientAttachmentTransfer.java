package com.mogutofu.mcai.client;

import com.mogutofu.mcai.client.net.AttachmentAbortPayload;
import com.mogutofu.mcai.client.net.AttachmentChunkPayload;
import com.mogutofu.mcai.client.net.AttachmentRequestPayload;
import com.mogutofu.mcai.client.net.AttachmentTooLargePayload;
import com.mogutofu.mcai.client.net.Protocol;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.network.chat.Component;

import java.io.File;
import java.io.IOException;
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
 *   <li>截图：{@code Screenshot.takeScreenshot(GameRenderer#mainRenderTarget(), Consumer&lt;NativeImage&gt;)}
 *       拿 NativeImage，{@code writeToFile} 先落成临时 PNG 再读回字节，然后 close 掉 NativeImage；</li>
 *   <li>文件：先 {@code Files.size} 校验上限再 {@code Files.readAllBytes}；</li>
 *   <li>按 24KB 分块发 AttachmentChunk，失败发 AttachmentAbort。</li>
 * </ol>
 *
 * <p>只读本机文件、只把字节发给服务器，不联网、不碰 DSH。</p>
 */
public final class ClientAttachmentTransfer {

    private ClientAttachmentTransfer() {
    }

    public static void handle(AttachmentRequestPayload request) {
        if (request == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            return; // 已经不在服务器里了，请求作废
        }
        try {
            if (request.kind() == Protocol.KIND_SCREENSHOT) {
                captureScreenshot(minecraft, request);
            } else if (request.kind() == Protocol.KIND_FILE) {
                readFile(request);
            } else {
                abort(request.transferId(), "未知的附件类型：" + request.kind());
            }
        } catch (Throwable error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 处理附件请求失败", error);
            abort(request.transferId(), describe(error));
        }
    }

    /** 服务端拒绝附件时的提示（可空实现）。 */
    public static void onTooLarge(AttachmentTooLargePayload message) {
        if (message == null) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        McaiClientFabric.LOGGER.info("[mcai-client] 服务端拒绝附件：{}", message.reason());
        if (minecraft.player != null) {
            minecraft.player.sendSystemMessage(Component.literal("[mcai-client] 服务端拒绝附件：" + message.reason())
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    // ── 截图 ──────────────────────────────────────────────────────────

    private static void captureScreenshot(Minecraft minecraft, AttachmentRequestPayload request) {
        // 26.x：主渲染目标在 26.1 还是 Minecraft#getMainRenderTarget()，26.2+ 搬到了
        // GameRenderer#mainRenderTarget()；三个版本共用一份源码，用下面的 mainRenderTarget() 探测。
        // 截图改成回调式 Screenshot.takeScreenshot(rt, Consumer<NativeImage>)，NativeImage
        // 也没有 asByteArray() 了，所以先 writeToFile 落成临时 PNG 再读字节（临时文件用完就删）。
        // 这里已经在客户端主线程（payload handler 里 execute 过），回调直接同步执行；
        // 万一它异步回调也没关系，全程不阻塞等待。
        try {
            Screenshot.takeScreenshot(mainRenderTarget(minecraft), image -> {
                if (image == null) {
                    abort(request.transferId(), "截图失败：没有拿到画面");
                    return;
                }
                try {
                    byte[] data = readPng(image);
                    if (data.length <= 0) {
                        abort(request.transferId(), "截图失败：PNG 数据为空");
                        return;
                    }
                    if (data.length > request.maxBytes()) {
                        abort(request.transferId(), "截图超过 " + Protocol.megabytes(request.maxBytes())
                                + " 限制（" + data.length + " 字节）");
                        return;
                    }
                    McaiClientFabric.LOGGER.info("[mcai-client] 已截图 {} 字节", data.length);
                    sendChunks(request.transferId(), data, "screenshot.png", "image/png", true);
                } catch (Throwable error) {
                    McaiClientFabric.LOGGER.warn("[mcai-client] 截图失败", error);
                    abort(request.transferId(), describe(error));
                } finally {
                    image.close();
                }
            });
        } catch (Throwable error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 截图失败", error);
            abort(request.transferId(), describe(error));
        }
    }

    /**
     * 取当前主渲染目标：26.1 用 {@code Minecraft#getMainRenderTarget()}，26.2+ 用
     * {@code GameRenderer#mainRenderTarget()}。反射两种都试，一份源码三版本通用。
     */
    private static RenderTarget mainRenderTarget(Minecraft minecraft) {
        try {
            return (RenderTarget) Minecraft.class.getMethod("getMainRenderTarget").invoke(minecraft);
        } catch (Throwable ignored) {
            // 26.2+ 没有旧访问器，换新的
        }
        try {
            return (RenderTarget) minecraft.gameRenderer.getClass().getMethod("mainRenderTarget")
                    .invoke(minecraft.gameRenderer);
        } catch (Throwable broken) {
            throw new IllegalStateException("找不到主渲染目标（26.x 访问器）", broken);
        }
    }

    /** NativeImage -> PNG 字节：26.x 只剩 writeToFile，先写临时文件再读回来。 */
    private static byte[] readPng(NativeImage image) throws IOException {
        File tmp = File.createTempFile("mcai-screenshot", ".png");
        try {
            image.writeToFile(tmp.toPath());
            return Files.readAllBytes(tmp.toPath());
        } finally {
            try {
                Files.deleteIfExists(tmp.toPath());
            } catch (IOException ignored) {
                // 删不掉无所谓，系统临时目录会清
            }
        }
    }

    // ── 本地文件 ──────────────────────────────────────────────────────

    private static void readFile(AttachmentRequestPayload request) throws Exception {
        Path path;
        try {
            path = Paths.get(request.path());
        } catch (InvalidPathException invalid) {
            abort(request.transferId(), "路径不合法：" + invalid.getMessage());
            return;
        }
        if (!Files.isRegularFile(path)) {
            abort(request.transferId(), "文件不存在或不是普通文件：" + request.path());
            return;
        }
        long size = Files.size(path);
        if (size > request.maxBytes()) {
            abort(request.transferId(), "文件超过 " + Protocol.megabytes(request.maxBytes())
                    + " 限制（" + size + " 字节）");
            return;
        }
        byte[] data = Files.readAllBytes(path);
        if (data.length > request.maxBytes()) {
            abort(request.transferId(), "文件超过 " + Protocol.megabytes(request.maxBytes())
                    + " 限制（" + data.length + " 字节）");
            return;
        }
        String fileName = path.getFileName() == null ? "file.bin" : path.getFileName().toString();
        String mediaType = mediaTypeOf(fileName);
        McaiClientFabric.LOGGER.info("[mcai-client] 已读取文件 {}（{} 字节，{}）",
                request.path(), data.length, mediaType);
        sendChunks(request.transferId(), data, fileName, mediaType, mediaType.startsWith("image/"));
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
        int chunkCount = data.length == 0 ? 1 : (data.length + Protocol.CHUNK_SIZE - 1) / Protocol.CHUNK_SIZE;
        for (int index = 0; index < chunkCount; index++) {
            int from = index * Protocol.CHUNK_SIZE;
            int to = Math.min(data.length, from + Protocol.CHUNK_SIZE);
            byte[] part = Arrays.copyOfRange(data, from, to);
            try {
                ClientNet.sendToServer(new AttachmentChunkPayload(transferId, index, chunkCount, fileName,
                        mediaType, image, data.length, part));
            } catch (RuntimeException error) {
                McaiClientFabric.LOGGER.warn("[mcai-client] 发送第 {} 块失败：{}", index, error.toString());
                abort(transferId, "发送分块失败（第 " + index + " 块）：" + describe(error));
                return;
            }
        }
        McaiClientFabric.LOGGER.info("[mcai-client] 已发送附件 {}：{} 字节 / {} 块", fileName, data.length, chunkCount);
    }

    private static void abort(UUID transferId, String reason) {
        String safeReason = reason == null || reason.isEmpty() ? "未知原因" : reason;
        McaiClientFabric.LOGGER.info("[mcai-client] 附件取消：{}", safeReason);
        try {
            if (ClientPlayNetworking.canSend(AttachmentAbortPayload.TYPE)) {
                ClientNet.sendToServer(new AttachmentAbortPayload(transferId, safeReason));
            }
        } catch (RuntimeException error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 发送 AttachmentAbort 失败：{}", error.toString());
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
