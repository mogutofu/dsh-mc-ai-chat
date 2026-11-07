package com.mogutofu.mcai;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * 跟 DSH 侧 {@code /mc-ai} 说话的那部分。
 *
 * <p>线程模型：指令线程（服务端主线程）只负责把玩家信息抄一份、丢进
 * {@link CompletableFuture#supplyAsync}；HTTP 阻塞在 {@code mcai-http} 线程池里等；
 * 拿到结果再用 {@link MinecraftServer#execute(Runnable)} 回到服务端线程发消息。
 * 全程主线程不阻塞，网络再慢也只会让玩家多等一会儿聊天栏。</p>
 */
public final class AiHttpClient {

    /** Minecraft 自带 Gson，不需要额外依赖。 */
    private static final Gson GSON = new Gson();

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "mcai-http");
        thread.setDaemon(true);
        return thread;
    });

    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .version(HttpClient.Version.HTTP_1_1)
            .build();

    private static final int MAX_CHAT_CHARS = 800;

    private AiHttpClient() {
    }

    /**
     * 异步问一次 AI，结果直接发到玩家聊天栏。
     *
     * <p>这里只在调用线程（服务端主线程）上读取玩家信息，之后的闭包里只用
     * {@link UUID} 和名字，不跨线程碰 {@link ServerPlayer} 对象。</p>
     */
    public static void ask(MinecraftServer server, ServerPlayer player, String text) {
        UUID playerId = player.getUUID();
        String playerName = player.getGameProfile().getName();
        String endpoint = McaiConfig.endpoint();
        int timeoutSeconds = McaiConfig.timeoutSeconds();

        CompletableFuture
                .supplyAsync(() -> post(endpoint, playerName, playerId, text, timeoutSeconds), EXECUTOR)
                .thenAccept(outcome -> deliver(server, playerId, outcome))
                .exceptionally(throwable -> {
                    deliver(server, playerId, Outcome.failure(reasonOf(throwable)));
                    return null;
                });
    }

    // ── 网络 ──────────────────────────────────────────────────────────

    private static Outcome post(String endpoint, String playerName, UUID playerId, String text, int timeoutSeconds) {
        JsonObject payload = new JsonObject();
        payload.addProperty("player", playerName);
        payload.addProperty("uuid", playerId.toString());
        payload.addProperty("text", text);

        HttpRequest request;
        try {
            request = HttpRequest.newBuilder(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload), StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException badEndpoint) {
            return Outcome.failure("配置里的 endpoint 不是合法地址：" + endpoint);
        }

        long startedAt = System.currentTimeMillis();
        try {
            HttpResponse<String> response = CLIENT.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            McaiMod.LOGGER.debug("[mcai] {} 的请求返回 HTTP {}，耗时 {} ms",
                    playerName, response.statusCode(), System.currentTimeMillis() - startedAt);
            return parse(response.statusCode(), response.body());
        } catch (HttpTimeoutException timeout) {
            return Outcome.failure("等 AI 回复超时了（" + timeoutSeconds + " 秒）");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return Outcome.failure("请求被中断了");
        } catch (ConnectException refused) {
            return Outcome.failure("连不上 AI 服务（" + endpoint + "），dsh-mc-server-ops 开了吗？");
        } catch (Exception error) {
            return Outcome.failure(describe(error));
        }
    }

    // ── 协议解析（ok 优先，HTTP 状态码只用于报错信息） ────────────────

    private static Outcome parse(int status, String body) {
        if (body == null || body.trim().isEmpty()) {
            return Outcome.failure("AI 服务返回了空内容（HTTP " + status + "）");
        }

        JsonObject json;
        try {
            JsonElement element = JsonParser.parseString(body);
            if (!element.isJsonObject()) {
                return Outcome.failure("AI 服务返回的不是 JSON 对象（HTTP " + status + "）");
            }
            json = element.getAsJsonObject();
        } catch (RuntimeException brokenJson) {
            return Outcome.failure("AI 服务返回的 JSON 解析不了（HTTP " + status + "）");
        }

        if (isOk(json)) {
            String reply = string(json, "reply");
            if (reply == null || reply.trim().isEmpty()) {
                return Outcome.failure("AI 说成功了，但回复是空的");
            }
            return Outcome.reply(reply);
        }

        String error = string(json, "error");
        if (error == null || error.trim().isEmpty()) {
            error = "AI 服务返回失败（HTTP " + status + "）";
        }
        return Outcome.failure(error);
    }

    /** 契约：HTTP 200 也可能 ok=false，所以一律看 ok 字段，不看状态码。 */
    private static boolean isOk(JsonObject json) {
        JsonElement ok = json.get("ok");
        if (ok == null || !ok.isJsonPrimitive() || !ok.getAsJsonPrimitive().isBoolean()) {
            return false;
        }
        return ok.getAsBoolean();
    }

    private static String string(JsonObject json, String key) {
        JsonElement value = json.get(key);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (RuntimeException notAString) {
            return null;
        }
    }

    // ── 回主线程发消息 ────────────────────────────────────────────────

    private static void deliver(MinecraftServer server, UUID playerId, Outcome outcome) {
        try {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return; // 玩家已经下线，这条回复就丢掉
                }
                player.sendSystemMessage(render(outcome));
            });
        } catch (RuntimeException shuttingDown) {
            // 服务端正在关服，任务队列不收活了；这时候丢消息是正确行为
            McaiMod.LOGGER.debug("[mcai] 服务端已停止，丢弃一条 /ai 回复");
        }
    }

    private static Component render(Outcome outcome) {
        if (outcome.ok) {
            return Component.literal("[小铜] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(outcome.text).withStyle(ChatFormatting.AQUA));
        }
        return Component.literal("[小铜] 出错了：").withStyle(ChatFormatting.RED)
                .append(Component.literal(outcome.text).withStyle(ChatFormatting.RED));
    }

    // ── 杂项 ──────────────────────────────────────────────────────────

    private static String reasonOf(Throwable throwable) {
        Throwable cause = throwable;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        return describe(cause);
    }

    private static String describe(Throwable error) {
        String message = error.getMessage();
        String type = error.getClass().getSimpleName();
        if (message == null || message.trim().isEmpty()) {
            return type;
        }
        return type + "：" + message;
    }

    /** 聊天栏是一行，换行和控制字符会把界面搞乱，顺手截一下长度。 */
    private static String sanitize(String raw) {
        String text = raw.replace('\r', ' ').replace('\n', ' ').replace('\t', ' ').trim();
        if (text.length() > MAX_CHAT_CHARS) {
            text = text.substring(0, MAX_CHAT_CHARS) + "…";
        }
        return text;
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
