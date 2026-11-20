package com.mogutofu.mcai.forge.client.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/** index 2，C→S：附件分块。data 长度 <= {@link NetProtocol#CHUNK_SIZE}。 */
public final class AttachmentChunk implements IMessage {

    public UUID transferId;
    public int chunkIndex;
    public int chunkCount;
    public String fileName;
    public String mediaType;
    public boolean image;
    public int totalBytes;
    public byte[] data;

    public AttachmentChunk() {
    }

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

    @Override
    public void fromBytes(ByteBuf buf) {
        transferId = new UUID(buf.readLong(), buf.readLong());
        chunkIndex = buf.readInt();
        chunkCount = buf.readInt();
        fileName = ByteBufUtils.readUTF8String(buf);
        mediaType = ByteBufUtils.readUTF8String(buf);
        image = buf.readBoolean();
        totalBytes = buf.readInt();
        int length = buf.readInt();
        if (length < 0 || length > NetProtocol.MAX_PACKET_BYTES) {
            length = 0;
        }
        data = new byte[length];
        buf.readBytes(data);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        UUID id = transferId == null ? new UUID(0L, 0L) : transferId;
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeInt(chunkIndex);
        buf.writeInt(chunkCount);
        ByteBufUtils.writeUTF8String(buf, fileName == null ? "" : fileName);
        ByteBufUtils.writeUTF8String(buf, mediaType == null ? "" : mediaType);
        buf.writeBoolean(image);
        buf.writeInt(totalBytes);
        byte[] payload = data == null ? new byte[0] : data;
        buf.writeInt(payload.length);
        buf.writeBytes(payload);
    }
}
