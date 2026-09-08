package com.example.antifreecam.listener;

import com.example.antifreecam.AntiFreecamPlugin;
import com.example.antifreecam.masker.ContainerMasker;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class FreecamInteractListener implements Listener {

    private final AntiFreecamPlugin plugin;
    private final ContainerMasker masker;

    public FreecamInteractListener(AntiFreecamPlugin plugin, ContainerMasker masker) {
        this.plugin = plugin;
        this.masker = masker;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (!plugin.getConfig().getBoolean("interaction-protection.cancel-occluded-interact", true)) {
            return;
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_BLOCK && action != Action.LEFT_CLICK_BLOCK) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.SPECTATOR || player.hasPermission("antifreecam.bypass")) {
            return;
        }

        Block clicked = event.getClickedBlock();
        if (clicked == null) return;

        Location eye = player.getEyeLocation();
        Location target = clicked.getLocation();

        // ตรวจสอบระยะทางจริงของผู้เล่น (Vanilla Survival Reach ~ 4.5 - 5.0 blocks)
        if (eye.distanceSquared(target.clone().add(0.5, 0.5, 0.5)) > 30.25) { // > 5.5 blocks
            event.setCancelled(true);
            return;
        }

        // ตรวจสอบสิ่งกีดขวางสายตา (Occlusion check)
        if (!masker.hasLineOfSight(eye, target)) {
            // ยกเลิกอย่างเงียบๆ ไม่ส่งเสียงและไม่แจ้งเตือน
            event.setCancelled(true);
            player.sendBlockChange(target, clicked.getBlockData());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!plugin.getConfig().getBoolean("interaction-protection.cancel-occluded-break", true)) {
            return;
        }

        Player player = event.getPlayer();
        if (player.getGameMode() == GameMode.CREATIVE || player.hasPermission("antifreecam.bypass")) {
            return;
        }

        Block block = event.getBlock();
        Location eye = player.getEyeLocation();
        Location target = block.getLocation();

        if (eye.distanceSquared(target.clone().add(0.5, 0.5, 0.5)) > 30.25) {
            event.setCancelled(true);
            return;
        }

        if (!masker.hasLineOfSight(eye, target)) {
            event.setCancelled(true);
            player.sendBlockChange(target, block.getBlockData());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        masker.restorePlayer(event.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        masker.restorePlayer(event.getPlayer());
    }
}
