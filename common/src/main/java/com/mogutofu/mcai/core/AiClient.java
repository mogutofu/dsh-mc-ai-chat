package com.mogutofu.mcai.core;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ConnectException;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 跟 DSH 插件 {@code /mc-ai} 说话的那部分（Java 8，零依赖）。
 *
 * <p>请求在 {@code mcai-http} 守护线程池里跑；回调也在那个线程，平台适配层负责
 * 切回服务器主线程再发聊天栏消息。</p>
 */
public final class AiClient {

    public interface Callback {
        void onResult(boolean ok, String text);
    }

    public interface Logger {
        void log(String message);
    }

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "mcai-http");
        thread.setDaemon(true);
        return thread;
    });

    private static final int MAX_CHAT_CHARS = 800;

    private AiClient() {
    }

    /** 异步问一次 AI；结果通过 callback 返回（可能带附件）。 */
    public static void ask(final AiSettings settings, final UUID playerId, final String playerName,
                           final String text, final Attachment attachment, final Logger logger,
                           final Callback callback) {
        EXECUTOR.execute(() -> {
            Outcome outcome = post(settings, playerId, playerName, text, attachment, logger);
            try {
                callback.onResult(outcome.ok, outcome.text);
            } catch (Throwable broken) {
                if (logger != null) {
                    logger.log("回调失败：" + broken);
                }
            }
        });
    }

    private static Outcome post(AiSettings settings, UUID playerId, String playerName, String text,
                                Attachment attachment, Logger logger) {
        String endpoint = settings.endpoint;
        int timeoutSeconds = settings.timeoutSeconds;
        if (attachment != null) {
            timeoutSeconds = Math.max(timeoutSeconds, 90);
        }
        StringBuilder body = new StringBuilder(256);
        body.append('{');
        body.append("\"player\":").append(Json.quote(playerName == null ? "" : playerName)).append(',');
        body.append("\"uuid\":").append(Json.quote(playerId == null ? "" : playerId.toString())).append(',');
        body.append("\"text\":").append(Json.quote(text == null ? "" : text));
        if (attachment != null) {
            body.append(",\"attachments\":[{");
            body.append("\"kind\":").append(Json.quote(attachment.image ? "image" : "file")).append(',');
            body.append("\"name\":").append(Json.quote(attachment.name)).append(',');
            body.append("\"mediaType\":").append(Json.quote(attachment.mediaType)).append(',');
            body.append("\"data\":").append(Json.quote(Base64.getEncoder().encodeToString(attachment.data)));
            body.append("}]");
            if (logger != null) {
                logger.log("请求带附件 " + attachment.name + "（" + attachment.mediaType + "，"
                        + attachment.data.length + " 字节）");
            }
        }
        body.append('}');

        HttpURLConnection connection = null;
        long startedAt = System.currentTimeMillis();
        try {
            URL url = new URL(endpoint);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("POST");
            connection.setDoOutput(true);
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(timeoutSeconds * 1000);
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            connection.setRequestProperty("Accept", "application/json");
            byte[] payload = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(payload.length);
            try (OutputStream out = connection.getOutputStream()) {
                out.write(payload);
            }
            int status = connection.getResponseCode();
            InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream();
            String response = readAll(stream);
            if (logger != null) {
                logger.log("请求返回 HTTP " + status + "，耗时 " + (System.currentTimeMillis() - startedAt) + " ms");
            }
            return parse(status, response);
        } catch (SocketTimeoutException timeout) {
            return Outcome.failure("等 AI 回复超时了（" + timeoutSeconds + " 秒）");
        } catch (ConnectException refused) {
            return Outcome.failure("连不上 AI 服务（" + endpoint + "），dsh-mc-server-ops 开了吗？");
        } catch (IOException | RuntimeException error) {
            return Outcome.failure(describe(error));
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Outcome parse(int status, String body) {
        if (body == null || body.trim().isEmpty()) {
            return Outcome.failure("AI 服务返回了空内容（HTTP " + status + "）");
        }
        Object root;
        try {
            root = Json.parse(body);
        } catch (RuntimeException brokenJson) {
            return Outcome.failure("AI 服务返回的 JSON 解析不了（HTTP " + status + "）");
        }
        if (!(root instanceof Map)) {
            return Outcome.failure("AI 服务返回的不是 JSON 对象（HTTP " + status + "）");
        }
        Map<String, Object> json = Json.object(root);
        Object okValue = json.get("ok");
        boolean ok = okValue instanceof Boolean && (Boolean) okValue;
        if (ok) {
            String reply = Json.stringValue(json, "reply", "");
            if (reply.trim().isEmpty()) {
                return Outcome.failure("AI 说成功了，但回复是空的");
            }
            return Outcome.reply(reply);
        }
        String error = Json.stringValue(json, "error", "");
        if (error.trim().isEmpty()) {
            error = "AI 服务返回失败（HTTP " + status + "）";
        }
        return Outcome.failure(error);
    }

    /** 聊天栏是一行，换行/控制字符会弄乱界面，顺手截断。 */
    public static String sanitize(String raw) {
        String text = raw == null ? "" : raw.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (text.length() > MAX_CHAT_CHARS) {
            text = text.substring(0, MAX_CHAT_CHARS) + "…";
        }
        return text;
    }

    private static String readAll(InputStream stream) throws IOException {
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream(1024);
        byte[] buffer = new byte[4096];
        int read;
        while ((read = stream.read(buffer)) >= 0) {
            out.write(buffer, 0, read);
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        return message == null || message.trim().isEmpty() ? type : type + "：" + message;
    }

    private static final class Outcome {
        final boolean ok;
        final String text;

        private Outcome(boolean ok, String text) {
            this.ok = ok;
            this.text = text;
        }

        static Outcome reply(String text) {
            return new Outcome(true, sanitize(text));
        }

        static Outcome failure(String text) {
            return new Outcome(false, sanitize(text));
        }
    }
}
