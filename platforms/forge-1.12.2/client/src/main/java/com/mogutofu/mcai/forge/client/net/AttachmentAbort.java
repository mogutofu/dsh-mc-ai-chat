package com.mogutofu.mcai.forge.client.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/** index 3，C→S：客户端读取附件失败时告诉服务端原因。 */
public final class AttachmentAbort implements IMessage {

    public UUID transferId;
    public String reason;

    public AttachmentAbort() {
    }

    public AttachmentAbort(UUID transferId, String reason) {
        this.transferId = transferId;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        transferId = new UUID(buf.readLong(), buf.readLong());
        reason = ByteBufUtils.readUTF8String(buf);
    }

    @Override
    public void toBytes(ByteBuf buf) {
        UUID id = transferId == null ? new UUID(0L, 0L) : transferId;
        buf.writeLong(id.getMostSignificantBits());
        buf.writeLong(id.getLeastSignificantBits());
        ByteBufUtils.writeUTF8String(buf, reason == null ? "" : reason);
    }
}
