package com.mogutofu.mcai.forge.client;

import com.mogutofu.mcai.forge.client.net.AttachmentAbort;
import com.mogutofu.mcai.forge.client.net.AttachmentChunk;
import com.mogutofu.mcai.forge.client.net.AttachmentRequest;
import com.mogutofu.mcai.forge.client.net.AttachmentRequestHandler;
import com.mogutofu.mcai.forge.client.net.AttachmentTooLarge;
import com.mogutofu.mcai.forge.client.net.AttachmentTooLargeHandler;
import com.mogutofu.mcai.forge.client.net.ClientHello;
import com.mogutofu.mcai.forge.client.net.NetProtocol;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 客户端侧 {@code mcai:net} 通道（1.12.2 SimpleImpl）。
 *
 * <p>只在物理客户端注册（与服务端模组互斥：服务端模组只在 DEDICATED_SERVER 注册），
 * 5 个消息索引必须与服务端完全一致。C→S 的消息在客户端只会用 {@code sendToServer}，
 * 收到的永远不是它们，所以注册空 handler 占位。</p>
 */
public final class ClientNet {

    private static volatile SimpleNetworkWrapper channel;

    private ClientNet() {
    }

    /** 由 {@link McaiClientMod} 在 preInit 调用。 */
    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        SimpleNetworkWrapper created = NetworkRegistry.INSTANCE.newSimpleChannel(NetProtocol.CHANNEL);

        // index 0：ClientHello（C→S）
        IMessageHandler<ClientHello, IMessage> helloNoop = (message, context) -> null;
        created.registerMessage(helloNoop, ClientHello.class, 0, Side.SERVER);

        // index 1：AttachmentRequest（S→C，真正干活的）
        created.registerMessage(new AttachmentRequestHandler(), AttachmentRequest.class, 1, Side.CLIENT);

        // index 2：AttachmentChunk（C→S）
        IMessageHandler<AttachmentChunk, IMessage> chunkNoop = (message, context) -> null;
        created.registerMessage(chunkNoop, AttachmentChunk.class, 2, Side.SERVER);

        // index 3：AttachmentAbort（C→S）
        IMessageHandler<AttachmentAbort, IMessage> abortNoop = (message, context) -> null;
        created.registerMessage(abortNoop, AttachmentAbort.class, 3, Side.SERVER);

        // index 4：AttachmentTooLarge（S→C，只提示）
        created.registerMessage(new AttachmentTooLargeHandler(), AttachmentTooLarge.class, 4, Side.CLIENT);

        channel = created;
        McaiClientMod.LOGGER.info("[mcai-client] 已注册网络通道 {}（协议 {}）", NetProtocol.CHANNEL, NetProtocol.VERSION);
    }

    public static boolean isReady() {
        return channel != null;
    }

    public static void sendToServer(ClientHello message) {
        requireChannel().sendToServer(message);
    }

    public static void sendToServer(AttachmentChunk message) {
        requireChannel().sendToServer(message);
    }

    public static void sendToServer(AttachmentAbort message) {
        requireChannel().sendToServer(message);
    }

    private static SimpleNetworkWrapper requireChannel() {
        SimpleNetworkWrapper current = channel;
        if (current == null) {
            throw new IllegalStateException("mcai-client 网络通道未注册");
        }
        return current;
    }
}
