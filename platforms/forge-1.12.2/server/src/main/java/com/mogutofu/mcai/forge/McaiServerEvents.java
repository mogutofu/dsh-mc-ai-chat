package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.core.WeatherVoteService;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.PlayerEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.UUID;

/**
 * 服务端每 tick / 玩家下线事件（1.12.2 没有 {@code @Mod.EventBusSubscriber}，
 * 由 {@link McaiMod} 在构造时注册到 {@code MinecraftForge.EVENT_BUS}）。
 *
 * <p>每 tick 干两件事：附件传输 15 秒超时检查、天气投票超时检查（后者内部每 20 tick 才判一次）。</p>
 */
public final class McaiServerEvents {

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event == null || !(event.player instanceof EntityPlayerMP)) {
            return;
        }
        EntityPlayer player = event.player;
        UUID playerId = player.getUniqueID();
        ClientPresence.remove(playerId);
        PendingTransfers.onPlayerLogout(playerId);
        WeatherVoteService service = McaiMod.weatherService();
        if (service != null) {
            service.logout(playerId);
        }
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event == null || event.phase != TickEvent.Phase.END) {
            return;
        }
        PendingTransfers.onServerTick();
        WeatherVoteService service = McaiMod.weatherService();
        if (service != null) {
            service.tick();
        }
    }
}
