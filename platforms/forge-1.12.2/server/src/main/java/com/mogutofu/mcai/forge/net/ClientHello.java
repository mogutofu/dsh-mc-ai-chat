package com.mogutofu.mcai.forge.net;

import io.netty.buffer.ByteBuf;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;

/** index 0，C→S：客户端模组登录后告诉服务端「我装了 mcai-client」。 */
public final class ClientHello implements IMessage {

    public int protocolVersion;

    /** SimpleImpl 反序列化需要一个空构造函数。 */
    public ClientHello() {
    }

    public ClientHello(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        protocolVersion = buf.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeInt(protocolVersion);
    }
}
