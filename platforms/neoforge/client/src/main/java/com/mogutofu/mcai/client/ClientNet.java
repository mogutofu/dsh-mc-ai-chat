package com.mogutofu.mcai.client;

import com.mogutofu.mcai.client.net.AttachmentAbortPayload;
import com.mogutofu.mcai.client.net.AttachmentChunkPayload;
import com.mogutofu.mcai.client.net.AttachmentRequestPayload;
import com.mogutofu.mcai.client.net.AttachmentTooLargePayload;
import com.mogutofu.mcai.client.net.ClientHelloPayload;
import com.mogutofu.mcai.client.net.NetProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 客户端侧 mcai:net 通道。
 *
 * <p>5 个 payload id 必须与服务端模组完全一致。收的两个（AttachmentRequest /
 * AttachmentTooLarge）挂真正的 handler；发的三个（ClientHello / AttachmentChunk /
 * AttachmentAbort）也要注册，否则编码时按 id 找不到 codec。</p>
 */
public final class ClientNet {

    private static volatile boolean ready;

    private ClientNet() {
    }

    /** 由 {@link McaiClientMod} 在模组总线上注册。 */
    public static synchronized void registerPayloads(RegisterPayloadHandlersEvent event) {
        if (ready) {
            return;
        }
        PayloadRegistrar registrar = event.registrar(NetProtocol.VERSION);

        // 收：服务端让客户端截图/读文件
        registrar.playToClient(AttachmentRequestPayload.TYPE, AttachmentRequestPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> onClientThread(() -> AttachmentTransfer.handle(payload))));
        // 收：服务端拒绝附件时的提示
        registrar.playToClient(AttachmentTooLargePayload.TYPE, AttachmentTooLargePayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> onClientThread(() -> AttachmentTransfer.onTooLarge(payload))));

        // 发：C→S，客户端不会收到，注册 codec 用于编码发送
        registrar.playToServer(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC, (payload, context) -> {
        });
        registrar.playToServer(AttachmentChunkPayload.TYPE, AttachmentChunkPayload.STREAM_CODEC, (payload, context) -> {
        });
        registrar.playToServer(AttachmentAbortPayload.TYPE, AttachmentAbortPayload.STREAM_CODEC, (payload, context) -> {
        });

        ready = true;
        McaiClientMod.LOGGER.info("[mcai-client] 已注册网络通道 mcai:*（协议 {}）", NetProtocol.VERSION);
    }

    public static boolean isReady() {
        return ready;
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (!ready) {
            throw new IllegalStateException("mcai-client 网络通道未注册");
        }
        ClientPacketDistributor.sendToServer(payload);
    }

    private static void onClientThread(Runnable runnable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(runnable);
    }
}
