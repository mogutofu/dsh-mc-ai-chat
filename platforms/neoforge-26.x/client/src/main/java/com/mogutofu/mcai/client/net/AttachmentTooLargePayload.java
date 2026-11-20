package com.mogutofu.mcai.client.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** S→C（预留）：服务端拒绝过大的附件时告知客户端。 */
public final class AttachmentTooLargePayload implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttachmentTooLargePayload> TYPE =
            new CustomPacketPayload.Type<>(McaiIds.of(NetProtocol.PAYLOAD_ATTACHMENT_TOO_LARGE));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentTooLargePayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                        buffer.writeUUID(payload.transferId);
                        buffer.writeUtf(payload.reason);
                    },
                    buffer -> new AttachmentTooLargePayload(buffer.readUUID(), buffer.readUtf()));

    public final UUID transferId;
    public final String reason;

    public AttachmentTooLargePayload(UUID transferId, String reason) {
        this.transferId = transferId;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public CustomPacketPayload.Type<AttachmentTooLargePayload> type() {
        return TYPE;
    }
}
