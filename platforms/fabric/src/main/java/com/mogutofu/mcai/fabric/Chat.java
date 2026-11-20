package com.mogutofu.mcai.fabric;

import com.mogutofu.mcai.core.AiClient;
import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.core.Attachment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 聊天栏反馈统一走这里，保证提示格式跟 1.2.0 一致（{@code [小铜] } 前缀）。
 */
public final class Chat {

    private Chat() {
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
     * 问一次 AI（可带一个附件），结果直接发到玩家聊天栏。
     *
     * <p>这里只在调用线程（服务端主线程）上读取玩家信息，之后的闭包里只用
     * {@link UUID} 和名字，不跨线程碰 {@link ServerPlayer} 对象。</p>
     */
    public static void ask(MinecraftServer server, ServerPlayer player, String text, Attachment attachment) {
        if (server == null || player == null) {
            return;
        }
        AiSettings settings = McaiFabric.aiSettings();
        UUID playerId = player.getUUID();
        String playerName = player.getName().getString();
        String safeText = text == null ? "" : text;

        AiClient.ask(settings, playerId, playerName, safeText, attachment,
                message -> McaiFabric.LOGGER.info("[mcai] " + message),
                (ok, result) -> deliver(server, playerId, ok, result));
    }

    /**
     * 附件链路失败时的降级：提示原因，消息非空就退回纯文本请求。
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
        ask(server, player, message, null);
    }

    private static void deliver(MinecraftServer server, UUID playerId, boolean ok, String text) {
        try {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return; // 玩家已经下线，这条回复就丢掉
                }
                player.sendSystemMessage(render(ok, text));
            });
        } catch (RuntimeException shuttingDown) {
            // 服务端正在关服，任务队列不收活了；这时候丢消息是正确行为
            McaiFabric.LOGGER.debug("[mcai] 服务端已停止，丢弃一条 /ai 回复");
        }
    }

    private static Component render(boolean ok, String text) {
        if (ok) {
            return Component.literal("[小铜] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(text).withStyle(ChatFormatting.AQUA));
        }
        return Component.literal("[小铜] 出错了：").withStyle(ChatFormatting.RED)
                .append(Component.literal(text).withStyle(ChatFormatting.RED));
    }
}
