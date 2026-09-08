package com.example.advancedcombat.listener;

import com.example.advancedcombat.AdvancedCombatPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class BedrockSweepListener implements Listener {

    private final AdvancedCombatPlugin plugin;
    private final Map<UUID, Long> lastAttackTime = new ConcurrentHashMap<>();

    public BedrockSweepListener(AdvancedCombatPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityDamage(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("sweep.enabled", true)) {
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) {
            return;
        }

        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }

        boolean bedrockOnly = plugin.getConfig().getBoolean("sweep.bedrock-only", true);
        if (bedrockOnly && !plugin.isBedrockPlayer(attacker.getUniqueId())) {
            // ปล่อยให้ Java จัดการระบบฟันกวาดตามธรรมชาติ
            return;
        }

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (!isSword(weapon.getType())) {
            return;
        }

        // ใน Vanilla Java: จะฟันกวาดเมื่อไม่ได้สปรินต์ และอยู่บนพื้น
        if (attacker.isSprinting() || !attacker.isOnGround()) {
            return;
        }

        // ป้องกันสแปมคลิก (Cooldown threshold 600ms)
        long now = System.currentTimeMillis();
        Long lastTime = lastAttackTime.get(attacker.getUniqueId());
        if (lastTime != null && (now - lastTime) < 600L) {
            lastAttackTime.put(attacker.getUniqueId(), now);
            return;
        }
        lastAttackTime.put(attacker.getUniqueId(), now);

        // ดาเมจฟันกวาดมาตรฐาน (1.0 = 0.5 หัวใจ)
        double sweepDamage = plugin.getConfig().getDouble("sweep.damage", 1.0);
        int sweepingLevel = getSweepingLevel(weapon);
        if (sweepingLevel > 0) {
            // สูตรวานิลลา: 1.0 + (level * (level / (level + 1.0)))
            sweepDamage += (sweepingLevel / (sweepingLevel + 1.0));
        }

        double radius = plugin.getConfig().getDouble("sweep.radius", 1.5);
        Location victimLoc = victim.getLocation();

        // เล่นเอฟเฟกต์และเสียงฟันกวาดทางการของ Java
        victim.getWorld().spawnParticle(Particle.SWEEP_ATTACK, victimLoc.add(0, 1.0, 0), 1);
        victim.getWorld().playSound(victimLoc, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 1.0f);

        for (Entity nearby : victim.getNearbyEntities(radius, 1.0, radius)) {
            if (nearby.equals(attacker) || nearby.equals(victim)) continue;
            if (!(nearby instanceof LivingEntity target)) continue;
            if (target instanceof ArmorStand) continue;

            // ตีศัตรูรอบข้างด้วยดาเมจฟันกวาด
            target.damage(sweepDamage, attacker);

            // แรงผลักเล็กน้อยตามมาตรฐาน Java Sweep
            Vector push = target.getLocation().toVector().subtract(attacker.getLocation().toVector());
            if (push.lengthSquared() > 0.001) {
                push.normalize().multiply(0.25).setY(0.1);
                target.setVelocity(push);
            }
        }
    }

    private boolean isSword(Material material) {
        return switch (material) {
            case DIAMOND_SWORD, NETHERITE_SWORD, IRON_SWORD, GOLDEN_SWORD, STONE_SWORD, WOODEN_SWORD -> true;
            default -> false;
        };
    }

    private int getSweepingLevel(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        try {
            Enchantment sweeping = Registry.ENCHANTMENT.get(NamespacedKey.minecraft("sweeping_edge"));
            if (sweeping != null) {
                return item.getEnchantmentLevel(sweeping);
            }
        } catch (Throwable ignored) {}
        return 0;
    }
}
