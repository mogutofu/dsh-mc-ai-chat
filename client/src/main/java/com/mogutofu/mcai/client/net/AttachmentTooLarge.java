package com.mogutofu.mcai.client.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** index 4，S→C（预留）：服务端拒绝过大的附件时告知客户端。 */
public final class AttachmentTooLarge {

    public final UUID transferId;
    public final String reason;

    public AttachmentTooLarge(UUID transferId, String reason) {
        this.transferId = transferId;
        this.reason = reason == null ? "" : reason;
    }

    public static void encode(AttachmentTooLarge message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.transferId);
        buffer.writeUtf(message.reason);
    }

    public static AttachmentTooLarge decode(FriendlyByteBuf buffer) {
        return new AttachmentTooLarge(buffer.readUUID(), buffer.readUtf());
    }
}
