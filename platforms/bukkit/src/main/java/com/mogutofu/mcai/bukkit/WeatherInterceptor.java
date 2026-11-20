package com.mogutofu.mcai.bukkit;

import com.mogutofu.mcai.core.Actor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

/**
 * 拦截玩家输入的 {@code /weather}，替换原版需要 OP 的命令。
 *
 * <p>不注册同名 Bukkit 命令，避免和原版命令表冲突：玩家输入先经过
 * {@link PlayerCommandPreprocessEvent}，在这里处理并取消原命令；控制台
 * 仍然走原版 {@code /weather}（本来就有权限，符合“控制台直接执行”）。</p>
 */
public final class WeatherInterceptor implements Listener {

    private final McaiPlugin plugin;

    WeatherInterceptor(McaiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        String raw = event.getMessage();
        if (raw == null || raw.isEmpty() || raw.charAt(0) != '/') {
            return;
        }
        String[] parts = raw.substring(1).trim().split("\\s+");
        if (parts.length == 0) {
            return;
        }
        String label = parts[0];
        int colon = label.indexOf(':');
        if (colon >= 0) {
            label = label.substring(colon + 1);
        }
        if (!"weather".equalsIgnoreCase(label)) {
            return;
        }

        event.setCancelled(true);
        Player player = event.getPlayer();
        Actor actor = Actor.player(player.getUniqueId(), player.getName(), player.isOp());
        if (parts.length == 1) {
            plugin.weather().weatherUsage(actor);
            return;
        }
        String type = parts[1].toLowerCase();
        if (!"clear".equals(type) && !"rain".equals(type) && !"thunder".equals(type)) {
            plugin.weather().weatherUsage(actor);
            return;
        }
        Integer duration = null;
        if (parts.length >= 3) {
            try {
                duration = Integer.parseInt(parts[2]);
                if (duration <= 0) {
                    duration = null;
                }
            } catch (NumberFormatException notANumber) {
                plugin.weather().weatherUsage(actor);
                return;
            }
        }
        plugin.weather().weather(actor, type, duration);
    }
}
