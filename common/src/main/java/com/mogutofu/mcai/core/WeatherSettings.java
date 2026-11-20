package com.mogutofu.mcai.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Map;

/**
 * {@code config/mcai-weather.json} 的读取与字段定义（所有平台共用）。
 *
 * <p>该文件由 DSH 插件 {@code dsh-mc-server-ops} 的设置面板写入，所以每次
 * {@code /weather} 都重新读一次；文件缺失/损坏时回退默认值。</p>
 */
public final class WeatherSettings {

    public final boolean enabled;
    public final int directThreshold;
    public final int approveNeed;
    public final int rejectNeed;
    public final int timeoutSeconds;
    public final int cooldownSeconds;

    public static final WeatherSettings DEFAULTS = new WeatherSettings(true, 2, 2, 1, 60, 60);

    public WeatherSettings(boolean enabled, int directThreshold, int approveNeed, int rejectNeed,
                           int timeoutSeconds, int cooldownSeconds) {
        this.enabled = enabled;
        this.directThreshold = clamp(directThreshold, 0, 20);
        this.approveNeed = clamp(approveNeed, 1, 20);
        this.rejectNeed = clamp(rejectNeed, 1, 20);
        this.timeoutSeconds = clamp(timeoutSeconds, 5, 3600);
        this.cooldownSeconds = clamp(cooldownSeconds, 0, 86400);
    }

    /** 读一份当前配置；任何异常都回落到默认值，不让一次坏文件把指令弄崩。 */
    public static WeatherSettings load(File file) {
        try {
            if (file == null || !file.isFile()) {
                return DEFAULTS;
            }
            String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            Map<String, Object> json = Json.object(Json.parse(text));
            return new WeatherSettings(
                    Json.boolValue(json, "enabled", true),
                    Json.intValue(json, "directThreshold", 2),
                    Json.intValue(json, "approveNeed", 2),
                    Json.intValue(json, "rejectNeed", 1),
                    Json.intValue(json, "timeoutSeconds", 60),
                    Json.intValue(json, "cooldownSeconds", 60));
        } catch (Throwable broken) {
            return DEFAULTS;
        }
    }

    public String defaultJson() {
        return "{\n"
                + "  \"enabled\": true,\n"
                + "  \"directThreshold\": 2,\n"
                + "  \"approveNeed\": 2,\n"
                + "  \"rejectNeed\": 1,\n"
                + "  \"timeoutSeconds\": 60,\n"
                + "  \"cooldownSeconds\": 60\n"
                + "}\n";
    }

    public String describe() {
        return "在线 ≤ " + directThreshold + " 人：直接生效；更多人在线：需要 "
                + approveNeed + " 人用 /vote yes 同意（/vote no 反对，/vote status 查看）";
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
