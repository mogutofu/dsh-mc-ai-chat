package com.mogutofu.mcai.client.neoforge;

import com.mogutofu.mcai.client.neoforge.net.AttachmentAbort;
import com.mogutofu.mcai.client.neoforge.net.AttachmentChunk;
import com.mogutofu.mcai.client.neoforge.net.AttachmentRequest;
import com.mogutofu.mcai.client.neoforge.net.AttachmentTooLarge;
import com.mogutofu.mcai.client.neoforge.net.ClientHello;
import com.mogutofu.mcai.client.neoforge.net.NetProtocol;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 客户端侧 mcai:net 载荷。
 *
 * <p>只在物理客户端注册（服务端模组只在 DEDICATED_SERVER 注册），5 个载荷的
 * id、版本与序列化格式必须和服务端完全一致。</p>
 */
public final class ClientNet {

    private static volatile boolean ready = false;

    private ClientNet() {
    }

    /** 由 {@link ClientBootstrap} 挂到 mod 事件总线。 */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        PayloadRegistrar registrar = event.registrar(NetProtocol.VERSION).optional();

        // S→C：服务端让客户端截图/读文件
        registrar.playToClient(AttachmentRequest.TYPE, AttachmentRequest.STREAM_CODEC,
                (payload, context) -> AttachmentTransfer.handle(payload));

        // S→C：服务端拒绝附件时的提示（预留）
        registrar.playToClient(AttachmentTooLarge.TYPE, AttachmentTooLarge.STREAM_CODEC,
                (payload, context) -> AttachmentTransfer.onTooLarge(payload));

        // C→S：客户端只发送，不会收到；no-op handler 保证载荷在客户端注册可发
        registrar.playToServer(ClientHello.TYPE, ClientHello.STREAM_CODEC, (payload, context) -> {
        });
        registrar.playToServer(AttachmentChunk.TYPE, AttachmentChunk.STREAM_CODEC, (payload, context) -> {
        });
        registrar.playToServer(AttachmentAbort.TYPE, AttachmentAbort.STREAM_CODEC, (payload, context) -> {
        });

        ready = true;
        McaiClientMod.LOGGER.info("[mcai-client] 已注册 mcai:net 附件载荷（协议 {}）", NetProtocol.VERSION);
    }

    public static boolean isReady() {
        return ready;
    }

    public static void sendToServer(CustomPacketPayload payload) {
        if (!ready || payload == null) {
            return;
        }
        PacketDistributor.sendToServer(payload);
    }
}
