package com.mogutofu.mcai.client;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

/**
 * mcai-client —— Minecraft AI Chat 的可选客户端模组（NeoForge 26.x）。
 *
 * <p>它只做两件事：登录时给服务端发 ClientHello；收到 AttachmentRequest 时
 * 截图/读本地文件，分块发回去。没有它服务端照常工作，只是 /ai screenshot、
 * /ai file 会降级成纯文本。</p>
 *
 * <p>纯客户端：{@code @Mod(dist = Dist.CLIENT)}，专用服务器上这个类根本不会被加载。</p>
 */
@Mod(value = McaiClientMod.MODID, dist = Dist.CLIENT)
public final class McaiClientMod {

    public static final String MODID = "mcai_client";
    public static final Logger LOGGER = LogUtils.getLogger();

    public McaiClientMod(IEventBus modEventBus) {
        modEventBus.addListener(ClientNet::registerPayloads);
        LOGGER.info("[mcai-client] Minecraft AI Chat 客户端已加载（截图/文件附件）");
    }
}
