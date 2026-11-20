package com.mogutofu.mcai.bukkit;

import com.mogutofu.mcai.bridge.BridgeTokens;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 插件里的极简 HTTP 上传端口（默认 25566）。
 *
 * <p>客户端把截图/文件字节 POST 到 {@code /mcai/upload}，带一次性 token 和 transferId；
 * 这里只做「校验 → 受限读取 → 交给主线程」，不碰游戏状态、不碰 DSH。请求线程用
 * {@link HttpServer} 自己的线程池，读取有超时看门狗；{@link #listener} 的实现负责
 * 切回主线程并调用 {@code AiClient}。</p>
 *
 * <p>只支持 {@code POST /mcai/upload} + {@code Content-Length}，不支持 chunked / multipart，
 * 这就是 mcai-client 1.4.0 的唯一用法。</p>
 */
public final class BridgeUploadServer {

    /** 收到一条合法上传后转到主线程的业务回调。 */
    public interface Listener {
        /** 按 transferId 查 pending（HTTP 线程调用，实现需线程安全）。 */
        PendingUploads.Pending pendingFor(UUID transferId);

        /** 字节读完了（HTTP 线程调用；实现必须切回主线程再动 Bukkit API）。 */
        void onUpload(UUID playerId, UUID transferId, boolean image, String fileName,
                      String mediaType, byte[] data);

        void log(String message);
    }

    private static final String PATH = "/mcai/upload";

    private final BridgeConfig config;
    private final BridgeTokens tokens;
    private final Listener listener;

    private final Semaphore slots;
    private final AtomicBoolean running = new AtomicBoolean(false);

    private HttpServer server;
    private ExecutorService pool;
    private ScheduledExecutorService watchdog;

    public BridgeUploadServer(BridgeConfig config, BridgeTokens tokens, Listener listener) {
        this.config = config;
        this.tokens = tokens;
        this.listener = listener;
        this.slots = new Semaphore(config.maxConcurrent, true);
    }

    /** 绑定端口；失败抛异常（调用方负责只禁用桥接、不影响文字 /ai）。 */
    public void start() throws IOException {
        InetSocketAddress address = new InetSocketAddress(config.bind, config.port);
        server = HttpServer.create(address, 0);
        pool = Executors.newFixedThreadPool(config.maxConcurrent + 2, runnable -> {
            Thread thread = new Thread(runnable, "mcai-bridge-http");
            thread.setDaemon(true);
            return thread;
        });
        watchdog = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mcai-bridge-http-watchdog");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(pool);
        server.createContext(PATH, this::handle);
        server.start();
        running.set(true);
        listener.log("附件上传端口已监听 " + config.bind + ":" + config.port
                + "（transport=" + config.transport + "，截图 ≤ " + mb(config.maxImageBytes)
                + "，文件 ≤ " + mb(config.maxFileBytes) + "，并发 " + config.maxConcurrent + "）");
    }

    public void stop() {
        running.set(false);
        if (server != null) {
            try {
                server.stop(1);
            } catch (Throwable ignored) {
                // 已经是停止状态
            }
            server = null;
        }
        if (pool != null) {
            pool.shutdownNow();
            pool = null;
        }
        if (watchdog != null) {
            watchdog.shutdownNow();
            watchdog = null;
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    // ── HTTP ─────────────────────────────────────────────────────────

    private void handle(HttpExchange exchange) {
        if (!running.get()) {
            respond(exchange, 503, "{\"ok\":false,\"error\":\"bridge disabled\"}");
            return;
        }
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())
                || !PATH.equals(exchange.getRequestURI().getPath())) {
            respond(exchange, 404, "{\"ok\":false,\"error\":\"not found\"}");
            return;
        }
        if (!slots.tryAcquire()) {
            respond(exchange, 429, "{\"ok\":false,\"error\":\"too many concurrent uploads\"}");
            return;
        }
        ScheduledFuture<?> deadline = null;
        try {
            deadline = watchdog.schedule(exchange::close,
                    Math.max(1, config.uploadTimeoutSeconds), TimeUnit.SECONDS);
            process(exchange);
        } catch (Throwable error) {
            listener.log("附件上传处理异常：" + describe(error));
            respondQuietly(exchange, 500, "{\"ok\":false,\"error\":\"" + escape(describe(error)) + "\"}");
        } finally {
            if (deadline != null) {
                deadline.cancel(false);
            }
            slots.release();
            exchange.close();
        }
    }

    private void process(HttpExchange exchange) throws IOException {
        String token = header(exchange, "X-Mcai-Token");
        String transferHeader = header(exchange, "X-Mcai-Transfer");
        String kindHeader = header(exchange, "X-Mcai-Kind");
        String nameHeader = header(exchange, "X-Mcai-Name-B64");
        String media = header(exchange, "X-Mcai-Media");

        UUID transferId = parseUuid(transferHeader);
        if (token == null || token.isEmpty() || transferId == null) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"缺少 token 或 transfer\"}");
            return;
        }
        if (!"image".equalsIgnoreCase(kindHeader) && !"file".equalsIgnoreCase(kindHeader)) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"kind 必须是 image 或 file\"}");
            return;
        }
        boolean image = "image".equalsIgnoreCase(kindHeader);

        BridgeTokens.Grant grant = tokens.consume(token, null, transferId, System.currentTimeMillis());
        if (grant == null) {
            listener.log("附件上传被拒绝：token 不存在/过期/已用过（transfer=" + transferId + "）");
            respond(exchange, 403, "{\"ok\":false,\"error\":\"token 无效或已过期\"}");
            return;
        }
        PendingUploads.Pending pending = listener.pendingFor(transferId);
        if (pending == null || !pending.playerId.equals(grant.playerId)) {
            listener.log("附件上传被拒绝：没有匹配的 pending（transfer=" + transferId + "）");
            respond(exchange, 409, "{\"ok\":false,\"error\":\"没有等待中的附件请求\"}");
            return;
        }
        if (pending.image != image) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"附件类型和请求不一致\"}");
            return;
        }

        int maxBytes = image ? config.maxImageBytes : config.maxFileBytes;
        String contentLength = header(exchange, "Content-Length");
        long declared;
        try {
            declared = contentLength == null ? -1 : Long.parseLong(contentLength.trim());
        } catch (NumberFormatException broken) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"Content-Length 非法\"}");
            return;
        }
        if (declared < 0) {
            respond(exchange, 411, "{\"ok\":false,\"error\":\"缺少 Content-Length\"}");
            return;
        }
        if (declared > maxBytes) {
            listener.log("附件上传被拒绝：声明大小 " + declared + " 超过上限 " + maxBytes);
            respond(exchange, 413, "{\"ok\":false,\"error\":\"附件超过上限（"
                    + mb(maxBytes) + "）\"}");
            return;
        }

        byte[] data = readBody(exchange.getRequestBody(), (int) declared, maxBytes);
        if (data == null) {
            listener.log("附件上传被拒绝：实际字节数超过上限或读取超时（transfer=" + transferId + "）");
            respond(exchange, 413, "{\"ok\":false,\"error\":\"附件读取超过上限\"}");
            return;
        }
        if (data.length != declared) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"实际字节数和 Content-Length 不一致\"}");
            return;
        }
        if (data.length == 0) {
            respond(exchange, 400, "{\"ok\":false,\"error\":\"附件是空的\"}");
            return;
        }

        String fileName = decodeName(nameHeader);
        if (fileName.isEmpty()) {
            fileName = image ? "screenshot.png" : "attachment.bin";
        }
        String mediaType = media == null || media.trim().isEmpty()
                ? (image ? "image/png" : "application/octet-stream")
                : media.trim();
        listener.log("收到附件 " + fileName + "（" + data.length + " 字节，" + mediaType
                + "，transfer=" + transferId + "）");
        respond(exchange, 200, "{\"ok\":true}");
        try {
            listener.onUpload(grant.playerId, transferId, image, fileName, mediaType, data);
        } catch (Throwable error) {
            listener.log("转交附件失败：" + describe(error));
        }
    }

    /**
     * 严格按 Content-Length 读；多读一个字节就判超限。
     *
     * @return 读完的字节；超限/超时返回 {@code null}
     */
    private byte[] readBody(InputStream in, int declared, int maxBytes) {
        if (declared > maxBytes) {
            return null;
        }
        byte[] buffer = new byte[Math.min(8192, Math.max(declared, 1))];
        ByteArrayOutputStream out = new ByteArrayOutputStream(Math.min(declared, 64 * 1024));
        long deadline = System.currentTimeMillis() + Math.max(1, config.uploadTimeoutSeconds) * 1000L;
        try {
            while (out.size() < declared) {
                if (System.currentTimeMillis() > deadline) {
                    return null;
                }
                int want = Math.min(buffer.length, declared - out.size());
                int read = in.read(buffer, 0, want);
                if (read < 0) {
                    break;
                }
                out.write(buffer, 0, read);
                if (out.size() > maxBytes) {
                    return null;
                }
            }
        } catch (IOException broken) {
            return null;
        }
        return out.toByteArray();
    }

    private static String header(HttpExchange exchange, String name) {
        return exchange.getRequestHeaders().getFirst(name);
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        try {
            return UUID.fromString(value.trim());
        } catch (IllegalArgumentException broken) {
            return null;
        }
    }

    private static String decodeName(String header) {
        if (header == null || header.trim().isEmpty()) {
            return "";
        }
        try {
            String decoded = new String(Base64.getDecoder().decode(header.trim()), StandardCharsets.UTF_8);
            String baseName = decoded.replace('\\', '/');
            int slash = baseName.lastIndexOf('/');
            if (slash >= 0) {
                baseName = baseName.substring(slash + 1);
            }
            baseName = baseName.replace('\r', ' ').replace('\n', ' ').trim();
            if (baseName.length() > 255) {
                baseName = baseName.substring(0, 255);
            }
            return baseName;
        } catch (IllegalArgumentException broken) {
            return "";
        }
    }

    private static void respond(HttpExchange exchange, int status, String json) {
        try {
            byte[] body = json.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(status, body.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(body);
            }
        } catch (IOException ignored) {
            // 客户端可能已经走了
        }
    }

    private static void respondQuietly(HttpExchange exchange, int status, String json) {
        try {
            respond(exchange, status, json);
        } catch (Throwable ignored) {
            // 双保险
        }
    }

    private static String escape(String text) {
        return text == null ? "" : text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String mb(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
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
