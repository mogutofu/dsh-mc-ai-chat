package com.mogutofu.mcai.fabric.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.UUID;

/** 服务端注册的 {@code mcai:chunk}：C→S，附件分块（data ≤ 24KB）。 */
public record AttachmentChunkPayload(UUID transferId, int chunkIndex, int chunkCount,
                                     String fileName, String mediaType, boolean image,
                                     int totalBytes, byte[] data) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<AttachmentChunkPayload> TYPE =
            new CustomPacketPayload.Type<>(Protocol.id("chunk"));

    public static final StreamCodec<FriendlyByteBuf, AttachmentChunkPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> {
                buffer.writeUUID(payload.transferId());
                buffer.writeInt(payload.chunkIndex());
                buffer.writeInt(payload.chunkCount());
                buffer.writeUtf(payload.fileName());
                buffer.writeUtf(payload.mediaType());
                buffer.writeBoolean(payload.image());
                buffer.writeInt(payload.totalBytes());
                buffer.writeByteArray(payload.data());
            },
            buffer -> new AttachmentChunkPayload(
                    buffer.readUUID(),
                    buffer.readInt(),
                    buffer.readInt(),
                    buffer.readUtf(),
                    buffer.readUtf(),
                    buffer.readBoolean(),
                    buffer.readInt(),
                    buffer.readByteArray()));

    public AttachmentChunkPayload {
        fileName = fileName == null ? "" : fileName;
        mediaType = mediaType == null ? "" : mediaType;
        data = data == null ? new byte[0] : data;
    }

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
