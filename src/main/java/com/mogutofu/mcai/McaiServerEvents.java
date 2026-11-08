package com.mogutofu.mcai;

import com.mogutofu.mcai.net.ClientPresence;
import com.mogutofu.mcai.net.PendingTransfers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 服务端杂项事件：登出清理 hello/待传输状态、每 tick 检查附件超时、关服清理。
 */
@Mod.EventBusSubscriber(modid = McaiMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class McaiServerEvents {

    private McaiServerEvents() {
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ClientPresence.remove(player.getUUID());
            PendingTransfers.onPlayerLogout(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            PendingTransfers.onServerTick();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PendingTransfers.clear();
        ClientPresence.clear();
    }
}