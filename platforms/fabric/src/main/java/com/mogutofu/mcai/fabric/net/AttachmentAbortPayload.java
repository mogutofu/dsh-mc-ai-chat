package com.mogutofu.mcai.fabric.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** 服务端注册的 {@code mcai:abort}：C→S，客户端读取/截图失败时发。 */
public record AttachmentAbortPayload(UUID transferId, String reason) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttachmentAbortPayload> TYPE =
            new CustomPacketPayload.Type<>(Protocol.id("abort"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentAbortPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.transferId());
                buffer.writeUtf(payload.reason() == null ? "" : payload.reason());
            },
            buffer -> new AttachmentAbortPayload(buffer.readUUID(), buffer.readUtf()));

    public AttachmentAbortPayload {
        reason = reason == null ? "" : reason;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
