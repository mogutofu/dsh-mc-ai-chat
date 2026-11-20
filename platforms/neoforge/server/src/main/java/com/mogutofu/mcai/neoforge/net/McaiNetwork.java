package com.mogutofu.mcai.neoforge.net;

import com.mogutofu.mcai.neoforge.McaiMod;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 服务端侧 mcai:net 载荷。
 *
 * <p>只在专用服务器（DEDICATED_SERVER）注册：单机 integrated server 不支持附件，
 * 在客户端 JVM 里由 mcai-client 模组注册同名载荷，避免同一 JVM 两个 jar 重复注册。</p>
 *
 * <p>所有载荷都声明为 optional：客户端/服务端任意一边没装都能正常连线，
 * 有客户端模组且服务端也装了 mcai 时附件才启用。</p>
 */
public final class McaiNetwork {

    private static volatile boolean ready = false;

    private McaiNetwork() {
    }

    /** 由 {@link McaiMod} 在构造时挂到 mod 事件总线。 */
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        if (FMLEnvironment.dist != Dist.DEDICATED_SERVER) {
            McaiMod.LOGGER.info("[mcai] 当前不是专用服务器，mcai:net 附件载荷不注册（附件仅支持专用服务器）");
            return;
        }
        PayloadRegistrar registrar = event.registrar(NetProtocol.VERSION).optional();

        // C→S：客户端 hello
        registrar.playToServer(ClientHello.TYPE, ClientHello.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sender) {
                ClientPresence.markHello(sender, payload.protocolVersion());
            }
        });

        // S→C：服务端不会收到，注册占位后才能向客户端发送
        registrar.playToClient(AttachmentRequest.TYPE, AttachmentRequest.STREAM_CODEC, (payload, context) -> {
        });

        // C→S：附件分块
        registrar.playToServer(AttachmentChunk.TYPE, AttachmentChunk.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sender) {
                PendingTransfers.onChunk(sender, payload);
            }
        });

        // C→S：客户端放弃
        registrar.playToServer(AttachmentAbort.TYPE, AttachmentAbort.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer sender) {
                PendingTransfers.onAbort(sender, payload);
            }
        });

        // S→C：预留，服务端只注册
        registrar.playToClient(AttachmentTooLarge.TYPE, AttachmentTooLarge.STREAM_CODEC, (payload, context) -> {
        });

        ready = true;
        McaiMod.LOGGER.info("[mcai] 已注册 mcai:net 附件载荷（协议 {}，仅专用服务器）", NetProtocol.VERSION);
    }

    public static boolean isReady() {
        return ready;
    }

    /** @return true 表示已经交给网络层发送 */
    public static boolean sendAttachmentRequest(ServerPlayer player, AttachmentRequest message) {
        if (!ready || player == null) {
            return false;
        }
        try {
            PacketDistributor.sendToPlayer(player, message);
            return true;
        } catch (RuntimeException error) {
            McaiMod.LOGGER.warn("[mcai] 给 {} 发送附件请求失败：{}", player.getGameProfile().getName(), error.toString());
            return false;
        }
    }

    public static void sendTooLarge(ServerPlayer player, AttachmentTooLarge message) {
        if (!ready || player == null) {
            return;
        }
        try {
            PacketDistributor.sendToPlayer(player, message);
        } catch (RuntimeException ignored) {
            // 客户端刚好掉线之类的，忽略即可
        }
    }
}
