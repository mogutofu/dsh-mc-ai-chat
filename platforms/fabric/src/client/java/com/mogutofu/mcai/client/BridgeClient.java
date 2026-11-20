package com.mogutofu.mcai.client;

import com.mogutofu.mcai.bridge.BridgeProtocol;
import com.mogutofu.mcai.bridge.BridgeUpload;
import com.mogutofu.mcai.client.net.BridgePayload;
import com.mogutofu.mcai.client.net.BridgeRegisterPayload;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Bukkit 附件桥接的客户端（Fabric 26.x，三个 MC 版本共用一份）。
 *
 * <p>登录后补发 {@code minecraft:register} + {@code mcai:bridge} HELLO；收到 INFO 记住
 * 上传端口/一次性 token；收到 REQUEST 截图或读本地文件，HTTP POST 到 Bukkit 插件端口，
 * 再回 DONE / ABORT。原有 {@code mcai:hello/chunk/abort/...} 逻辑不动。</p>
 */
public final class BridgeClient {

    public static final String CLIENT_VERSION = "1.4.0";

    private static final ExecutorService UPLOAD_POOL = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "mcai-bridge-upload");
        thread.setDaemon(true);
        return thread;
    });

    private static volatile boolean ready;
    private static volatile int port;
    private static volatile String advertiseHost = "";
    private static volatile String token = "";
    private static volatile int maxImageBytes = 8 * 1024 * 1024;
    private static volatile int maxFileBytes = 16 * 1024 * 1024;
    private static volatile int timeoutSeconds = 30;

    private BridgeClient() {
    }

    /** 由 {@link ClientNet#register()} 在 mcai:bridge 载荷注册成功后调用。 */
    public static void markReady() {
        ready = true;
        McaiClientFabric.LOGGER.info("[mcai-client] 附件桥接已就绪（协议 {}）", BridgeProtocol.VERSION);
    }

    public static boolean isReady() {
        return ready;
    }

    /** 进服务器后调用。 */
    public static void onLogin() {
        if (!ready) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 桥接载荷未注册，跳过 HELLO");
            return;
        }
        try {
            if (Minecraft.getInstance().getConnection() == null) {
                return;
            }
            try {
                ClientNet.sendRaw(BridgeRegisterPayload.ofChannels(BridgeProtocol.CHANNEL));
            } catch (Throwable ignored) {
                McaiClientFabric.LOGGER.info("[mcai-client] 无法补发 minecraft:register（服务端可能不需要）");
            }
            ClientNet.sendRaw(new BridgePayload(BridgeProtocol.hello("fabric", CLIENT_VERSION)));
            McaiClientFabric.LOGGER.info("[mcai-client] 已发送附件桥接 HELLO（fabric {}）", CLIENT_VERSION);
        } catch (Throwable error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 发送附件桥接 HELLO 失败：{}", error.toString());
        }
    }

    // ── 收帧（客户端主线程） ─────────────────────────────────────────

    public static void handleFrame(byte[] frame) {
        try {
            byte type = BridgeProtocol.typeOf(frame);
            if (type == BridgeProtocol.TYPE_INFO) {
                applyInfo(BridgeProtocol.parseInfo(frame));
            } else if (type == BridgeProtocol.TYPE_REQUEST) {
                handleRequest(BridgeProtocol.parseRequest(frame));
            } else {
                McaiClientFabric.LOGGER.warn("[mcai-client] 忽略未知桥接帧类型 {}", type);
            }
        } catch (IllegalArgumentException broken) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 桥接帧不合法：{}", broken.getMessage());
        } catch (RuntimeException error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 处理桥接帧失败", error);
        }
    }

    private static void applyInfo(BridgeProtocol.Info info) {
        port = info.port;
        advertiseHost = info.advertiseHost == null ? "" : info.advertiseHost;
        token = info.token == null ? "" : info.token;
        maxImageBytes = info.maxImageBytes > 0 ? info.maxImageBytes : 8 * 1024 * 1024;
        maxFileBytes = info.maxFileBytes > 0 ? info.maxFileBytes : 16 * 1024 * 1024;
        McaiClientFabric.LOGGER.info("[mcai-client] 附件桥接可用：端口 {}，advertise-host='{}'，上限 {}/{} 字节",
                port, advertiseHost, maxImageBytes, maxFileBytes);
    }

    // ── REQUEST ───────────────────────────────────────────────────────

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
            McaiClientFabric.LOGGER.warn("[mcai-client] 桥接附件处理失败", error);
            abort(request.transferId, describe(error));
        }
    }

    private static void captureScreenshot(BridgeProtocol.Request request) {
        Minecraft minecraft = Minecraft.getInstance();
        try {
            Screenshot.takeScreenshot(mainRenderTarget(minecraft), image -> {
                if (image == null) {
                    abort(request.transferId, "截图失败：没有拿到画面");
                    return;
                }
                try {
                    byte[] data = readPng(image);
                    if (data.length == 0) {
                        abort(request.transferId, "截图失败：PNG 数据为空");
                        return;
                    }
                    if (data.length > request.maxBytes) {
                        abort(request.transferId, "截图超过上限（" + data.length + " > "
                                + request.maxBytes + " 字节）");
                        return;
                    }
                    McaiClientFabric.LOGGER.info("[mcai-client] 桥接截图 {} 字节，准备上传", data.length);
                    uploadAsync(request, true, "screenshot.png", "image/png", data);
                } catch (Throwable error) {
                    McaiClientFabric.LOGGER.warn("[mcai-client] 桥接截图失败", error);
                    abort(request.transferId, "截图失败：" + describe(error));
                } finally {
                    image.close();
                }
            });
        } catch (Throwable error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 桥接截图失败", error);
            abort(request.transferId, "截图失败：" + describe(error));
        }
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
        McaiClientFabric.LOGGER.info("[mcai-client] 桥接读取文件 {}（{} 字节，{}）", request.path, data.length, mediaType);
        uploadAsync(request, mediaType.startsWith("image/"), fileName, mediaType, data);
    }

    // ── 上传 ──────────────────────────────────────────────────────────

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
                    McaiClientFabric.LOGGER.info("[mcai-client] 附件上传成功（{} {} 字节 → {}:{}）",
                            fileName, data.length, host, targetPort);
                    sendFrame(BridgeProtocol.done(request.transferId, true, ""));
                } else {
                    McaiClientFabric.LOGGER.warn("[mcai-client] 附件上传失败（HTTP {}）：{}", result.status, result.error);
                    abort(request.transferId, result.error);
                }
            });
        });
    }

    // ── 发包 ──────────────────────────────────────────────────────────

    private static void sendFrame(byte[] frame) {
        ClientNet.sendRaw(new BridgePayload(frame));
    }

    private static void abort(UUID transferId, String reason) {
        String safe = reason == null || reason.trim().isEmpty() ? "未知原因" : reason.trim();
        McaiClientFabric.LOGGER.info("[mcai-client] 桥接附件取消：{}", safe);
        try {
            sendFrame(BridgeProtocol.abort(transferId, safe));
        } catch (RuntimeException error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 发送 ABORT 失败：{}", error.toString());
        }
    }

    private static String serverHost() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() != null) {
                SocketAddress remote = minecraft.getConnection().getConnection().getRemoteAddress();
                if (remote instanceof InetSocketAddress) {
                    InetSocketAddress inet = (InetSocketAddress) remote;
                    if (inet.getAddress() != null) {
                        return inet.getAddress().getHostAddress();
                    }
                    return inet.getHostString();
                }
            }
        } catch (Throwable ignored) {
            // 单机 / 局域网
        }
        return "127.0.0.1";
    }

    /** 26.1 用 {@code Minecraft#getMainRenderTarget()}，26.2+ 用 {@code GameRenderer#mainRenderTarget()}。 */
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

    private static byte[] readPng(NativeImage image) throws IOException {
        File temp = File.createTempFile("mcai-bridge-shot", ".png");
        try {
            image.writeToFile(temp);
            return Files.readAllBytes(temp.toPath());
        } finally {
            if (!temp.delete()) {
                temp.deleteOnExit();
            }
        }
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
}
