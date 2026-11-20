package com.mogutofu.mcai.bridge;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

/**
 * 客户端侧的上传：把截图像素 / 本地文件字节 POST 给 Bukkit 插件的 HTTP 端口。
 *
 * <p>只发一个请求、只收一小段 JSON；异常都转成 {@link Result}，不往外抛，保证
 * 客户端不会因为桥接失败崩溃。Java 8 语法，零依赖。</p>
 */
public final class BridgeUpload {

    /** 上传结果。 */
    public static final class Result {
        public final boolean ok;
        public final String error;
        public final int status;

        Result(boolean ok, String error, int status) {
            this.ok = ok;
            this.error = error;
            this.status = status;
        }

        public static Result ok(int status) {
            return new Result(true, "", status);
        }

        public static Result failure(String error, int status) {
            return new Result(false, error == null || error.isEmpty() ? "未知错误" : error, status);
        }
    }

    private static final int MAX_RESPONSE_BYTES = 8192;

    private BridgeUpload() {
    }

    /**
     * POST {@code http://host:port/mcai/upload}。
     *
     * @param advertiseHost INFO 下发的地址；空串表示用客户端自己连接服务器的地址（由调用方解析）
     * @param timeoutSeconds 连接/读取超时；≤0 时按 30 秒
     */
    public static Result post(String advertiseHost, int port, String token, UUID transferId,
                              boolean image, String fileName, String mediaType, byte[] data,
                              int timeoutSeconds) {
        if (data == null) {
            data = new byte[0];
        }
        if (fileName == null || fileName.isEmpty()) {
            fileName = image ? "screenshot.png" : "attachment.bin";
        }
        if (mediaType == null || mediaType.isEmpty()) {
            mediaType = image ? "image/png" : "application/octet-stream";
        }
        int timeout = timeoutSeconds <= 0 ? 30 : Math.min(timeoutSeconds, 300);
        String url = buildUrl(advertiseHost, port);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(timeout * 1000);
            connection.setReadTimeout(timeout * 1000);
            connection.setRequestProperty("Content-Type", "application/octet-stream");
            connection.setRequestProperty("X-Mcai-Token", token == null ? "" : token);
            connection.setRequestProperty("X-Mcai-Transfer", transferId == null ? "" : transferId.toString());
            connection.setRequestProperty("X-Mcai-Kind", image ? "image" : "file");
            connection.setRequestProperty("X-Mcai-Name-B64",
                    Base64.getEncoder().encodeToString(fileName.getBytes(StandardCharsets.UTF_8)));
            connection.setRequestProperty("X-Mcai-Media", mediaType);
            connection.setFixedLengthStreamingMode(data.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(data);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String body = readAll(stream, MAX_RESPONSE_BYTES);
            if (status == 200) {
                return Result.ok(status);
            }
            String error = extractError(body);
            if (error.isEmpty()) {
                error = "上传被服务器拒绝（HTTP " + status + "）";
            }
            return Result.failure(error, status);
        } catch (IOException | RuntimeException error) {
            return Result.failure(describe(error), -1);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /** {@code http://host:port/mcai/upload}；host 为空时调用方应先解析好。 */
    public static String buildUrl(String advertiseHost, int port) {
        String host = advertiseHost == null ? "" : advertiseHost.trim();
        if (host.isEmpty()) {
            host = "127.0.0.1";
        }
        if (host.startsWith("[") && host.endsWith("]")) {
            host = host.substring(1, host.length() - 1);
        }
        boolean ipv6 = host.indexOf(':') >= 0;
        return "http://" + (ipv6 ? "[" + host + "]" : host) + ":" + port + "/mcai/upload";
    }

    /** 文件名只取 basename，绝不把客户端路径带给服务器。 */
    public static String baseName(String path) {
        if (path == null) {
            return "";
        }
        String normalized = path.replace('\\', '/');
        int slash = normalized.lastIndexOf('/');
        String name = slash >= 0 ? normalized.substring(slash + 1) : normalized;
        return name.trim();
    }

    /**
     * 从 INFO 的 advertiseHost 和客户端实际连接的服务器地址里挑一个能用的。
     *
     * <p>插件 {@code advertise-host} 为空时，客户端用自己连接服务器的地址（NAT / 代理时
     * 由服主填公网域名）。</p>
     */
    public static String resolveHost(String advertiseHost, String serverHost) {
        if (advertiseHost != null && !advertiseHost.trim().isEmpty()) {
            return advertiseHost.trim();
        }
        return serverHost == null ? "" : serverHost.trim();
    }

    private static String extractError(String body) {
        if (body == null) {
            return "";
        }
        int key = body.indexOf("\"error\"");
        if (key < 0) {
            return "";
        }
        int colon = body.indexOf(':', key);
        if (colon < 0) {
            return "";
        }
        int start = body.indexOf('"', colon + 1);
        if (start < 0) {
            return "";
        }
        int end = body.indexOf('"', start + 1);
        if (end < 0) {
            return "";
        }
        return body.substring(start + 1, end);
    }

    private static String readAll(InputStream stream, int max) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(256);
        byte[] buffer = new byte[1024];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            if (out.size() + read > max) {
                break;
            }
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.trim().isEmpty() ? type : type + "：" + message;
    }
}
