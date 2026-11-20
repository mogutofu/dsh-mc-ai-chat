package com.mogutofu.mcai.neoforge.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** C→S：附件分块，data 长度 ≤ {@link NetProtocol#CHUNK_SIZE}。 */
public final class AttachmentChunkPayload implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttachmentChunkPayload> TYPE =
            new CustomPacketPayload.Type<>(McaiIds.of(NetProtocol.PAYLOAD_ATTACHMENT_CHUNK));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentChunkPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                        buffer.writeUUID(payload.transferId);
                        buffer.writeInt(payload.chunkIndex);
                        buffer.writeInt(payload.chunkCount);
                        buffer.writeUtf(payload.fileName);
                        buffer.writeUtf(payload.mediaType);
                        buffer.writeBoolean(payload.image);
                        buffer.writeInt(payload.totalBytes);
                        buffer.writeInt(payload.data.length);
                        buffer.writeBytes(payload.data);
                    },
                    buffer -> {
                        UUID transferId = buffer.readUUID();
                        int chunkIndex = buffer.readInt();
                        int chunkCount = buffer.readInt();
                        String fileName = buffer.readUtf();
                        String mediaType = buffer.readUtf();
                        boolean image = buffer.readBoolean();
                        int totalBytes = buffer.readInt();
                        int length = buffer.readInt();
                        byte[] data = new byte[Math.max(0, length)];
                        buffer.readBytes(data);
                        return new AttachmentChunkPayload(transferId, chunkIndex, chunkCount, fileName, mediaType,
                                image, totalBytes, data);
                    });

    public final UUID transferId;
    public final int chunkIndex;
    public final int chunkCount;
    public final String fileName;
    public final String mediaType;
    public final boolean image;
    public final int totalBytes;
    public final byte[] data;

    public AttachmentChunkPayload(UUID transferId, int chunkIndex, int chunkCount, String fileName, String mediaType,
                                  boolean image, int totalBytes, byte[] data) {
        this.transferId = transferId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.fileName = fileName == null ? "" : fileName;
        this.mediaType = mediaType == null ? "" : mediaType;
        this.image = image;
        this.totalBytes = totalBytes;
        this.data = data == null ? new byte[0] : data;
    }

    @Override
    public CustomPacketPayload.Type<AttachmentChunkPayload> type() {
        return TYPE;
    }
}
