package com.mogutofu.mcai.net;

import net.minecraft.network.FriendlyByteBuf;

import java.util.UUID;

/** index 1，S→C：让客户端截图或读取本地文件并发回来。 */
public final class AttachmentRequest {

    public final UUID transferId;
    public final byte kind;
    public final String path;
    public final int maxBytes;

    public AttachmentRequest(UUID transferId, byte kind, String path, int maxBytes) {
        this.transferId = transferId;
        this.kind = kind;
        this.path = path == null ? "" : path;
        this.maxBytes = maxBytes;
    }

    public static void encode(AttachmentRequest message, FriendlyByteBuf buffer) {
        buffer.writeUUID(message.transferId);
        buffer.writeByte(message.kind);
        buffer.writeUtf(message.path);
        buffer.writeInt(message.maxBytes);
    }

    public static AttachmentRequest decode(FriendlyByteBuf buffer) {
        return new AttachmentRequest(buffer.readUUID(), buffer.readByte(), buffer.readUtf(), buffer.readInt());
    }
}
