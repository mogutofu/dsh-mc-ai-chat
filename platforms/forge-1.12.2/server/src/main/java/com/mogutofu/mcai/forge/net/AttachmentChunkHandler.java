package com.mogutofu.mcai.forge.net;

import com.mogutofu.mcai.forge.PendingTransfers;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** index 2 的服务端处理：附件分块，切回主线程交给 {@link PendingTransfers}。 */
public final class AttachmentChunkHandler implements IMessageHandler<AttachmentChunk, IMessage> {

    @Override
    public IMessage onMessage(final AttachmentChunk message, MessageContext context) {
        final MinecraftServer server = FMLCommonHandler.instance().getMinecraftServerInstance();
        final EntityPlayerMP player = MessageSources.player(context);
        if (server == null || player == null || message == null) {
            return null;
        }
        server.addScheduledTask(() -> PendingTransfers.onChunk(player, message));
        return null;
    }
}
