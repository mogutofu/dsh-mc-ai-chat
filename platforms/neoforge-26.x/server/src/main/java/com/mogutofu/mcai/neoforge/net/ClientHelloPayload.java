package com.mogutofu.mcai.neoforge.net;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** C→S：客户端模组登录时告诉服务端「我装了 mcai-client」。 */
public final class ClientHelloPayload implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ClientHelloPayload> TYPE =
            new CustomPacketPayload.Type<>(McaiIds.of(NetProtocol.PAYLOAD_CLIENT_HELLO));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClientHelloPayload> STREAM_CODEC =
            StreamCodec.of((buffer, payload) -> buffer.writeInt(payload.protocolVersion),
                    buffer -> new ClientHelloPayload(buffer.readInt()));

    public final int protocolVersion;

    public ClientHelloPayload(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public CustomPacketPayload.Type<ClientHelloPayload> type() {
        return TYPE;
    }
}
