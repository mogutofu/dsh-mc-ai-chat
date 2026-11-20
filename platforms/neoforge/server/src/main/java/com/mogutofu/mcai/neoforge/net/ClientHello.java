package com.mogutofu.mcai.neoforge.net;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** C→S：客户端模组登录时告诉服务端「我装了 mcai-client」。字段与 1.2.0 一致。 */
public record ClientHello(int protocolVersion) implements CustomPacketPayload {

    public static final Type<ClientHello> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath("mcai", "client_hello"));

    public static final StreamCodec<FriendlyByteBuf, ClientHello> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ClientHello decode(FriendlyByteBuf buffer) {
            return new ClientHello(buffer.readInt());
        }

        @Override
        public void encode(FriendlyByteBuf buffer, ClientHello message) {
            buffer.writeInt(message.protocolVersion());
        }
    };

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
