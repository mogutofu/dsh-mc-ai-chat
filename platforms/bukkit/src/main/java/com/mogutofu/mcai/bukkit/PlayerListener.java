package com.mogutofu.mcai.bukkit;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** 玩家下线：清 /ai 冷却、收尾天气投票、丢掉附件桥接的 token / pending。 */
public final class PlayerListener implements Listener {

    private final McaiPlugin plugin;

    PlayerListener(McaiPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.cooldown().remove(event.getPlayer().getUniqueId());
        plugin.weather().logout(event.getPlayer().getUniqueId());
        if (plugin.bridge() != null) {
            plugin.bridge().forget(event.getPlayer());
        }
    }
}
