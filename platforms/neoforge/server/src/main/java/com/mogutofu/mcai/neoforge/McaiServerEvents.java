package com.mogutofu.mcai.neoforge;

import com.mogutofu.mcai.neoforge.net.ClientPresence;
import com.mogutofu.mcai.neoforge.net.PendingTransfers;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 服务端杂项事件：登出清理 hello/待传输/冷却状态、每 tick 检查附件与投票超时、关服清理。
 */
public final class McaiServerEvents {

    private McaiServerEvents() {
    }

    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClientPresence.remove(player.getUUID());
            PendingTransfers.onPlayerLogout(player.getUUID());
            AiCommand.onPlayerLogout(player.getUUID());
            WeatherVote.onPlayerLogout(player.getUUID());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        PendingTransfers.onServerTick();
        WeatherVote.onServerTick();
    }

    public static void onServerStopping(ServerStoppingEvent event) {
        PendingTransfers.clear();
        ClientPresence.clear();
        WeatherVote.reset();
    }
}
