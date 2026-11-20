package com.mogutofu.mcai.neoforge;

import net.neoforged.fml.loading.FMLPaths;

import java.io.File;

/**
 * 配置文件路径：{@code config/mcai-common.json}、{@code config/mcai-weather.json}。
 *
 * <p>与 Forge 1.20.1 / Bukkit 版字段、默认值一致；天气配置每次 {@code /weather}
 * 都重新读一次，因为它是 DSH 插件设置面板单向写过来的。</p>
 */
public final class McaiPaths {

    private McaiPaths() {
    }

    public static File configDir() {
        try {
            return FMLPaths.CONFIGDIR.get().toFile();
        } catch (Throwable broken) {
            // 极端情况下（例如 FML 路径还没初始化）退回相对目录，不让指令直接崩
            return new File("config");
        }
    }

    public static File aiConfigFile() {
        return new File(configDir(), "mcai-common.json");
    }

    public static File weatherConfigFile() {
        return new File(configDir(), "mcai-weather.json");
    }
}
