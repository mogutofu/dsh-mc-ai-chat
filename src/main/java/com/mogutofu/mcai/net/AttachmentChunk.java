package com.mogutofu.mcai.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** index 2，C→S：附件分块。data 长度 <= NetProtocol.CHUNK_SIZE。 */
public final class AttachmentChunk {

    public final UUID transferId;
    public final int chunkIndex;
    public final int chunkCount;
    public final String fileName;
    public final String mediaType;
    public final boolean image;
    public final int totalBytes;
    public final byte[] data;

    public AttachmentChunk(UUID transferId, int chunkIndex, int chunkCount, String fileName, String mediaType,
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

    public static void encode(AttachmentChunk message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.transferId);
        buffer.writeInt(message.chunkIndex);
        buffer.writeInt(message.chunkCount);
        buffer.writeUtf(message.fileName);
        buffer.writeUtf(message.mediaType);
        buffer.writeBoolean(message.image);
        buffer.writeInt(message.totalBytes);
        buffer.writeByteArray(message.data);
    }

    public static AttachmentChunk decode(FriendlyByteBuf buffer) {
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
}
