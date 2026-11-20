package com.mogutofu.mcai.neoforge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** S→C（预留）：服务端拒绝过大的附件时告知客户端。字段与 1.2.0 一致。 */
public record AttachmentTooLarge(UUID transferId, String reason) implements CustomPacketPayload {

    public static final Type<AttachmentTooLarge> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("mcai", "attachment_too_large"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentTooLarge> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AttachmentTooLarge decode(FriendlyByteBuf buffer) {
            return new AttachmentTooLarge(buffer.readUUID(), buffer.readUtf());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, AttachmentTooLarge message) {
            buffer.writeUUID(message.transferId());
            buffer.writeUtf(message.reason() == null ? "" : message.reason());
        }
    };

    public AttachmentTooLarge {
        reason = reason == null ? "" : reason;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
