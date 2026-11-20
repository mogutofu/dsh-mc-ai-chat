package com.mogutofu.mcai.fabric;

import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.core.Cooldown;
import com.mogutofu.mcai.core.WeatherSettings;
import com.mogutofu.mcai.fabric.net.FabNet;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.UUID;

/**
 * mc-ai 的 Fabric 服务端模组（mod id {@code mcai}）。
 *
 * <p>提供：{@code /ai}（文字 + 截图/文件附件，附件需要可选客户端模组 mcai-client）、
 * {@code /weather} 与 {@code /vote}（玩家天气投票）。HTTP / 投票逻辑都在
 * {@code common} 的 {@code com.mogutofu.mcai.core} 里，这里只做平台适配。</p>
 *
 * <p>配置：{@code config/mcai-common.json}（endpoint/timeoutSeconds/enabled/rateLimitSeconds）、
 * {@code config/mcai-weather.json}（天气投票，每次 /weather 重新读）。</p>
 */
public final class McaiFabric implements ModInitializer {

    public static final String MODID = "mcai";
    public static final Logger LOGGER = LoggerFactory.getLogger(MODID);

    private static final Cooldown COOLDOWN = new Cooldown();

    private static File aiConfigFile;
    private static File weatherConfigFile;
    private static volatile MinecraftServer server;

    @Override
    public void onInitialize() {
        File gameDir = FabricLoader.getInstance().getGameDir().toFile();
        File configDir = new File(gameDir, "config");
        aiConfigFile = new File(configDir, "mcai-common.json");
        weatherConfigFile = new File(configDir, "mcai-weather.json");
        AiSettings.writeDefaultIfMissing(aiConfigFile);
        writeWeatherDefaultIfMissing();

        FabNet.registerPayloads();
        FabNet.registerServerReceivers();

        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            AiCommands.register(dispatcher);
            Weather.register(dispatcher);
        });

        ServerTickEvents.END_SERVER_TICK.register(currentServer -> {
            server = currentServer;
            FabNet.onServerTick();
            Weather.tick();
        });

        ServerLifecycleEvents.SERVER_STARTED.register(currentServer -> server = currentServer);

        ServerPlayConnectionEvents.DISCONNECT.register((handler, currentServer) -> {
            ServerPlayer player;
            try {
                player = handler.getPlayer();
            } catch (Throwable broken) {
                return;
            }
            if (player == null) {
                return;
            }
            UUID playerId = player.getUUID();
            ClientPresence.remove(playerId);
            PendingTransfers.onPlayerLogout(playerId);
            Weather.logout(playerId);
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(currentServer -> {
            FabNet.onServerStop();
            Weather.reset();
            server = null;
        });

        LOGGER.info("[mcai] mc-ai Fabric 服务端已加载（/ai 文字聊天 + 截图/文件附件 + /weather 天气投票）");
    }

    // ── 给各模块用的状态 ─────────────────────────────────────────────

    /** 每次读配置：改完 config/mcai-common.json 不用重启。 */
    public static AiSettings aiSettings() {
        return AiSettings.load(aiConfigFile);
    }

    public static Cooldown cooldown() {
        return COOLDOWN;
    }

    public static File weatherConfigFile() {
        return weatherConfigFile;
    }

    public static MinecraftServer server() {
        return server;
    }

    // ── 内部 ─────────────────────────────────────────────────────────

    private static void writeWeatherDefaultIfMissing() {
        if (weatherConfigFile == null || weatherConfigFile.isFile()) {
            return;
        }
        try {
            File parent = weatherConfigFile.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            Files.write(weatherConfigFile.toPath(),
                    new WeatherSettings(true, 2, 2, 1, 60, 60).defaultJson().getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            // 写不出来不影响默认配置
        }
    }
}
