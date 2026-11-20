package com.mogutofu.mcai.bukkit;

import com.mogutofu.mcai.core.AiSettings;
import com.mogutofu.mcai.core.Cooldown;
import com.mogutofu.mcai.core.WeatherSettings;
import com.mogutofu.mcai.core.WeatherVoteService;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Bukkit / Spigot / Paper 版 mc-ai。
 *
 * <p>只做文字 {@code /ai}（没有客户端附件），但完整实现 {@code /weather} + {@code /vote}
 * 天气投票，配置读服务器目录 {@code config/mcai-weather.json}（与 Forge 模组同字段/默认值）。</p>
 */
public final class McaiPlugin extends JavaPlugin implements WeatherVoteService.Host {

    private final Cooldown cooldown = new Cooldown();
    private WeatherVoteService weatherService;

    @Override
    public void onEnable() {
        AiSettings.writeDefaultIfMissing(aiConfigFile());
        writeWeatherDefaultIfMissing();

        weatherService = new WeatherVoteService(this);
        getServer().getPluginManager().registerEvents(new WeatherInterceptor(this), this);
        getServer().getPluginManager().registerEvents(new PlayerListener(this), this);

        PluginCommand ai = getCommand("ai");
        if (ai != null) {
            AiCommand executor = new AiCommand(this);
            ai.setExecutor(executor);
            ai.setTabCompleter(executor);
        }
        PluginCommand vote = getCommand("vote");
        if (vote != null) {
            VoteCommand executor = new VoteCommand(this);
            vote.setExecutor(executor);
            vote.setTabCompleter(executor);
        }

        // 天气投票超时检查：每 tick 调一次，内部每 20 tick 判定一次。
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (weatherService != null) {
                weatherService.tick();
            }
        }, 20L, 1L);

        getLogger().info("mc-ai 已加载（/ai 文字聊天；/weather + /vote 天气投票）");
    }

    @Override
    public void onDisable() {
        if (weatherService != null) {
            weatherService.reset();
        }
        cooldown.clear();
    }

    WeatherVoteService weather() {
        return weatherService;
    }

    AiSettings aiSettings() {
        return AiSettings.load(aiConfigFile());
    }

    Cooldown cooldown() {
        return cooldown;
    }

    // ── WeatherVoteService.Host ───────────────────────────────────────

    @Override
    public int onlineCount() {
        return Bukkit.getOnlinePlayers().size();
    }

    @Override
    public void send(com.mogutofu.mcai.core.Actor actor, String message) {
        String line = "[天气] " + message;
        if (actor.console) {
            getLogger().info(line);
            return;
        }
        Player player = Bukkit.getPlayer(actor.id);
        if (player != null) {
            player.sendMessage(line);
        }
    }

    @Override
    public void broadcast(String message) {
        Bukkit.getServer().broadcastMessage("[天气] " + message);
        getLogger().info("[天气] " + message);
    }

    @Override
    public void applyWeather(String type, Integer durationSeconds) {
        if (Bukkit.getWorlds().isEmpty()) {
            return;
        }
        World world = Bukkit.getWorlds().get(0);
        int ticks = durationSeconds == null
                ? WeatherVoteService.defaultDurationTicks()
                : Math.max(1, durationSeconds * 20);
        if ("clear".equals(type)) {
            world.setStorm(false);
            world.setThundering(false);
            world.setWeatherDuration(ticks);
            world.setClearWeatherDuration(ticks);
        } else if ("rain".equals(type)) {
            world.setStorm(true);
            world.setThundering(false);
            world.setWeatherDuration(ticks);
        } else if ("thunder".equals(type)) {
            world.setStorm(true);
            world.setThundering(true);
            world.setWeatherDuration(ticks);
            world.setThunderDuration(ticks);
        }
    }

    @Override
    public void log(String message) {
        getLogger().info("[mcai] " + message);
    }

    @Override
    public File configFile() {
        return new File(serverRoot(), "config/mcai-weather.json");
    }

    // ── 文件 ─────────────────────────────────────────────────────────

    private File serverRoot() {
        return Bukkit.getWorldContainer();
    }

    private File aiConfigFile() {
        return new File(serverRoot(), "config/mcai-common.json");
    }

    private void writeWeatherDefaultIfMissing() {
        File file = configFile();
        if (file.isFile()) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                parent.mkdirs();
            }
            Files.write(file.toPath(), new WeatherSettings(true, 2, 2, 1, 60, 60).defaultJson()
                    .getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
            // 写不出来不影响默认配置
        }
    }
}
