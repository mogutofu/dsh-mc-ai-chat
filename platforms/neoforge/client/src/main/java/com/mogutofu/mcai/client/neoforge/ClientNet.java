package com.mogutofu.mcai.client.neoforge;

import com.mogutofu.mcai.client.neoforge.net.AttachmentAbort;
import com.mogutofu.mcai.client.neoforge.net.AttachmentChunk;
import com.mogutofu.mcai.client.neoforge.net.AttachmentRequest;
import com.mogutofu.mcai.client.neoforge.net.AttachmentTooLarge;
import com.mogutofu.mcai.client.neoforge.net.BridgePayload;
import com.mogutofu.mcai.client.neoforge.net.BridgeRegisterPayload;
import com.mogutofu.mcai.client.neoforge.net.ClientHello;
import com.mogutofu.mcai.client.neoforge.net.NetProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 客户端侧 mcai:net 载荷 + 1.4.0 的附件桥接载荷。
 *
 * <p>只在物理客户端注册（服务端模组只在 DEDICATED_SERVER 注册），5 个原载荷的
 * id、版本与序列化格式必须和服务端完全一致；{@code mcai:bridge} 只给 Bukkit 插件用。</p>
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

        registerBridge(registrar);

        ready = true;
        McaiClientMod.LOGGER.info("[mcai-client] 已注册 mcai:net 附件载荷（协议 {}）", NetProtocol.VERSION);
    }

    /** 1.4.0 附件桥接：只注册 mcai:bridge；minecraft:register 兼容包失败也不影响原有功能。 */
    private static void registerBridge(PayloadRegistrar registrar) {
        try {
            registrar.playToClient(BridgePayload.TYPE, BridgePayload.STREAM_CODEC,
                    (payload, context) -> BridgeClient.handleFrame(payload.data()));
            BridgeClient.markReady();
        } catch (Throwable error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 注册 mcai:bridge 载荷失败：{}", error.toString());
        }
        try {
            // 兼容包：Paper 的 sendPluginMessage 需要客户端声明通道
            registrar.playToServer(BridgeRegisterPayload.TYPE, BridgeRegisterPayload.STREAM_CODEC,
                    (payload, context) -> {
                    });
        } catch (Throwable error) {
            McaiClientMod.LOGGER.info("[mcai-client] minecraft:register 兼容载荷注册被跳过：{}", error.toString());
        }
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
