package com.mogutofu.mcai.neoforge;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 聊天栏反馈统一走这里：{@code /ai} 用 {@code [小铜] } 前缀，
 * 天气投票用 {@code [天气] } 前缀（与 Forge 1.2.0 一致）。
 */
public final class ChatFeedback {

    private ChatFeedback() {
    }

    public static void notice(ServerPlayer player, String text, ChatFormatting color) {
        if (player == null || text == null || text.isEmpty()) {
            return;
        }
        player.sendSystemMessage(Component.literal("[小铜] " + text).withStyle(color));
    }

    public static void thinking(ServerPlayer player) {
        notice(player, "正在输入…", ChatFormatting.DARK_GRAY);
    }

    /** 全服广播（在线 0 人也安全）：给天气投票用。 */
    public static void broadcast(MinecraftServer server, String text, ChatFormatting color) {
        if (server == null || text == null || text.isEmpty()) {
            return;
        }
        server.getPlayerList().broadcastSystemMessage(
                Component.literal("[天气] " + text).withStyle(color), false);
    }

    /** 附件链路失败时的降级：提示原因，消息非空就退回纯文本请求。 */
    public static void degradeToText(MinecraftServer server, ServerPlayer player, String message, String reason) {
        if (player == null) {
            return;
        }
        notice(player, reason, ChatFormatting.GRAY);
        if (message == null || message.trim().isEmpty()) {
            return;
        }
        notice(player, "已改为只发送消息", ChatFormatting.DARK_GRAY);
        thinking(player);
        AiHandler.ask(server, player, message);
    }
}
