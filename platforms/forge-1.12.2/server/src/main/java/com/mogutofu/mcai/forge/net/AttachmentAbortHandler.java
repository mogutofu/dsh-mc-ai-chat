package com.mogutofu.mcai.forge.net;

import com.mogutofu.mcai.forge.PendingTransfers;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** index 3 的服务端处理：客户端放弃，降级成只发文字。 */
public final class AttachmentAbortHandler implements IMessageHandler<AttachmentAbort, IMessage> {

    @Override
    public IMessage onMessage(final AttachmentAbort message, MessageContext context) {
        final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        final EntityPlayerMP player = MessageSources.player(context);
        if (server == null || player == null || message == null) {
            return null;
        }
        server.addScheduledTask(() -> PendingTransfers.onAbort(player, message));
        return null;
    }
}
