package com.mogutofu.mcai;

import net.minecraftforge.common.ForgeConfigSpec;

/**
 * {@code config/mcai-common.toml} 里的四个配置项。
 *
 * <p>取值统一走下面的静态方法：配置还没加载完（或者文件写坏了）时直接回落到默认值，
 * 不让指令因为一次 {@link IllegalStateException} 把服务端带崩。</p>
 */
public final class McaiConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    public static final ForgeConfigSpec.ConfigValue<String> ENDPOINT = BUILDER
            .comment("AI 服务地址，POST JSON 过去；协议见 mcai-contract.md",
                    "默认 http://127.0.0.1:8765/mc-ai")
            .define("endpoint", "http://127.0.0.1:8765/mc-ai");

    public static final ForgeConfigSpec.IntValue TIMEOUT_SECONDS = BUILDER
            .comment("单次请求最多等多少秒")
            .defineInRange("timeoutSeconds", 60, 1, 600);

    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("关掉之后 /ai 只会提示「AI 未开启」")
            .define("enabled", true);

    public static final ForgeConfigSpec.IntValue RATE_LIMIT_SECONDS = BUILDER
            .comment("同一玩家两次 /ai 的最小间隔（秒），填 0 表示不限")
            .defineInRange("rateLimitSeconds", 5, 0, 3600);

    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private McaiConfig() {
    }

    public static String endpoint() {
        return value(ENDPOINT, "http://127.0.0.1:8765/mc-ai");
    }

    public static int timeoutSeconds() {
        return value(TIMEOUT_SECONDS, 60);
    }

    public static boolean enabled() {
        return value(ENABLED, Boolean.TRUE);
    }

    public static int rateLimitSeconds() {
        return value(RATE_LIMIT_SECONDS, 5);
    }

    private static <T> T value(ForgeConfigSpec.ConfigValue<T> entry, T fallback) {
        try {
            T value = entry.get();
            return value == null ? fallback : value;
        } catch (RuntimeException notLoadedYet) {
            return fallback;
        }
    }
}
