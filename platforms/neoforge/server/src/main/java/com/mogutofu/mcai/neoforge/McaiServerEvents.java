package com.mogutofu.mcai.neoforge;

import com.mogutofu.mcai.neoforge.net.ClientPresence;
import com.mogutofu.mcai.neoforge.net.PendingTransfers;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * 服务端事件总线：注册指令、挂服务器实例、每 tick 检查超时、登出/关服清理。
 */
@EventBusSubscriber(modid = McaiMod.MODID)
public final class McaiServerEvents {

    private McaiServerEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        AiCommandHandler.register(event.getDispatcher());
        WeatherFeature.get().registerCommands(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        WeatherFeature.get().attach(server);
        McaiMod.LOGGER.info("[mcai] 服务端已启动，天气投票与附件通道就绪（在线 {} 人）",
                server.getPlayerList().getPlayerCount());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        PendingTransfers.clear();
        ClientPresence.clear();
        WeatherFeature.get().reset();
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ClientPresence.remove(player.getUUID());
        PendingTransfers.onPlayerLogout(player.getUUID());
        WeatherFeature.get().logout(player.getUUID());
        AiBridge.clearCooldown(player.getUUID());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        PendingTransfers.onServerTick(server);
        WeatherFeature.get().tick();
    }
}
