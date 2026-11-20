package com.mogutofu.mcai.forge.client.net;

import com.mogutofu.mcai.forge.client.AttachmentTransfer;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

/** index 1 的客户端处理：切回客户端主线程去截图/读文件。 */
public final class AttachmentRequestHandler implements IMessageHandler<AttachmentRequest, IMessage> {

    @Override
    public IMessage onMessage(final AttachmentRequest message, MessageContext context) {
        if (message == null) {
            return null;
        }
        Minecraft minecraft = Minecraft.getMinecraft();
        minecraft.addScheduledTask(() -> AttachmentTransfer.handle(message));
        return null;
    }
}
