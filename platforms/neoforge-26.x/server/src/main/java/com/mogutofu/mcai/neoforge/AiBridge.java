package com.mogutofu.mcai.neoforge;

import com.mogutofu.mcai.core.AiClient;
import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.core.Attachment;
import com.mogutofu.mcai.core.Cooldown;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * {@code /ai} 的桥：common 的 {@link AiClient}（异步 HTTP）+ 服务端主线程回话。
 *
 * <p>回调在 {@code mcai-http} 线程上，这里统一用 {@link MinecraftServer#execute(Runnable)}
 * 切回服务端线程再发聊天栏消息；玩家中途下线就丢掉这条回复。</p>
 */
public final class AiBridge {

    private static final Cooldown COOLDOWN = new Cooldown();

    private AiBridge() {
    }

    /** 每次 /ai 都重新读 config/mcai-common.json。 */
    public static AiSettings settings() {
        return AiSettings.load(McaiPaths.aiConfigFile());
    }

    public static boolean tryAcquire(UUID playerId, AiSettings settings) {
        return COOLDOWN.tryAcquire(playerId, settings.rateLimitSeconds);
    }

    public static void clearCooldown(UUID playerId) {
        COOLDOWN.remove(playerId);
    }

    public static void ask(MinecraftServer server, ServerPlayer player, String text, Attachment attachment) {
        if (player == null) {
            return;
        }
        AiSettings settings = settings();
        UUID playerId = player.getUUID();
        String playerName = player.getGameProfile().name();
        McaiMod.LOGGER.info("[mcai] {} 发起 /ai 请求（附件：{}）", playerName,
                attachment == null ? "无" : attachment.name);
        AiClient.ask(settings, playerId, playerName, text == null ? "" : text, attachment,
                McaiMod.LOGGER::info,
                (ok, reply) -> deliver(server, playerId, ok, reply));
    }

    private static void deliver(MinecraftServer server, UUID playerId, boolean ok, String reply) {
        if (server == null) {
            return;
        }
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

    private static Component render(boolean ok, String reply) {
        if (ok) {
            return Component.literal("[小铜] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal(reply).withStyle(ChatFormatting.AQUA));
        }
        return Component.literal("[小铜] 出错了：").withStyle(ChatFormatting.RED)
                .append(Component.literal(reply).withStyle(ChatFormatting.RED));
    }
}
