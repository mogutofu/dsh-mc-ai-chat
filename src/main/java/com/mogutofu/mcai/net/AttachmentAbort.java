package com.mogutofu.mcai.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** index 3，C→S：客户端读取附件失败时告诉服务端原因。 */
public final class AttachmentAbort {

    public final UUID transferId;
    public final String reason;

    public AttachmentAbort(UUID transferId, String reason) {
        this.transferId = transferId;
        this.reason = reason == null ? "" : reason;
    }

    public static void encode(AttachmentAbort message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.transferId);
        buffer.writeUtf(message.reason);
    }

    public static AttachmentAbort decode(FriendlyByteBuf buffer) {
        return new AttachmentAbort(buffer.readUUID(), buffer.readUtf());
    }
}
