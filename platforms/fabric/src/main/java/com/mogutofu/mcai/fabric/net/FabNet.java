package com.mogutofu.mcai.fabric.net;

import com.mogutofu.mcai.fabric.ClientPresence;
import com.mogutofu.mcai.fabric.McaiFabric;
import com.mogutofu.mcai.fabric.PendingTransfers;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.level.ServerPlayer;

/**
 * 服务端侧的 Fabric networking 接线。
 *
 * <p>只在服务端模组（mcai，environment=server）里注册：客户端模组 mcai_client 自己
 * 注册同名 payload 的另一半，两个 jar 不会同时加载。</p>
 */
public final class FabNet {

    private static volatile boolean ready;

    private FabNet() {
    }

    /** 在 {@code onInitialize()} 里注册 payload 类型（C2S 接收 + S2C 发送）。 */
    public static void registerPayloads() {
        PayloadTypeRegistry.playC2S().register(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AttachmentChunkPayload.TYPE, AttachmentChunkPayload.STREAM_CODEC);
        PayloadTypeRegistry.playC2S().register(AttachmentAbortPayload.TYPE, AttachmentAbortPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AttachmentRequestPayload.TYPE, AttachmentRequestPayload.STREAM_CODEC);
        PayloadTypeRegistry.playS2C().register(AttachmentTooLargePayload.TYPE, AttachmentTooLargePayload.STREAM_CODEC);
        ready = true;
    }

    /** 注册 C→S 三个消息的接收器；处理逻辑统一切回服务端主线程。 */
    public static void registerServerReceivers() {
        ServerPlayNetworking.registerGlobalReceiver(ClientHelloPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> ClientPresence.markHello(player, payload.protocolVersion()));
        });
        ServerPlayNetworking.registerGlobalReceiver(AttachmentChunkPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> PendingTransfers.onChunk(player, payload));
        });
        ServerPlayNetworking.registerGlobalReceiver(AttachmentAbortPayload.TYPE, (payload, context) -> {
            ServerPlayer player = context.player();
            context.server().execute(() -> PendingTransfers.onAbort(player, payload));
        });
        McaiFabric.LOGGER.info("[mcai] 已注册 Fabric payload mcai:hello/chunk/abort（收）与 mcai:request/too_large（发），协议 {}",
                Protocol.PROTOCOL_VERSION);
    }

    public static boolean isReady() {
        return ready;
    }

    /** @return true 表示已经交给 Fabric 网络层发送 */
    public static boolean sendRequest(ServerPlayer player, AttachmentRequestPayload payload) {
        if (!ready || player == null) {
            return false;
        }
        if (!ServerPlayNetworking.canSend(player, AttachmentRequestPayload.TYPE)) {
            return false;
        }
        ServerPlayNetworking.send(player, payload);
        return true;
    }

    /** 每个服务端 tick 调一次：检查附件 15 秒超时。 */
    public static void onServerTick() {
        PendingTransfers.onServerTick();
    }

    /** 关服清理：待传输状态和 ClientHello 记录都清掉。 */
    public static void onServerStop() {
        PendingTransfers.clear();
        ClientPresence.clear();
    }

    public static void sendTooLarge(ServerPlayer player, AttachmentTooLargePayload payload) {
        if (!ready || player == null) {
            return;
        }
        try {
            if (ServerPlayNetworking.canSend(player, AttachmentTooLargePayload.TYPE)) {
                ServerPlayNetworking.send(player, payload);
            }
        } catch (RuntimeException ignored) {
            // 客户端刚好掉线之类的，忽略即可
        }
    }
}
