package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.core.AiClient;
import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.core.Attachment;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.UUID;

/**
 * {@code /ai} 的实际请求：HTTP 交给公共核心 {@link AiClient}（Java 8 HttpURLConnection），
 * 这里只负责把回调切回服务端主线程再发聊天栏消息。
 */
final class AiRequests {

    private AiRequests() {
    }

    /** 旧版纯文本请求。 */
    static void ask(MinecraftServer server, EntityPlayerMP player, String text) {
        ask(server, player, text, null);
    }

    /** 问一次 AI（可带 1 个附件），结果直接发到玩家聊天栏。 */
    static void ask(final MinecraftServer server, final EntityPlayerMP player, String text, Attachment attachment) {
        if (server == null || player == null) {
            return;
        }
        // 只在这里（服务端主线程）读玩家信息，之后的闭包里只用 UUID，不跨线程碰实体
        final UUID playerId = player.getUniqueID();
        final String playerName = player.getName();
        AiSettings settings = AiSettings.load(McaiMod.commonConfig());

        AiClient.ask(settings, playerId, playerName, text, attachment,
                message -> McaiMod.LOGGER.info(message),
                (ok, reply) -> {
                    try {
                        server.addScheduledTask(() -> deliver(server, playerId, ok, reply));
                    } catch (RuntimeException shuttingDown) {
                        // 服务端正在关服，任务队列不收活了；这时候丢消息是正确行为
                        McaiMod.LOGGER.debug("[mcai] 服务端已停止，丢弃一条 /ai 回复");
                    }
                });
    }

    private static void deliver(MinecraftServer server, UUID playerId, boolean ok, String text) {
        EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(playerId);
        if (player == null) {
            return; // 玩家已经下线，这条回复就丢掉
        }
        player.sendMessage(render(ok, text));
    }

    private static ITextComponent render(boolean ok, String text) {
        String body = text == null ? "" : text;
        ITextComponent head;
        ITextComponent tail;
        if (ok) {
            head = new TextComponentString("[小铜] ").setStyle(new Style().setColor(TextFormatting.GOLD));
            tail = new TextComponentString(body).setStyle(new Style().setColor(TextFormatting.AQUA));
        } else {
            head = new TextComponentString("[小铜] 出错了：").setStyle(new Style().setColor(TextFormatting.RED));
            tail = new TextComponentString(body).setStyle(new Style().setColor(TextFormatting.RED));
        }
        head.appendSibling(tail);
        return head;
    }
}
