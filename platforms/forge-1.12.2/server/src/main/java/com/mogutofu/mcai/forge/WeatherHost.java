package com.mogutofu.mcai.forge;

import com.mogutofu.mcai.core.Actor;
import com.mogutofu.mcai.core.WeatherVoteService;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextFormatting;
import net.minecraft.world.World;
import net.minecraft.world.storage.WorldInfo;

import java.io.File;

/**
 * {@link WeatherVoteService.Host} 的 1.12.2 实现：在线人数、聊天栏、主世界天气、配置路径。
 *
 * <p>天气用原版 {@code WorldInfo} 的四个字段改（跟 1.12.2 的 {@code /weather} 一样）：
 * clear → cleanWeatherTime；rain/thunder → rainTime + thunderTime。</p>
 */
final class WeatherHost implements WeatherVoteService.Host {

    private final MinecraftServer server;

    WeatherHost(MinecraftServer server) {
        this.server = server;
    }

    @Override
    public int onlineCount() {
        return server.getPlayerList().getCurrentPlayerCount();
    }

    @Override
    public void send(Actor actor, String message) {
        if (actor == null || message == null) {
            return;
        }
        if (!actor.console) {
            EntityPlayerMP player = server.getPlayerList().getPlayerByUUID(actor.id);
            if (player != null) {
                ChatFeedback.notice(player, message, TextFormatting.GRAY);
                return;
            }
        }
        // 控制台 / 玩家已经下线：留日志，别丢消息
        McaiMod.LOGGER.info("[天气] " + message);
    }

    @Override
    public void broadcast(String message) {
        ChatFeedback.broadcast(server, message, TextFormatting.YELLOW);
    }

    @Override
    public void applyWeather(String type, Integer durationSeconds) {
        World world = server.getWorld(0);
        if (world == null) {
            McaiMod.LOGGER.warn("[mcai] 找不到主世界，天气没有改");
            return;
        }
        WorldInfo info = world.getWorldInfo();
        int ticks = durationSeconds == null ? WeatherVoteService.defaultDurationTicks() : durationSeconds * 20;
        if ("clear".equals(type)) {
            info.setCleanWeatherTime(ticks);
            info.setRainTime(0);
            info.setThunderTime(0);
            info.setRaining(false);
            info.setThundering(false);
        } else if ("rain".equals(type)) {
            info.setCleanWeatherTime(0);
            info.setRainTime(ticks);
            info.setThunderTime(ticks);
            info.setRaining(true);
            info.setThundering(false);
        } else if ("thunder".equals(type)) {
            info.setCleanWeatherTime(0);
            info.setRainTime(ticks);
            info.setThunderTime(ticks);
            info.setRaining(true);
            info.setThundering(true);
        }
    }

    @Override
    public void log(String message) {
        McaiMod.LOGGER.info("[天气] " + message);
    }

    @Override
    public File configFile() {
        return McaiMod.weatherConfig();
    }
}
