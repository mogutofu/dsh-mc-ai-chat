package com.mogutofu.mcai.client;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * mcai-client —— Minecraft AI Chat 的可选客户端模组（Fabric 26.x）。
 *
 * <p>它只做两件事：登录时给服务端发 ClientHello；收到 AttachmentRequest 时
 * 截图/读本地文件，分块发回去。没有它服务端照常工作，只是 /ai screenshot、
 * /ai file 会降级成纯文本。</p>
 */
public final class McaiClientFabric implements ClientModInitializer {

    public static final String MODID = "mcai_client";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    @Override
    public void onInitializeClient() {
        ClientNet.register();
        LOGGER.info("[mcai-client] Minecraft AI Chat 客户端已加载（截图/文件附件）");
    }
}
