package com.mogutofu.mcai.client.neoforge;

import com.mogutofu.mcai.client.neoforge.net.ClientHello;
import com.mogutofu.mcai.client.neoforge.net.NetProtocol;
import net.minecraft.network.Connection;
import net.minecraft.network.ConnectionProtocol;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * 客户端事件：进服务器时告诉服务端「我装了 mcai-client」。
 *
 * <p>只在服务端确实注册了同 id 载荷时才发 hello：玩家连到没装 mcai 的
 * 服务器（或单机）时不会白发包。</p>
 */
public final class ClientEvents {

    private ClientEvents() {
    }

    public static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        try {
            if (!ClientNet.isReady()) {
                McaiClientMod.LOGGER.warn("[mcai-client] 网络载荷未注册，跳过 ClientHello");
            } else {
                Connection connection = event.getConnection();
                if (connection != null) {
                    if (!NetworkRegistry.hasChannel(connection, ConnectionProtocol.PLAY, ClientHello.TYPE.id())) {
                        McaiClientMod.LOGGER.info("[mcai-client] 服务端未安装 mcai，跳过 ClientHello");
                    } else {
                        ClientNet.sendToServer(new ClientHello(NetProtocol.PROTOCOL_VERSION));
                        McaiClientMod.LOGGER.info("[mcai-client] 已向服务器发送 ClientHello（协议 {}）",
                                NetProtocol.PROTOCOL_VERSION);
                    }
                }
            }
        } catch (RuntimeException error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 发送 ClientHello 失败：{}", error.toString());
        }
        // 1.4.0：不论服务端是不是 mcai 模组服，都发一次 Bukkit 桥接 HELLO；
        // 没有插件时不会回 INFO，客户端保持「无桥接」，原有功能不受影响。
        BridgeClient.onLogin(event.getConnection());
    }
}
