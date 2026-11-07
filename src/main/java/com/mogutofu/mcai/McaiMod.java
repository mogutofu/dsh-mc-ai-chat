package com.mogutofu.mcai;

import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Minecraft AI Chat。
 *
 * <p>这个 mod 本身不含任何 AI 能力，它只做一件事：把游戏里的 {@code /ai <内容>}
 * 打包成 JSON，POST 到本机的 DSH 服务（默认 {@code http://127.0.0.1:8765/mc-ai}），
 * 再把回复贴回玩家聊天栏。服务端那半由 {@code dsh-mc-server-ops} 插件提供。</p>
 */
@Mod(McaiMod.MODID)
public final class McaiMod {

    public static final String MODID = "mcai";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public McaiMod() {
        // 配置写到 config/mcai-common.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, McaiConfig.SPEC, "mcai-common.toml");
        LOGGER.info("[mcai] Minecraft AI Chat 已加载，/ai 指令准备就绪");
    }
}
