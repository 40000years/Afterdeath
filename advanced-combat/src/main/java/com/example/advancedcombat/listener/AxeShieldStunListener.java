package com.example.advancedcombat.listener;

import com.example.advancedcombat.AdvancedCombatPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class AxeShieldStunListener implements Listener {

    private final AdvancedCombatPlugin plugin;

    public AxeShieldStunListener(AdvancedCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("shield-stun.enabled", true)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }

        // ตรวจสอบว่าผู้เล่นกำลังยกโล่ป้องกันอยู่หรือไม่
        if (!victim.isBlocking()) {
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!isAxe(weapon.getType())) {
            return;
        }

        int cooldownTicks = plugin.getConfig().getInt("shield-stun.cooldown-ticks", 100);

        // ปิดการใช้งานโล่ (Shield Cooldown) 5 วินาที ตามมาตรฐาน Vanilla Java
        victim.setCooldown(Material.SHIELD, cooldownTicks);
        try {
            victim.clearActiveItem();
        } catch (Throwable ignored) {}

        if (plugin.getConfig().getBoolean("shield-stun.play-sound", true)) {
            victim.getWorld().playSound(victim.getLocation(), Sound.ITEM_SHIELD_BREAK, 1.0f, 1.0f);
        }
    }

    private boolean isAxe(Material material) {
        return switch (material) {
            case DIAMOND_AXE, NETHERITE_AXE, IRON_AXE, GOLDEN_AXE, STONE_AXE, WOODEN_AXE -> true;
            default -> false;
        };
    }
}
