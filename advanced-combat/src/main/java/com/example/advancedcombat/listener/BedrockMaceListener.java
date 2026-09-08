package com.example.advancedcombat.listener;

import com.example.advancedcombat.AdvancedCombatPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BedrockMaceListener implements Listener {

    private final AdvancedCombatPlugin plugin;
    private final Map<UUID, Double> startFallY = new ConcurrentHashMap<>();
    private final Map<UUID, Long> recentSmashAttackers = new ConcurrentHashMap<>();

    public BedrockMaceListener(AdvancedCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        UUID uuid = player.getUniqueId();

        // ตรวจสอบเฉพาะเมื่อเปิดใช้งานฟังก์ชัน
        if (!plugin.getConfig().getBoolean("mace.enabled", true)) {
            return;
        }

        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;

        // ถ้าผู้เล่นแตะพื้น, บิน หรืออยู่ในน้ำ/ลาวา/บันได
        if (player.isOnGround() || player.isFlying() || player.isInWater() || player.isClimbing()) {
            startFallY.remove(uuid);
            return;
        }

        // หากกำลังร่วงหล่นลงมา
        if (to.getY() < from.getY()) {
            startFallY.putIfAbsent(uuid, from.getY());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("mace.enabled", true)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        // ตรวจสอบว่าเป็นผู้เล่น Bedrock หรือเปิดใช้กับทุกคน
        boolean applyToAll = plugin.getConfig().getBoolean("mace.apply-to-all", false);
        if (!applyToAll && !plugin.isBedrockPlayer(attacker.getUniqueId())) {
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType() != Material.MACE) {
            return;
        }

        // คำนวณระยะการตกที่แท้จริง
        double vanillaFall = attacker.getFallDistance();
        Double trackedStartY = startFallY.get(attacker.getUniqueId());
        double trackedFall = 0.0;
        if (trackedStartY != null && trackedStartY > attacker.getLocation().getY()) {
            trackedFall = trackedStartY - attacker.getLocation().getY();
        }

        double fallDistance = Math.max(vanillaFall, trackedFall);
        double minFall = plugin.getConfig().getDouble("mace.min-fall-distance", 1.5);

        // ต้องตกลงมามากกว่าระยะขั้นต่ำ (Vanilla = 1.5 บล็อก)
        if (fallDistance < minFall) {
            return;
        }

        // คำนวณโบนัสดาเมจตามสูตรมาตรฐาน Vanilla Java 26.2:
        // - 3 บล็อกแรก: +3 ต่อบล็อก
        // - 5 บล็อกถัดไป (บล็อกที่ 4-8): +2 ต่อบล็อก
        // - บล็อกที่เกิน 8 ขึ้นไป: +1 ต่อบล็อก
        double bonusDamage = 0.0;
        if (fallDistance <= 3.0) {
            bonusDamage = fallDistance * 3.0;
        } else if (fallDistance <= 8.0) {
            bonusDamage = (3.0 * 3.0) + ((fallDistance - 3.0) * 2.0);
        } else {
            bonusDamage = (3.0 * 3.0) + (5.0 * 2.0) + ((fallDistance - 8.0) * 1.0);
        }

        // ตรวจสอบเอนแชนต์ Density (ความหนาแน่น) ตามมาตรฐาน
        int densityLevel = getDensityLevel(weapon);
        if (densityLevel > 0) {
            bonusDamage += (densityLevel * 0.5 * fallDistance);
        }

        // ปรับดาเมจตามมาตรฐาน Vanilla
        event.setDamage(event.getDamage() + bonusDamage);

        // ลบล้าง Fall Damage เมื่อทุบโดนเป้าหมายตามแบบ Vanilla
        if (plugin.getConfig().getBoolean("mace.negate-fall-damage", true)) {
            attacker.setFallDistance(0.0f);
            startFallY.remove(attacker.getUniqueId());
            recentSmashAttackers.put(attacker.getUniqueId(), System.currentTimeMillis() + 1500L);
        }

        // เอฟเฟกต์เสียงและอนุภาคทางการของ Minecraft 1.21
        Entity victim = event.getEntity();
        World world = victim.getWorld();
        Location hitLoc = victim.getLocation().add(0, 0.5, 0);

        if (plugin.getConfig().getBoolean("mace.play-effects", true)) {
            Sound smashSound = (fallDistance >= 5.0) ? Sound.ITEM_MACE_SMASH_GROUND_HEAVY : Sound.ITEM_MACE_SMASH_GROUND;
            world.playSound(hitLoc, smashSound, 1.0f, 1.0f);

            try {
                world.spawnParticle(Particle.GUST_EMITTER_LARGE, hitLoc, 1);
            } catch (Throwable ignored) {
                world.spawnParticle(Particle.EXPLOSION, hitLoc, 1);
            }
        }

        // คลื่นผลักม็อบรอบข้าง (Radial Knockback) 2.5 บล็อก
        double knockbackRadius = plugin.getConfig().getDouble("mace.knockback-radius", 2.5);
        for (Entity nearby : victim.getNearbyEntities(knockbackRadius, knockbackRadius, knockbackRadius)) {
            if (nearby.equals(attacker) || nearby.equals(victim)) continue;
            if (!(nearby instanceof LivingEntity livingNearby)) continue;

            Vector diff = livingNearby.getLocation().toVector().subtract(victim.getLocation().toVector());
            if (diff.lengthSquared() < 0.001) {
                diff = new Vector(0.5, 0, 0.5);
            }
            diff.normalize().multiply(0.65).setY(0.38);
            livingNearby.setVelocity(diff);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) {
            return;
        }

        if (!(event.getEntity() instanceof Player player)) {
            return;
        }

        Long expireAt = recentSmashAttackers.get(player.getUniqueId());
        if (expireAt != null) {
            if (System.currentTimeMillis() <= expireAt) {
                event.setCancelled(true);
            }
            recentSmashAttackers.remove(player.getUniqueId());
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        startFallY.remove(uuid);
        recentSmashAttackers.remove(uuid);
    }

    private int getDensityLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        try {
            Enchantment density = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("density"));
            if (density != null) {
                return item.getEnchantmentLevel(density);
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}
