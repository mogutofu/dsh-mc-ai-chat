package com.mogutofu.mcai.client;

import com.mogutofu.mcai.client.net.AttachmentAbortPayload;
import com.mogutofu.mcai.client.net.AttachmentChunkPayload;
import com.mogutofu.mcai.client.net.AttachmentRequestPayload;
import com.mogutofu.mcai.client.net.AttachmentTooLargePayload;
import com.mogutofu.mcai.client.net.BridgePayload;
import com.mogutofu.mcai.client.net.BridgeRegisterPayload;
import com.mogutofu.mcai.client.net.ClientHelloPayload;
import com.mogutofu.mcai.client.net.NetProtocol;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
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

        registerBridge(registrar);

        ready = true;
        McaiClientMod.LOGGER.info("[mcai-client] 已注册网络通道 mcai:*（协议 {}）", NetProtocol.VERSION);
    }

    /** 1.4.0 附件桥接：只注册 mcai:bridge；minecraft:register 兼容包失败也不影响原有功能。 */
    private static void registerBridge(PayloadRegistrar registrar) {
        try {
            registrar.playToClient(BridgePayload.TYPE, BridgePayload.STREAM_CODEC,
                    (payload, context) -> context.enqueueWork(
                            () -> onClientThread(() -> BridgeClient.handleFrame(payload.data))));
            BridgeClient.markReady();
        } catch (Throwable error) {
            McaiClientMod.LOGGER.warn("[mcai-client] 注册 mcai:bridge 载荷失败：{}", error.toString());
        }
        try {
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
        if (!ready) {
            throw new IllegalStateException("mcai-client 网络通道未注册");
        }
        ClientPacketDistributor.sendToServer(payload);
    }

    /**
     * 绕过 NeoForge 的「服务端有没有这个通道」检查，直接发原版自定义载荷。
     *
     * <p>Bukkit 服务器不会声明 mcai 通道，{@link ClientPacketDistributor} 会拒绝发送；
     * 1.4.0 的 Bukkit 附件桥接必须走这条原始路径。</p>
     */
    public static void sendRaw(CustomPacketPayload payload) {
        if (payload == null) {
            throw new IllegalArgumentException("payload 不能为空");
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.getConnection() == null) {
            throw new IllegalStateException("不在服务器里");
        }
        minecraft.getConnection().getConnection().send(new ServerboundCustomPayloadPacket(payload));
    }

    private static void onClientThread(Runnable runnable) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft == null) {
            return;
        }
        minecraft.execute(runnable);
    }
}
