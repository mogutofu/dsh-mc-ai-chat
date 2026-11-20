package com.mogutofu.mcai.neoforge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/** C→S：附件分块。data 长度 ≤ {@link NetProtocol#CHUNK_SIZE}。字段与 1.2.0 一致。 */
public record AttachmentChunk(UUID transferId, int chunkIndex, int chunkCount, String fileName,
                              String mediaType, boolean image, int totalBytes, byte[] data)
        implements CustomPacketPayload {

    public static final Type<AttachmentChunk> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("mcai", "attachment_chunk"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentChunk> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public AttachmentChunk decode(FriendlyByteBuf buffer) {
            UUID transferId = buffer.readUUID();
            int chunkIndex = buffer.readInt();
            int chunkCount = buffer.readInt();
            String fileName = buffer.readUtf();
            String mediaType = buffer.readUtf();
            boolean image = buffer.readBoolean();
            int totalBytes = buffer.readInt();
            byte[] data = buffer.readByteArray();
            return new AttachmentChunk(transferId, chunkIndex, chunkCount, fileName, mediaType, image, totalBytes, data);
        }

        @Override
        public void encode(FriendlyByteBuf buffer, AttachmentChunk message) {
            buffer.writeUUID(message.transferId());
            buffer.writeInt(message.chunkIndex());
            buffer.writeInt(message.chunkCount());
            buffer.writeUtf(message.fileName() == null ? "" : message.fileName());
            buffer.writeUtf(message.mediaType() == null ? "" : message.mediaType());
            buffer.writeBoolean(message.image());
            buffer.writeInt(message.totalBytes());
            buffer.writeByteArray(message.data() == null ? new byte[0] : message.data());
        }
    };

    public AttachmentChunk {
        fileName = fileName == null ? "" : fileName;
        mediaType = mediaType == null ? "" : mediaType;
        data = data == null ? new byte[0] : data;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
