package com.mogutofu.mcai.client;

import com.mogutofu.mcai.client.net.ClientHelloPayload;
import com.mogutofu.mcai.client.net.NetProtocol;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;

/**
 * 客户端事件：进服务器时告诉服务端「我装了 mcai-client」。
 */
@EventBusSubscriber(modid = McaiClientMod.MODID, value = Dist.CLIENT)
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
            ClientNet.sendToServer(new ClientHelloPayload(NetProtocol.PROTOCOL_VERSION));
            McaiClientMod.LOGGER.info("[mcai-client] 已向服务器发送 ClientHello（协议 {}）",
                    NetProtocol.PROTOCOL_VERSION);
        } catch (RuntimeException error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 发送 ClientHello 失败", error);
        }
    }
}
