package com.mogutofu.mcai.forge;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.ITextComponent;
import net.minecraft.util.text.Style;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.util.text.TextFormatting;

import java.util.List;

/**
 * 聊天栏反馈统一走这里，保证提示格式跟 1.20.1 / 1.2.0 一致（{@code [小铜] } 前缀，
 * 天气广播用 {@code [天气] } 前缀）。
 */
final class ChatFeedback {

    private ChatFeedback() {
    }

    static void notice(EntityPlayerMP player, String text, TextFormatting color) {
        if (player == null || text == null || text.isEmpty()) {
            return;
        }
        player.sendMessage(new TextComponentString("[小铜] " + text).setStyle(new Style().setColor(color)));
    }

    static void thinking(EntityPlayerMP player) {
        notice(player, "正在输入…", TextFormatting.DARK_GRAY);
    }

    /** 全服广播（在线 0 人也安全）：给天气投票用，同时留一份服务端日志。 */
    static void broadcast(MinecraftServer server, String text, TextFormatting color) {
        if (server == null || text == null || text.isEmpty()) {
            return;
        }
        ITextComponent message = new TextComponentString("[天气] " + text).setStyle(new Style().setColor(color));
        List<EntityPlayerMP> players = server.getPlayerList().getPlayers();
        for (EntityPlayerMP player : players) {
            if (player != null) {
                player.sendMessage(message);
            }
        }
        McaiMod.LOGGER.info("[天气] " + text);
    }

    /** 附件链路失败/无客户端时的降级：提示原因，消息非空就退回纯文本请求。 */
    static void degradeToText(MinecraftServer server, EntityPlayerMP player, String message, String reason) {
        if (player == null) {
            return;
        }
        notice(player, reason, TextFormatting.GRAY);
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        notice(player, "已改为只发送消息", TextFormatting.DARK_GRAY);
        thinking(player);
        AiRequests.ask(server, player, message);
    }
}
