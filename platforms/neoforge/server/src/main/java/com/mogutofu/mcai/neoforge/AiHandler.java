package com.mogutofu.mcai.neoforge;

import com.mogutofu.mcai.core.AiClient;
import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.core.Attachment;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * 跟 DSH 侧 {@code /mc-ai} 说话的那部分（服务端）。
 *
 * <p>用公共核心的 {@link AiClient} 异步 POST；回调在 HTTP 线程，这里再切回
 * 服务端主线程（{@link MinecraftServer#execute(Runnable)}）发聊天栏消息，
 * 全程不阻塞主线程。请求/响应契约与 1.2.0 一致。</p>
 */
public final class AiHandler {

    private AiHandler() {
    }

    /** 旧版纯文本请求。 */
    public static void ask(MinecraftServer server, ServerPlayer player, String text) {
        ask(server, player, text, null);
    }

    /** 问一次 AI（可带一个附件），结果直接发到玩家聊天栏。 */
    public static void ask(MinecraftServer server, ServerPlayer player, String text, Attachment attachment) {
        if (server == null || player == null) {
            return;
        }
        UUID playerId = player.getUUID();
        String playerName = player.getGameProfile().getName();
        AiSettings settings = AiSettings.load(McaiMod.commonConfigFile());

        AiClient.ask(settings, playerId, playerName, text == null ? "" : text, attachment,
                message -> McaiMod.LOGGER.info("[mcai] {}", message),
                (ok, reply) -> deliver(server, playerId, ok, reply));
    }

    private static void deliver(MinecraftServer server, UUID playerId, boolean ok, String reply) {
        try {
            server.execute(() -> {
                ServerPlayer player = server.getPlayerList().getPlayer(playerId);
                if (player == null) {
                    return; // 玩家已经下线，这条回复就丢掉
                }
                player.sendSystemMessage(render(ok, reply));
            });
        } catch (RuntimeException shuttingDown) {
            // 服务端正在关服，任务队列不收活了；这时候丢消息是正确行为
            McaiMod.LOGGER.debug("[mcai] 服务端已停止，丢弃一条 /ai 回复");
        }
    }

    private static Component render(boolean ok, String text) {
        String safe = text == null ? "" : text;
        if (ok) {
            return Component.literal("[小铜] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(safe).withStyle(ChatFormatting.AQUA));
        }
        return Component.literal("[小铜] 出错了：").withStyle(ChatFormatting.RED)
                .append(Component.literal(safe).withStyle(ChatFormatting.RED));
    }
}
