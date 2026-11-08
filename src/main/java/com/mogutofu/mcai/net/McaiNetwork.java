package com.mogutofu.mcai.net;

import com.mogutofu.mcai.McaiMod;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * 服务端侧 mcai:net 通道。
 *
 * <p>只在专用服务器（DEDICATED_SERVER）注册：单机 integrated server 不支持附件，
 * 在客户端 JVM 里由 mcai-client 模组注册同名通道，避免同一 JVM 两个 jar 重复注册。</p>
 */
public final class McaiNetwork {

    private static volatile SimpleChannel channel;

    private McaiNetwork() {
    }

    /** 由 {@link com.mogutofu.mcai.McaiMod} 在构造时调用（仅专用服务器）。 */
    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        SimpleChannel created = NetworkRegistry.newSimpleChannel(
                NetProtocol.CHANNEL,
                () -> NetProtocol.VERSION,
                NetworkRegistry.acceptMissingOr(NetProtocol.VERSION),
                NetworkRegistry.acceptMissingOr(NetProtocol.VERSION));

        // index 0：客户端 hello
        created.registerMessage(0, ClientHello.class, ClientHello::encode, ClientHello::decode,
                (message, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    ServerPlayer sender = context.getSender();
                    context.enqueueWork(() -> ClientPresence.markHello(sender, message.protocolVersion));
                    context.setPacketHandled(true);
                });

        // index 1：S→C，服务端不会收到；注册占位保证两侧索引一致
        created.registerMessage(1, AttachmentRequest.class, AttachmentRequest::encode, AttachmentRequest::decode,
                (message, contextSupplier) -> contextSupplier.get().setPacketHandled(true));

        // index 2：附件分块
        created.registerMessage(2, AttachmentChunk.class, AttachmentChunk::encode, AttachmentChunk::decode,
                (message, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    ServerPlayer sender = context.getSender();
                    context.enqueueWork(() -> PendingTransfers.onChunk(sender, message));
                    context.setPacketHandled(true);
                });

        // index 3：客户端放弃
        created.registerMessage(3, AttachmentAbort.class, AttachmentAbort::encode, AttachmentAbort::decode,
                (message, contextSupplier) -> {
                    NetworkEvent.Context context = contextSupplier.get();
                    ServerPlayer sender = context.getSender();
                    context.enqueueWork(() -> PendingTransfers.onAbort(sender, message));
                    context.setPacketHandled(true);
                });

        // index 4：S→C，服务端只注册空 handler
        created.registerMessage(4, AttachmentTooLarge.class, AttachmentTooLarge::encode, AttachmentTooLarge::decode,
                (message, contextSupplier) -> contextSupplier.get().setPacketHandled(true));

        channel = created;
        McaiMod.LOGGER.info("[mcai] 已注册网络通道 {}（协议 {}）", NetProtocol.CHANNEL, NetProtocol.VERSION);
    }

    public static boolean isReady() {
        return channel != null;
    }

    /** @return true 表示已经交给网络层发送 */
    public static boolean sendAttachmentRequest(ServerPlayer player, AttachmentRequest message) {
        SimpleChannel current = channel;
        if (current == null || player == null) {
            return false;
        }
        current.send(PacketDistributor.PLAYER.with(() -> player), message);
        return true;
    }

    public static void sendTooLarge(ServerPlayer player, AttachmentTooLarge message) {
        SimpleChannel current = channel;
        if (current == null || player == null) {
            return;
        }
        try {
            current.send(PacketDistributor.PLAYER.with(() -> player), message);
        } catch (RuntimeException ignored) {
            // 客户端刚好掉线之类的，忽略即可
        }
    }
}
