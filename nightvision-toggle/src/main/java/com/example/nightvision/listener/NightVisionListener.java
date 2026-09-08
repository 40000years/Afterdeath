package com.example.nightvision.listener;

import com.example.nightvision.NightVisionPlugin;
import com.example.nightvision.manager.NightVisionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

public class NightVisionListener implements Listener {

    private final NightVisionPlugin plugin;
    private final NightVisionManager manager;

    public NightVisionListener(NightVisionPlugin plugin, NightVisionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                manager.loadPlayer(player);
            }
        }, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                manager.applyEffect(player);
            }
        }, 3L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                manager.applyEffect(player);
            }
        }, 3L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        manager.unloadPlayer(event.getPlayer());
    }
}
