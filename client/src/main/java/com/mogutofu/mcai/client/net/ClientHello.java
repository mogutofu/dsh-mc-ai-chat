package com.mogutofu.mcai.client.net;

import net.minecraft.network.FriendlyByteBuf;

/** index 0，C→S：客户端模组登录时告诉服务端「我装了 mcai-client」。 */
public final class ClientHello {

    public final int protocolVersion;

    public ClientHello(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    public static void encode(ClientHello message, FriendlyByteBuf buffer) {
        buffer.writeInt(message.protocolVersion);
    }

    public static ClientHello decode(FriendlyByteBuf buffer) {
        return new ClientHello(buffer.readInt());
    }
}
