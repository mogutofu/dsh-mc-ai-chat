package com.mogutofu.mcai.client.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** S→C：让客户端截图或读取本地文件并发回来（{@code kind} 0=截图、1=文件）。 */
public final class AttachmentRequestPayload implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttachmentRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(McaiIds.of(NetProtocol.PAYLOAD_ATTACHMENT_REQUEST));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentRequestPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                        buffer.writeUUID(payload.transferId);
                        buffer.writeByte(payload.kind);
                        buffer.writeUtf(payload.path);
                        buffer.writeInt(payload.maxBytes);
                    },
                    buffer -> new AttachmentRequestPayload(buffer.readUUID(), buffer.readByte(),
                            buffer.readUtf(), buffer.readInt()));

    public final UUID transferId;
    public final byte kind;
    public final String path;
    public final int maxBytes;

    public AttachmentRequestPayload(UUID transferId, byte kind, String path, int maxBytes) {
        this.transferId = transferId;
        this.kind = kind;
        this.path = path == null ? "" : path;
        this.maxBytes = maxBytes;
    }

    @Override
    public CustomPacketPayload.Type<AttachmentRequestPayload> type() {
        return TYPE;
    }
}
