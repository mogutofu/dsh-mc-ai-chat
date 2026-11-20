package com.mogutofu.mcai.forge.client.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/** index 1，S→C：服务端让客户端截图或读取本地文件。 */
public final class AttachmentRequest implements IMessage {

    public UUID transferId;
    public byte kind;
    public String path;
    public int maxBytes;

    public AttachmentRequest() {
    }

    public AttachmentRequest(UUID transferId, byte kind, String path, int maxBytes) {
        this.transferId = transferId;
        this.kind = kind;
        this.path = path == null ? "" : path;
        this.maxBytes = maxBytes;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        transferId = new UUID(buf.readLong(), buf.readLong());
        kind = buf.readByte();
        path = ByteBufUtils.readUTF8String(buf);
        maxBytes = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        UUID id = transferId == null ? new UUID(0L, 0L) : transferId;
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        buf.writeByte(kind);
        ByteBufUtils.writeUTF8String(buf, path == null ? "" : path);
        buf.writeInt(maxBytes);
    }
}
