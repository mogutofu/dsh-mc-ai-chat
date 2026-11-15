package com.mogutofu.mcai;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * {@code config/mcai-weather.json} —— 游戏内天气投票的配置。
 *
 * <p>这个文件由 DSH 插件 {@code dsh-mc-server-ops} 的设置面板写入（服务端目录 config/ 下），
 * 所以玩家改完设置不用重启服务器：{@code /weather} 每次执行时读一次。
 * 文件不存在/写坏了就回落到默认值（在线 ≤2 人直接改、3 人以上要 2 票同意）。</p>
 */
public final class WeatherConfig {

    /** 一份解析好的配置。 */
    public record Settings(boolean enabled, int directThreshold, int approveNeed, int rejectNeed,
                           int timeoutSeconds, int cooldownSeconds) {
    }

    private static final Settings DEFAULTS = new Settings(true, 2, 2, 1, 60, 60);

    private WeatherConfig() {
    }

    /** 配置文件路径（专用服务器上就是 <服务器目录>/config/mcai-weather.json）。 */
    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve("mcai-weather.json");
    }

    /** 读一份当前配置；任何异常都回落到默认值，不让一次坏文件把指令弄崩。 */
    public static Settings load() {
        try {
            Path path = file();
            if (!Files.isRegularFile(path)) {
                return DEFAULTS;
            }
            JsonObject json = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            return new Settings(
                    bool(json, "enabled", true),
                    clamp(intOf(json, "directThreshold", 2), 0, 20),
                    clamp(intOf(json, "approveNeed", 2), 1, 20),
                    clamp(intOf(json, "rejectNeed", 1), 1, 20),
                    clamp(intOf(json, "timeoutSeconds", 60), 5, 3600),
                    clamp(intOf(json, "cooldownSeconds", 60), 0, 86400));
        } catch (Throwable broken) {
            return DEFAULTS;
        }
    }

    private static boolean bool(JsonObject json, String key, boolean fallback) {
        try {
            JsonElement value = json.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsBoolean();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int intOf(JsonObject json, String key, int fallback) {
        try {
            JsonElement value = json.get(key);
            return value == null || value.isJsonNull() ? fallback : value.getAsInt();
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
