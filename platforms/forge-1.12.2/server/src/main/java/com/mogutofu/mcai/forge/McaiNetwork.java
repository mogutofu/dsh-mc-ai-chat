package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.forge.net.AttachmentAbort;
import com.mogutofu.mcai.forge.net.AttachmentAbortHandler;
import com.mogutofu.mcai.forge.net.AttachmentChunk;
import com.mogutofu.mcai.forge.net.AttachmentChunkHandler;
import com.mogutofu.mcai.forge.net.AttachmentRequest;
import com.mogutofu.mcai.forge.net.AttachmentTooLarge;
import com.mogutofu.mcai.forge.net.ClientHello;
import com.mogutofu.mcai.forge.net.ClientHelloHandler;
import com.mogutofu.mcai.forge.net.NetProtocol;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

/**
 * 服务端侧 {@code mcai:net} 通道（1.12.2 SimpleImpl）。
 *
 * <p>只在专用服务器（DEDICATED_SERVER）注册：单机 integrated server 不支持附件，
 * 在客户端 JVM 里由 mcai-client 模组注册同名通道，避免同一 JVM 两个 jar 重复注册。</p>
 *
 * <p>5 个消息索引与客户端模组严格一致：0 ClientHello、1 AttachmentRequest、
 * 2 AttachmentChunk、3 AttachmentAbort、4 AttachmentTooLarge。服务端不会收到的
 * S→C 两个消息也注册占位 handler，保证两侧索引一致。</p>
 */
public final class McaiNetwork {

    private static volatile SimpleNetworkWrapper channel;

    private McaiNetwork() {
    }

    /** 由 {@link McaiMod} 在 preInit 调用（仅专用服务器）。 */
    public static synchronized void register() {
        if (channel != null) {
            return;
        }
        SimpleNetworkWrapper created = NetworkRegistry.INSTANCE.newSimpleChannel(NetProtocol.CHANNEL);

        // index 0：客户端 hello（C→S）
        created.registerMessage(new ClientHelloHandler(), ClientHello.class, 0, Side.SERVER);

        // index 1：S→C，服务端只注册占位，保证索引一致
        IMessageHandler<AttachmentRequest, IMessage> requestNoop = (message, context) -> null;
        created.registerMessage(requestNoop, AttachmentRequest.class, 1, Side.CLIENT);

        // index 2：附件分块（C→S）
        created.registerMessage(new AttachmentChunkHandler(), AttachmentChunk.class, 2, Side.SERVER);

        // index 3：客户端放弃（C→S）
        created.registerMessage(new AttachmentAbortHandler(), AttachmentAbort.class, 3, Side.SERVER);

        // index 4：S→C，服务端只注册占位
        IMessageHandler<AttachmentTooLarge, IMessage> tooLargeNoop = (message, context) -> null;
        created.registerMessage(tooLargeNoop, AttachmentTooLarge.class, 4, Side.CLIENT);

        channel = created;
        McaiMod.LOGGER.info("[mcai] 已注册网络通道 {}（协议 {}）", NetProtocol.CHANNEL, NetProtocol.VERSION);
    }

    public static boolean isReady() {
        return channel != null;
    }

    /** @return true 表示已经交给网络层发送 */
    public static boolean sendAttachmentRequest(EntityPlayerMP player, AttachmentRequest message) {
        SimpleNetworkWrapper current = channel;
        if (current == null || player == null || message == null) {
            return false;
        }
        current.sendTo(message, player);
        return true;
    }

    public static void sendTooLarge(EntityPlayerMP player, AttachmentTooLarge message) {
        SimpleNetworkWrapper current = channel;
        if (current == null || player == null || message == null) {
            return;
        }
        try {
            current.sendTo(message, player);
        } catch (RuntimeException ignored) {
            // 客户端刚好掉线之类的，忽略即可
        }
    }
}
