package com.mogutofu.mcai.fabric.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** 服务端注册的 {@code mcai:request}：S→C，让客户端截图或读取本地文件并发回来。 */
public record AttachmentRequestPayload(UUID transferId, byte kind, String path, int maxBytes)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttachmentRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Protocol.id("request"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.transferId());
                buffer.writeByte(payload.kind());
                buffer.writeUtf(payload.path());
                buffer.writeInt(payload.maxBytes());
            },
            buffer -> new AttachmentRequestPayload(
                    buffer.readUUID(), buffer.readByte(), buffer.readUtf(), buffer.readInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
