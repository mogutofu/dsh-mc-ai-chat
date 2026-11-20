package com.mogutofu.mcai.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * {@code config/mcai-common.json} —— /ai 的 HTTP 配置（所有非 Forge 1.20.1 平台共用）。
 *
 * <p>Forge 1.20.1 继续用原有的 {@code config/mcai-common.toml}，行为一致。</p>
 */
public final class AiSettings {

    public final String endpoint;
    public final int timeoutSeconds;
    public final boolean enabled;
    public final int rateLimitSeconds;

    public static final AiSettings DEFAULTS =
            new AiSettings("http://127.0.0.1:8765/mc-ai", 60, true, 5);

    public AiSettings(String endpoint, int timeoutSeconds, boolean enabled, int rateLimitSeconds) {
        this.endpoint = endpoint == null || endpoint.trim().isEmpty()
                ? "http://127.0.0.1:8765/mc-ai" : endpoint.trim();
        this.timeoutSeconds = Math.max(5, Math.min(600, timeoutSeconds));
        this.enabled = enabled;
        this.rateLimitSeconds = Math.max(0, Math.min(3600, rateLimitSeconds));
    }

    public static AiSettings load(File file) {
        try {
            if (file == null || !file.isFile()) {
                return DEFAULTS;
            }
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Map<String, Object> json = Json.object(Json.parse(text));
            return new AiSettings(
                    Json.stringValue(json, "endpoint", DEFAULTS.endpoint),
                    Json.intValue(json, "timeoutSeconds", 60),
                    Json.boolValue(json, "enabled", true),
                    Json.intValue(json, "rateLimitSeconds", 5));
        } catch (Throwable broken) {
            return DEFAULTS;
        }
    }

    /** 首次启动写一份默认配置，方便服主知道能改什么。已存在则不覆盖。 */
    public static void writeDefaultIfMissing(File file) {
        try {
            if (file == null || file.exists()) {
                return;
            }
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            String text = "{\n"
                    + "  \"endpoint\": \"http://127.0.0.1:8765/mc-ai\",\n"
                    + "  \"timeoutSeconds\": 60,\n"
                    + "  \"enabled\": true,\n"
                    + "  \"rateLimitSeconds\": 5\n"
                    + "}\n";
            Files.write(file.toPath(), text.getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE_NEW);
        } catch (Throwable ignored) {
            // 配置写不出来不影响主流程
        }
    }
}
