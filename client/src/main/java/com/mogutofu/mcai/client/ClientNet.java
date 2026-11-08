package com.mogutofu.mcai.client;

import com.mogutofu.mcai.client.net.AttachmentAbort;
import com.mogutofu.mcai.client.net.AttachmentChunk;
import com.mogutofu.mcai.client.net.AttachmentRequest;
import com.mogutofu.mcai.client.net.AttachmentTooLarge;
import com.mogutofu.mcai.client.net.ClientHello;
import com.mogutofu.mcai.client.net.NetProtocol;
import net.minecraft.client.Minecraft;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 客户端侧 mcai:net 通道。
 *
 * <p>只在物理客户端注册（与服务端模组互斥：服务端模组只在 DEDICATED_SERVER 注册），
 * 5 个消息索引必须与服务端完全一致。</p>
 */
public final class ClientNet {

    private static volatile SimpleChannel channel;

    private ClientNet() {
    }

    /** 由 {@link McaiClientMod} 在客户端构造时通过 DistExecutor 调用。 */
    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        SimpleChannel created = NetworkRegistry.newSimpleChannel(
                NetProtocol.CHANNEL,
                () -> NetProtocol.VERSION,
                NetworkRegistry.acceptMissingOr(NetProtocol.VERSION),
                NetworkRegistry.acceptMissingOr(NetProtocol.VERSION));

        // index 0：ClientHello 是 C→S，客户端不会收到
        created.registerMessage(0, ClientHello.class, ClientHello::encode, ClientHello::decode,
                (message, contextSupplier) -> contextSupplier.get().setPacketHandled(true));

        // index 1：服务端让客户端截图/读文件
        created.registerMessage(1, AttachmentRequest.class, AttachmentRequest::encode, AttachmentRequest::decode,
                (message, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    context.enqueueWork(() -> Minecraft.getInstance().execute(() -> AttachmentTransfer.handle(message)));
                    context.setPacketHandled(true);
                });

        // index 2：分块是 C→S，客户端不会收到
        created.registerMessage(2, AttachmentChunk.class, AttachmentChunk::encode, AttachmentChunk::decode,
                (message, contextSupplier) -> contextSupplier.get().setPacketHandled(true));

        // index 3：abort 是 C→S，客户端不会收到
        created.registerMessage(3, AttachmentAbort.class, AttachmentAbort::encode, AttachmentAbort::decode,
                (message, contextSupplier) -> contextSupplier.get().setPacketHandled(true));

        // index 4：服务端拒绝附件时的提示
        created.registerMessage(4, AttachmentTooLarge.class, AttachmentTooLarge::encode, AttachmentTooLarge::decode,
                (message, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    context.enqueueWork(() -> Minecraft.getInstance().execute(() -> AttachmentTransfer.onTooLarge(message)));
                    context.setPacketHandled(true);
                });

        channel = created;
        McaiClientMod.LOGGER.info("[mcai-client] 已注册网络通道 {}（协议 {}）", NetProtocol.CHANNEL, NetProtocol.VERSION);
    }

    public static boolean isReady() {
        return channel != null;
    }

    public static void sendToServer(ClientHello message) {
        SimpleChannel current = requireChannel();
        current.sendToServer(message);
    }

    public static void sendToServer(AttachmentChunk message) {
        SimpleChannel current = requireChannel();
        current.sendToServer(message);
    }

    public static void sendToServer(AttachmentAbort message) {
        SimpleChannel current = requireChannel();
        current.sendToServer(message);
    }

    private static SimpleChannel requireChannel() {
        SimpleChannel current = channel;
        if (current == null) {
            throw new IllegalStateException("mcai-client 网络通道未注册");
        }
        return current;
    }
}
