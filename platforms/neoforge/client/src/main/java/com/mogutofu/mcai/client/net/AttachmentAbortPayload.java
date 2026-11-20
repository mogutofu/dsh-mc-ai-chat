package com.mogutofu.mcai.client.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** C→S：客户端读不到附件（或放弃）时告诉服务端原因。 */
public final class AttachmentAbortPayload implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttachmentAbortPayload> TYPE =
            new CustomPacketPayload.Type<>(McaiIds.of(NetProtocol.PAYLOAD_ATTACHMENT_ABORT));

    public static final StreamCodec<RegistryFriendlyByteBuf, AttachmentAbortPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> {
                        buffer.writeUUID(payload.transferId);
                        buffer.writeUtf(payload.reason);
                    },
                    buffer -> new AttachmentAbortPayload(buffer.readUUID(), buffer.readUtf()));

    public final UUID transferId;
    public final String reason;

    public AttachmentAbortPayload(UUID transferId, String reason) {
        this.transferId = transferId;
        this.reason = reason == null ? "" : reason;
    }

    @Override
    public CustomPacketPayload.Type<AttachmentAbortPayload> type() {
        return TYPE;
    }
}
