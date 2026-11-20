package com.mogutofu.mcai.forge.client;

import com.mogutofu.mcai.forge.client.net.ClientHello;
import com.mogutofu.mcai.forge.client.net.NetProtocol;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * 客户端事件：进入世界后告诉服务端「我装了 mcai-client」。
 *
 * <p>1.12.2 里 {@code FMLNetworkEvent.ClientConnectedToServerEvent} 触发得太早（服务端
 * 还没把玩家加进 PlayerList），所以改成在客户端 tick 里等 {@code Minecraft.player} 出现再发；
 * 每个连接只发一次，掉回标题界面/换服时重置。</p>
 */
public final class ClientEvents {

    private boolean helloPending = true;

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player == null || minecraft.getConnection() == null) {
            // 不在服务器里：下次进去还要再发一次
            helloPending = true;
            return;
        }
        if (!helloPending) {
            return;
        }
        helloPending = false;
        if (!ClientNet.isReady()) {
            McaiClientMod.LOGGER.warn("[mcai-client] 网络通道未注册，跳过 ClientHello");
            return;
        }
        try {
            ClientNet.sendToServer(new ClientHello(NetProtocol.PROTOCOL_VERSION));
            McaiClientMod.LOGGER.info("[mcai-client] 已向服务器发送 ClientHello（协议 {}）", NetProtocol.PROTOCOL_VERSION);
        } catch (RuntimeException error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 发送 ClientHello 失败", error);
            helloPending = true;
        }
    }
}
