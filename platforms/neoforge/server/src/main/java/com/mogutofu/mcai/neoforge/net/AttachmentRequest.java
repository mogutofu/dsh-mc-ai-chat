package com.mogutofu.mcai.neoforge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** S→C：让客户端截图或读取本地文件并发回来。字段与 1.2.0 一致。 */
public record AttachmentRequest(UUID transferId, byte kind, String path, int maxBytes)
        implements CustomPacketPayload {

    public static final Type<AttachmentRequest> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("mcai", "attachment_request"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentRequest> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AttachmentRequest decode(FriendlyByteBuf buffer) {
            return new AttachmentRequest(buffer.readUUID(), buffer.readByte(), buffer.readUtf(), buffer.readInt());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, AttachmentRequest message) {
            buffer.writeUUID(message.transferId());
            buffer.writeByte(message.kind());
            buffer.writeUtf(message.path() == null ? "" : message.path());
            buffer.writeInt(message.maxBytes());
        }
    };

    public AttachmentRequest {
        path = path == null ? "" : path;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
