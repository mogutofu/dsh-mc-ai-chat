package com.mogutofu.mcai.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * mcai-client —— Minecraft AI Chat 的可选客户端模组。
 *
 * <p>它只做两件事：登录时给服务端发 ClientHello；收到 AttachmentRequest 时
 * 截图/读本地文件，分块发回去。没有它服务端照常工作，只是 /ai screenshot、
 * /ai file 会降级成纯文本。</p>
 *
 * <p>纯客户端：在专用服务器上构造函数直接 return，一个客户端类都不碰。</p>
 */
@Mod(McaiClientMod.MODID)
public final class McaiClientMod {

    public static final String MODID = "mcai_client";
    public static final Logger LOGGER = LogManager.getLogger(MODID);

    public McaiClientMod() {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            // 专用服务器：只打一行日志，不做任何事，更不能碰客户端类
            LOGGER.info("[mcai-client] 当前不是客户端环境，mcai-client 不做任何事");
            return;
        }
        // 延迟到确认是客户端后再加载客户端类（避免专用服务器加载 Minecraft/Screenshot 等）
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientNet::register);
        LOGGER.info("[mcai-client] Minecraft AI Chat 客户端已加载（截图/文件附件）");
    }
}
