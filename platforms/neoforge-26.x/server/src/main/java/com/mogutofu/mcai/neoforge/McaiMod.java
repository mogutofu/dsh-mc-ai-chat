package com.mogutofu.mcai.neoforge;

import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.neoforge.net.McaiNetwork;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Minecraft AI Chat 服务端模组（NeoForge 26.x）。
 *
 * <p>这个 mod 本身不含任何 AI 能力，它只做一件事：把游戏里的 {@code /ai <内容>}
 * 打包成 JSON，POST 到本机的 DSH 服务（默认 {@code http://127.0.0.1:8765/mc-ai}），
 * 再把回复贴回玩家聊天栏。服务端那半由 {@code dsh-mc-server-ops} 插件提供。</p>
 *
 * <p>装了 {@code mcai-client} 的玩家可以用 {@code /ai screenshot}、{@code /ai file}
 * 把截图/本地文件一起发给 AI；附件走 26.x 的 {@code CustomPacketPayload}，
 * 只在专用服务器注册（单机 integrated server 不支持，按无客户端处理并降级）。</p>
 */
@Mod(McaiMod.MODID)
public final class McaiMod {

    public static final String MODID = "mcai";
    public static final Logger LOGGER = LogUtils.getLogger();

    public McaiMod(IEventBus modEventBus) {
        // config/mcai-common.json（/ai 的 endpoint/超时/开关/限流），已存在不覆盖
        try {
            AiSettings.writeDefaultIfMissing(McaiPaths.aiConfigFile());
        } catch (Throwable broken) {
            LOGGER.warn("[mcai] 写默认 mcai-common.json 失败：{}", broken.toString());
        }
        // config/mcai-weather.json：DSH 插件会覆盖写，这里只在缺文件时补一份默认值
        WeatherFeature.writeDefaultConfigIfMissing();

        // 附件网络只在专用服务器注册；客户端 JVM（含单机 integrated server）由 mcai-client 注册
        modEventBus.addListener(McaiNetwork::registerPayloads);

        LOGGER.info("[mcai] Minecraft AI Chat 已加载，/ai 指令准备就绪");
    }
}
