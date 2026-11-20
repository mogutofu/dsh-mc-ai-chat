package com.mogutofu.mcai.forge.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

import java.util.UUID;

/** index 4，S→C：服务端拒绝过大的附件时告知客户端（客户端只提示，不影响主流程）。 */
public final class AttachmentTooLarge implements IMessage {

    public UUID transferId;
    public String reason;

    public AttachmentTooLarge() {
    }

    public AttachmentTooLarge(UUID transferId, String reason) {
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
