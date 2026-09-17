package com.example.voidscape.gui;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.guide.BedrockGuideService;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class UpgradeMenuService implements Listener {
    private final VoidscapePlugin plugin;

    public UpgradeMenuService(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Opens the optimal upgrade interface depending on the player's platform.
     * Bedrock players get a native touchscreen SimpleForm, while Java players get a Chest GUI.
     */
    public static void open(VoidscapePlugin plugin, Player player) {
        if (player == null || !player.isOnline()) return;

        if (BedrockGuideService.isBedrock(player)) {
            try {
                if (FloodgateUpgradeForm.open(plugin, player, -1)) {
                    return;
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not open Bedrock upgrade form for " + player.getName() + ": " + t.getMessage());
            }
        }

        // Fallback to chest GUI
        ChestUpgradeGui.open(plugin, player);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onSmithingTableInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getClickedBlock() == null) return;

        Material blockType = event.getClickedBlock().getType();
        if (blockType != Material.SMITHING_TABLE) return;

        Player player = event.getPlayer();

        // On Bedrock, right clicking a smithing table opens the mobile touch upgrade menu!
        if (BedrockGuideService.isBedrock(player)) {
            event.setCancelled(true);
            open(plugin, player);
            return;
        }

        // On Java, sneaking and right-clicking opens the upgrade menu
        if (player.isSneaking()) {
            event.setCancelled(true);
            open(plugin, player);
        }
    }
}
