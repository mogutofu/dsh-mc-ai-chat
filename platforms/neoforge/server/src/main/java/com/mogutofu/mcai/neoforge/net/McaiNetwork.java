package com.mogutofu.mcai.neoforge.net;

import com.mogutofu.mcai.neoforge.McaiMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 服务端侧 mcai:net 附件通道。
 *
 * <p>只在专用服务器注册 payload：单机 integrated server 不支持附件，而且客户端 jar
 * 在同一个 JVM 里会注册同一批 payload id，两边都注册会撞车。没有通道时
 * {@code /ai screenshot}、{@code /ai file} 自动降级为只发文字。</p>
 *
 * <p>5 个 payload id 必须与 mcai-client 的 {@code com.mogutofu.mcai.client.net} 完全一致。</p>
 */
public final class McaiNetwork {

    private static volatile boolean ready;

    private McaiNetwork() {
    }

    /** 由 {@link McaiMod} 在模组总线上注册（{@code RegisterPayloadHandlersEvent}）。 */
    public static synchronized void registerPayloads(RegisterPayloadHandlersEvent event) {
        if (ready) {
            return;
        }
        if (!FMLEnvironment.getDist().isDedicatedServer()) {
            McaiMod.LOGGER.info("[mcai] 当前不是专用服务器，mcai:net 通道不注册（附件仅支持专用服务器）");
            return;
        }
        PayloadRegistrar registrar = event.registrar(NetProtocol.VERSION);

        // index 0：客户端 hello
        registrar.playToServer(ClientHelloPayload.TYPE, ClientHelloPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ClientPresence.markHello(player, payload.protocolVersion);
                    }
                }));

        // index 1：S→C，服务端不收；注册是为了编码发送时能按 id 找到 codec
        registrar.playToClient(AttachmentRequestPayload.TYPE, AttachmentRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                });

        // index 2：附件分块
        registrar.playToServer(AttachmentChunkPayload.TYPE, AttachmentChunkPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PendingTransfers.onChunk(player, payload);
                    }
                }));

        // index 3：客户端放弃
        registrar.playToServer(AttachmentAbortPayload.TYPE, AttachmentAbortPayload.STREAM_CODEC,
                (payload, context) -> context.enqueueWork(() -> {
                    if (context.player() instanceof ServerPlayer player) {
                        PendingTransfers.onAbort(player, payload);
                    }
                }));

        // index 4：S→C，服务端只注册发送用的 codec
        registrar.playToClient(AttachmentTooLargePayload.TYPE, AttachmentTooLargePayload.STREAM_CODEC,
                (payload, context) -> {
                });

        ready = true;
        McaiMod.LOGGER.info("[mcai] 已注册网络通道 mcai:*（协议 {}）", NetProtocol.VERSION);
    }

    public static boolean isReady() {
        return ready;
    }

    /** @return true 表示已经交给网络层发送 */
    public static boolean sendAttachmentRequest(ServerPlayer player, AttachmentRequestPayload payload) {
        if (!ready || player == null) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, payload);
        return true;
    }

    public static void sendTooLarge(ServerPlayer player, AttachmentTooLargePayload payload) {
        if (!ready || player == null) {
            return;
        }
        try {
            PacketDistributor.sendToPlayer(player, payload);
        } catch (RuntimeException ignored) {
            // 客户端刚好掉线之类的，忽略即可
        }
    }
}
