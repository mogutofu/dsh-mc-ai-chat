package com.mogutofu.mcai.neoforge;

import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.neoforge.net.McaiNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Minecraft AI Chat 服务端模组（NeoForge 1.21.1）。
 *
 * <p>这个 mod 本身不含任何 AI 能力，它只做一件事：把游戏里的 {@code /ai <内容>}
 * 打包成 JSON，POST 到本机的 DSH 服务（默认 {@code http://127.0.0.1:8765/mc-ai}），
 * 再把回复贴回玩家聊天栏。服务端那半由 {@code dsh-mc-server-ops} 插件提供。</p>
 *
 * <p>装了 {@code mcai_client} 的玩家可以用 {@code /ai screenshot}、{@code /ai file}
 * 把截图/本地文件一起发给 AI；附件载荷只在专用服务器注册（单机 integrated server
 * 不支持，按无客户端处理并降级）。</p>
 */
@Mod(McaiMod.MODID)
public final class McaiMod {

    public static final String MODID = "mcai";
    public static final Logger LOGGER = LoggerFactory.getLogger("mcai");

    public McaiMod(IEventBus modEventBus) {
        // 首次启动写一份默认配置，方便服主知道能改什么
        AiSettings.writeDefaultIfMissing(commonConfigFile());

        // mod 事件总线：网络载荷注册
        modEventBus.addListener(McaiNetwork::registerPayloads);

        // 游戏事件总线：指令、tick、登出、关服
        NeoForge.EVENT_BUS.addListener(McaiCommands::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(McaiServerEvents::onPlayerLoggedOut);
        NeoForge.EVENT_BUS.addListener(McaiServerEvents::onServerTick);
        NeoForge.EVENT_BUS.addListener(McaiServerEvents::onServerStopping);

        LOGGER.info("[mcai] Minecraft AI Chat 已加载（NeoForge 1.21.1），/ai 与 /weather 准备就绪");
    }

    /** config/mcai-common.json（endpoint/timeoutSeconds/enabled/rateLimitSeconds）。 */
    public static File commonConfigFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mcai-common.json").toFile();
    }

    /** config/mcai-weather.json（由 DSH 插件设置面板写入；每次 /weather 重新读）。 */
    public static File weatherConfigFile() {
        return FMLPaths.CONFIGDIR.get().resolve("mcai-weather.json").toFile();
    }
}
