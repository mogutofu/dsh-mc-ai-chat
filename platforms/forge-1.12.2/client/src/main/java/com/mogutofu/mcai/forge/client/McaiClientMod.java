package com.mogutofu.mcai.forge.client;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * mcai-client —— Minecraft AI Chat 的可选客户端模组（Forge 1.12.2）。
 *
 * <p>它只做两件事：登录后给服务端发 ClientHello；收到 AttachmentRequest 时截图/读本地文件，
 * 24KB 分块发回去。没有它服务端照常工作，只是 /ai screenshot、/ai file 会降级成纯文本。</p>
 *
 * <p>纯客户端：{@code clientSideOnly = true}，装在专用服务器的 mods 里 Forge 会直接跳过它。</p>
 */
@Mod(modid = McaiClientMod.MODID, name = "Minecraft AI Chat Client", version = McaiClientMod.VERSION,
        clientSideOnly = true, acceptableRemoteVersions = "*", acceptedMinecraftVersions = "[1.12.2]")
public final class McaiClientMod {

    public static final String MODID = "mcai_client";
    public static final String VERSION = "1.3.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) {
            LOGGER.info("[mcai-client] 当前不是客户端环境，mcai-client 不做任何事");
            return;
        }
        ClientNet.register();
        LOGGER.info("[mcai-client] Minecraft AI Chat 客户端已加载（截图/文件附件）");
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        if (FMLCommonHandler.instance().getSide() != Side.CLIENT) {
            return;
        }
        // 1.12.2 没有 @Mod.EventBusSubscriber，事件处理器要手动挂到事件总线上
        MinecraftForge.EVENT_BUS.register(new ClientEvents());
    }
}
