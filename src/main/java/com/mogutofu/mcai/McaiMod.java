package com.mogutofu.mcai;

import com.mogutofu.mcai.net.McaiNetwork;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Minecraft AI Chat 服务端模组。
 *
 * <p>这个 mod 本身不含任何 AI 能力，它只做一件事：把游戏里的 {@code /ai <内容>}
 * 打包成 JSON，POST 到本机的 DSH 服务（默认 {@code http://127.0.0.1:8765/mc-ai}），
 * 再把回复贴回玩家聊天栏。服务端那半由 {@code dsh-mc-server-ops} 插件提供。</p>
 *
 * <p>1.1.0 起：装了 {@code mcai-client} 的玩家可以用 {@code /ai screenshot}、
 * {@code /ai file} 把截图/本地文件一起发给 AI；附件走 {@code mcai:net} 通道，
 * 只在专用服务器注册（单机 integrated server 不支持，按无客户端处理并降级）。</p>
 */
@Mod(McaiMod.MODID)
public final class McaiMod {

    public static final String MODID = "mcai";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public McaiMod() {
        // 配置写到 config/mcai-common.toml
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, McaiConfig.SPEC, "mcai-common.toml");

        // 附件网络只在专用服务器注册；客户端 JVM（含单机 integrated server）由 mcai-client 注册
        if (FMLEnvironment.dist == Dist.DEDICATED_SERVER) {
            McaiNetwork.register();
        } else {
            LOGGER.info("[mcai] 当前不是专用服务器，mcai:net 通道不注册（附件仅支持专用服务器）");
        }

        LOGGER.info("[mcai] Minecraft AI Chat 已加载，/ai 指令准备就绪");
    }
}
