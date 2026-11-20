package com.mogutofu.mcai.forge.net;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.lang.reflect.Field;

/**
 * 从 {@link MessageContext} 拿到发包玩家。
 *
 * <p>1.12.2 里 C→S 消息的玩家挂在 {@code NetHandlerPlayServer} 上，但那个字段在开发环境叫
 * {@code player}、在生产环境是 srg 名（{@code field_147369_b}），写死任何一个都会在另一半
 * 环境里炸。所以这里按「字段类型是 EntityPlayerMP」找一次并缓存，去混淆/重混淆都不受影响。</p>
 */
public final class MessageSources {

    private static volatile Field playerField;

    private MessageSources() {
    }

    /** @return 发包玩家；不在服务端/已经断线时返回 null */
    public static EntityPlayerMP player(MessageContext context) {
        if (context == null) {
            return null;
        }
        Object handler;
        try {
            handler = context.getServerHandler();
        } catch (RuntimeException notServerSide) {
            return null;
        }
        if (handler == null) {
            return null;
        }
        try {
            Field field = playerField;
            if (field == null) {
                for (Field candidate : handler.getClass().getDeclaredFields()) {
                    if (candidate.getType() == EntityPlayerMP.class) {
                        candidate.setAccessible(true);
                        field = candidate;
                        break;
                    }
                }
                if (field == null) {
                    return null;
                }
                playerField = field;
            }
            return (EntityPlayerMP) field.get(handler);
        } catch (Throwable broken) {
            return null;
        }
    }
}
