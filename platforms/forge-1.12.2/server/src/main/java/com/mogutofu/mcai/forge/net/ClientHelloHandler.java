package com.mogutofu.mcai.forge.net;

import com.mogutofu.mcai.forge.ClientPresence;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** index 0 的服务端处理：记下这个玩家装了 mcai-client。 */
public final class ClientHelloHandler implements IMessageHandler<ClientHello, IMessage> {

    @Override
    public IMessage onMessage(final ClientHello message, MessageContext context) {
        final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        final EntityPlayerMP player = MessageSources.player(context);
        if (server == null || player == null || message == null) {
            return null;
        }
        final int protocolVersion = message.protocolVersion;
        server.addScheduledTask(() -> ClientPresence.markHello(player, protocolVersion));
        return null;
    }
}
