package com.mogutofu.mcai.neoforge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** C→S：客户端读取附件失败时告诉服务端原因。字段与 1.2.0 一致。 */
public record AttachmentAbort(UUID transferId, String reason) implements CustomPacketPayload {

    public static final Type<AttachmentAbort> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("mcai", "attachment_abort"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentAbort> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AttachmentAbort decode(FriendlyByteBuf buffer) {
            return new AttachmentAbort(buffer.readUUID(), buffer.readUtf());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, AttachmentAbort message) {
            buffer.writeUUID(message.transferId());
            buffer.writeUtf(message.reason() == null ? "" : message.reason());
        }
    };

    public AttachmentAbort {
        reason = reason == null ? "" : reason;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
