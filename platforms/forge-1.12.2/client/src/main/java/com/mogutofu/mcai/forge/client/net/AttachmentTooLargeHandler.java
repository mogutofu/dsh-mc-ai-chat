package com.mogutofu.mcai.forge.client.net;

import com.mogutofu.mcai.forge.client.AttachmentTransfer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** index 4 的客户端处理：服务端拒绝附件时给玩家一个提示（不影响主流程）。 */
public final class AttachmentTooLargeHandler implements IMessageHandler<AttachmentTooLarge, IMessage> {

    @Override
    public IMessage onMessage(final AttachmentTooLarge message, MessageContext context) {
        if (message == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> AttachmentTransfer.onTooLarge(message));
        return null;
    }
}
