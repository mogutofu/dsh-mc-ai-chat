package com.mogutofu.mcai.client.neoforge;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/**
 * 只有物理客户端才会加载这个类：注册载荷处理与登录事件。
 *
 * <p>单独拆一个类，专用服务器上永远不会加载到它，也就不会去解析
 * {@code Minecraft}/截图相关的客户端类。</p>
 */
final class ClientBootstrap {

    private ClientBootstrap() {
    }

    static void init(IEventBus modEventBus) {
        modEventBus.addListener(ClientNet::registerPayloads);
        NeoForge.EVENT_BUS.addListener(ClientEvents::onLoggingIn);
    }
}
