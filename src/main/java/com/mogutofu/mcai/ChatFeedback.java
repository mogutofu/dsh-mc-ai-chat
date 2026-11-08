package com.mogutofu.mcai;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 聊天栏反馈统一走这里，保证提示格式跟旧版一致（{@code [小铜] 前缀}）。
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

    /**
     * 附件链路失败时的降级：提示原因，消息非空就退回旧版纯文本请求。
     */
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
        AiHttpClient.ask(server, player, message);
    }
}
