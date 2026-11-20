package com.mogutofu.mcai.client.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** 客户端注册的 {@code mcai:hello}：C→S，登录时告诉服务端「我装了 mcai-client」。 */
public record ClientHelloPayload(int protocolVersion) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientHelloPayload> TYPE =
            new CustomPacketPayload.Type<>(Protocol.id("hello"));

    public static final StreamCodec<FriendlyByteBuf, ClientHelloPayload> STREAM_CODEC = StreamCodec.of(
            (buffer, payload) -> buffer.writeVarInt(payload.protocolVersion()),
            buffer -> new ClientHelloPayload(buffer.readVarInt()));

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
