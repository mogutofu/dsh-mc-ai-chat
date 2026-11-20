package com.mogutofu.mcai.fabric.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** 服务端注册的 {@code mcai:too_large}：S→C，服务端拒绝超大附件时的提示（可空实现）。 */
public record AttachmentTooLargePayload(UUID transferId, String reason) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttachmentTooLargePayload> TYPE =
            new CustomPacketPayload.Type<>(Protocol.id("too_large"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentTooLargePayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.transferId());
                buffer.writeUtf(payload.reason() == null ? "" : payload.reason());
            },
            buffer -> new AttachmentTooLargePayload(buffer.readUUID(), buffer.readUtf()));

    public AttachmentTooLargePayload {
        reason = reason == null ? "" : reason;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
