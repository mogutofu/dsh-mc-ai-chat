package com.mogutofu.mcai.client;

import com.mogutofu.mcai.client.net.AttachmentAbortPayload;
import com.mogutofu.mcai.client.net.AttachmentChunkPayload;
import com.mogutofu.mcai.client.net.AttachmentRequestPayload;
import com.mogutofu.mcai.client.net.AttachmentTooLargePayload;
import com.mogutofu.mcai.client.net.BridgePayload;
import com.mogutofu.mcai.client.net.BridgeRegisterPayload;
import com.mogutofu.mcai.client.net.ClientHelloPayload;
import com.mogutofu.mcai.client.net.Protocol;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * 客户端侧 mcai:net 的 Fabric 接线。
 *
 * <p>只注册客户端需要的方向：C2S 三个（hello/chunk/abort）用于发送，
 * S2C 两个（request/too_large）用于接收；payload ID 必须与服务端模组一致。</p>
 */
public final class ClientNet {

    private static volatile boolean ready;

    private ClientNet() {
    }

    public static void register() {
        PayloadTypeRegistry c2s = registry("serverboundPlay", "playC2S");
        PayloadTypeRegistry s2c = registry("clientboundPlay", "playS2C");
        c2s.register(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC);
        c2s.register(AttachmentChunkPayload.TYPE, AttachmentChunkPayload.STREAM_CODEC);
        c2s.register(AttachmentAbortPayload.TYPE, AttachmentAbortPayload.STREAM_CODEC);
        s2c.register(AttachmentRequestPayload.TYPE, AttachmentRequestPayload.STREAM_CODEC);
        s2c.register(AttachmentTooLargePayload.TYPE, AttachmentTooLargePayload.STREAM_CODEC);

        ClientPlayNetworking.registerGlobalReceiver(AttachmentRequestPayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientAttachmentTransfer.handle(payload)));
        ClientPlayNetworking.registerGlobalReceiver(AttachmentTooLargePayload.TYPE, (payload, context) ->
                context.client().execute(() -> ClientAttachmentTransfer.onTooLarge(payload)));

        registerBridge(c2s, s2c);

        // 进服务器时告诉服务端「我装了 mcai-client」；服务器没装服务端模组就什么都不发。
        // 1.4.0：再补一次 Bukkit 桥接握手（声明通道 + HELLO）。
        ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> {
            sendHello();
            BridgeClient.onLogin();
        });

        ready = true;
        McaiClientFabric.LOGGER.info(
                "[mcai-client] 已注册 Fabric payload mcai:hello/chunk/abort（发）与 mcai:request/too_large（收），协议 {}",
                Protocol.PROTOCOL_VERSION);
    }

    /** 1.4.0 附件桥接：mcai:bridge 双向 + minecraft:register 兼容包。 */
    private static void registerBridge(PayloadTypeRegistry c2s, PayloadTypeRegistry s2c) {
        try {
            c2s.register(BridgePayload.TYPE, BridgePayload.STREAM_CODEC);
            s2c.register(BridgePayload.TYPE, BridgePayload.STREAM_CODEC);
            ClientPlayNetworking.registerGlobalReceiver(BridgePayload.TYPE, (payload, context) ->
                    context.client().execute(() -> BridgeClient.handleFrame(payload.data())));
            BridgeClient.markReady();
        } catch (Throwable error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 注册 mcai:bridge 载荷失败：{}", error.toString());
        }
        try {
            c2s.register(BridgeRegisterPayload.TYPE, BridgeRegisterPayload.STREAM_CODEC);
        } catch (Throwable error) {
            McaiClientFabric.LOGGER.info("[mcai-client] minecraft:register 兼容载荷注册被跳过：{}", error.toString());
        }
    }

    /**
     * 26.x 的 Fabric API 把 {@code PayloadTypeRegistry.playC2S()/playS2C()} 改名成
     * {@code serverboundPlay()/clientboundPlay()}；三个 MC 版本共用一个源码集，
     * 两个名字都试，哪个存在用哪个。
     */
    private static PayloadTypeRegistry registry(String primary, String fallback) {
        for (String name : new String[]{primary, fallback}) {
            try {
                Object value = PayloadTypeRegistry.class.getMethod(name).invoke(null);
                if (value instanceof PayloadTypeRegistry registry) {
                    return registry;
                }
            } catch (Throwable ignored) {
                // 换下一个名字
            }
        }
        throw new IllegalStateException(
                "Fabric API 的 PayloadTypeRegistry 没有 " + primary + "() / " + fallback + "()，版本不兼容");
    }

    private static void sendHello() {
        try {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.getConnection() == null) {
                return;
            }
            if (!ClientPlayNetworking.canSend(ClientHelloPayload.TYPE)) {
                McaiClientFabric.LOGGER.info("[mcai-client] 服务器没有 mcai 服务端模组，跳过 ClientHello");
                return;
            }
            ClientPlayNetworking.send(new ClientHelloPayload(Protocol.PROTOCOL_VERSION));
            McaiClientFabric.LOGGER.info("[mcai-client] 已向服务器发送 ClientHello（协议 {}）",
                    Protocol.PROTOCOL_VERSION);
        } catch (RuntimeException error) {
            McaiClientFabric.LOGGER.warn("[mcai-client] 发送 ClientHello 失败：{}", error.toString());
        }
    }

    public static boolean isReady() {
        return ready;
    }

    public static void sendToServer(ClientHelloPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(AttachmentChunkPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    public static void sendToServer(AttachmentAbortPayload payload) {
        ClientPlayNetworking.send(payload);
    }

    /**
     * 绕过 Fabric 的 {@code canSend}（服务端没声明通道就拒绝发送），直接发原版自定义载荷。
     *
     * <p>Bukkit 服务器不会有 mcai 通道声明，1.4.0 的附件桥接握手必须走这条原始路径。</p>
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

    /** 客户端能不能往当前服务器发分块（服务器得装了 mcai 服务端模组）。 */
    public static boolean canSendChunk() {
        return ClientPlayNetworking.canSend(AttachmentChunkPayload.TYPE);
    }
}
