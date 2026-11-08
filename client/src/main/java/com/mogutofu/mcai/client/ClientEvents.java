package com.mogutofu.mcai.client;

import com.mogutofu.mcai.client.net.ClientHello;
import com.mogutofu.mcai.client.net.NetProtocol;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 客户端事件：进服务器时告诉服务端「我装了 mcai-client」。
 */
@Mod.EventBusSubscriber(modid = McaiClientMod.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEvents {

    private ClientEvents() {
    }

    @SubscribeEvent
    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        if (!ClientNet.isReady()) {
            McaiClientMod.LOGGER.warn("[mcai-client] 网络通道未注册，跳过 ClientHello");
            return;
        }
        try {
            ClientNet.sendToServer(new ClientHello(NetProtocol.PROTOCOL_VERSION));
            McaiClientMod.LOGGER.info("[mcai-client] 已向服务器发送 ClientHello（协议 {}）", NetProtocol.PROTOCOL_VERSION);
        } catch (RuntimeException error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 发送 ClientHello 失败", error);
        }
    }
}
