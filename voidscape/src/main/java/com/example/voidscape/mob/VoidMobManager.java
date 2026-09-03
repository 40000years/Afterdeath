package com.example.voidscape.mob;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vex;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.concurrent.ThreadLocalRandom;

/**
 * VoidMobManager: จัดการการเสกและควบคุมมอนสเตอร์ยักษ์ใน The Void
 * - Giant Phantom (พญาเงาเวหาทมิฬ) ขยายตัวใหญ่ยักษ์ HP 400 (20 เท่า)
 * - Giant Vex (ภูตทมิฬกลืนวิญญาณ) ขยายตัวใหญ่ยักษ์ HP 280 (20 เท่า)
 * - Shadow Stalker (เงาแห่งความมืด) HP 200 (10 เท่า)
 */
public class VoidMobManager {

    private final VoidscapePlugin plugin;

    public VoidMobManager(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * นับจำนวนมอนสเตอร์ Void ที่ยังคงมีชีวิตอยู่รอบตัวผู้เล่นในรัศมีที่กำหนด
     */
    public int getNearbyVoidMonsterCount(Player player, double radius) {
        int count = 0;
        for (Entity entity : player.getNearbyEntities(radius, radius, radius)) {
            if (entity instanceof LivingEntity living && !living.isDead()) {
                if (living.getPersistentDataContainer().has(plugin.getKeyShadowStalker(), PersistentDataType.BYTE)) {
                    count++;
                }
            }
        }
        return count;
    }

    /**
     * สุ่มเสกมอนสเตอร์ Void รอบตัวผู้เล่นตามสัดส่วน Weight ใน config
     */
    public LivingEntity spawnRandomVoidMonster(Player player) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        int phantomWeight = plugin.getConfig().getInt("spawning.giant-phantom.weight", 50);
        int vexWeight = plugin.getConfig().getInt("spawning.giant-vex.weight", 50);
        int stalkerWeight = plugin.getConfig().getInt("spawning.shadow-stalker.weight", 25);
        int totalWeight = phantomWeight + vexWeight + stalkerWeight;

        if (totalWeight <= 0) return null;

        int roll = rnd.nextInt(totalWeight);
        if (roll < phantomWeight) {
            return spawnGiantPhantom(player);
        } else if (roll < phantomWeight + vexWeight) {
            return spawnGiantVex(player);
        } else {
            return spawnShadowStalker(player);
        }
    }

    /**
     * เสก Giant Phantom (พญาเงาเวหาทมิฬ) - บินโฉบจากความมืด ตัวใหญ่โต เลือด 20 เท่า
     */
    public Phantom spawnGiantPhantom(Player player) {
        Location pLoc = player.getLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        double angle = rnd.nextDouble() * 2 * Math.PI;
        double dist = 14 + rnd.nextDouble() * 10;
        Location spawnLoc = pLoc.clone().add(Math.cos(angle) * dist, 10 + rnd.nextDouble() * 8, Math.sin(angle) * dist);

        return spawnGiantPhantomAt(spawnLoc, player);
    }

    public Phantom spawnGiantPhantomAt(Location loc, Player target) {
        try {
            World world = loc.getWorld();
            if (world == null) return null;

            Phantom phantom = (Phantom) world.spawnEntity(loc, EntityType.PHANTOM);

            int size = plugin.getConfig().getInt("spawning.giant-phantom.size", 6);
            double scale = plugin.getConfig().getDouble("spawning.giant-phantom.scale", 2.2);
            double maxHealth = plugin.getConfig().getDouble("spawning.giant-phantom.health", 400.0);
            double attackDamage = plugin.getConfig().getDouble("spawning.giant-phantom.attack-damage", 14.0);

            // ขยายขนาดปีกและเอนจินของ Phantom
            phantom.setSize(size);

            // ขยายสเกล Entity ด้วย Attribute.SCALE
            AttributeInstance scaleAttr = phantom.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
            }

            // ตั้งค่า HP 20 เท่า (400 HP)
            AttributeInstance healthAttr = phantom.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(maxHealth);
                phantom.setHealth(maxHealth);
            }

            // ตั้งค่าพลังโจมตี
            AttributeInstance dmgAttr = phantom.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(attackDamage);
            }

            phantom.customName(Component.text("✦ พญาเงาเวหาทมิฬ (Abyssal Leviathan)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            phantom.setCustomNameVisible(true);
            if (target != null) {
                phantom.setTarget(target);
            }

            // ป้องกัน Phantom เผาไหม้เวลากลางวัน
            phantom.setShouldBurnInDay(false);

            // ติดแท็ก Void Monster เพื่อใช้เช็คตอนดรอปของ
            phantom.getPersistentDataContainer().set(plugin.getKeyShadowStalker(), PersistentDataType.BYTE, (byte) 1);

            // เอฟเฟกต์เปิดตัว (เบา ไม่แลค)
            world.spawnParticle(Particle.PORTAL, loc, 25, 0.6, 0.6, 0.6, 0.05);
            world.playSound(loc, Sound.ENTITY_PHANTOM_SWOOP, 1.8f, 0.4f);

            if (target != null) {
                target.sendMessage(Component.text("🦇 เงาปีกขนาดยักษ์กำลังแหวกความมืดมิดดิ่งลงมาหาคุณ!", NamedTextColor.RED));
            }

            return phantom;
        } catch (Exception e) {
            plugin.getLogger().warning("เกิดข้อผิดพลาดขณะเสก Giant Phantom: " + e.getMessage());
            return null;
        }
    }

    /**
     * เสก Giant Vex (ภูตทมิฬกลืนวิญญาณ) - ปีศาจบินตัวใหญ่ ทรงพลัง เลือด 20 เท่า ถือดาบ
     */
    public Vex spawnGiantVex(Player player) {
        Location pLoc = player.getLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        double angle = rnd.nextDouble() * 2 * Math.PI;
        double dist = 10 + rnd.nextDouble() * 8;
        Location spawnLoc = pLoc.clone().add(Math.cos(angle) * dist, 2 + rnd.nextDouble() * 4, Math.sin(angle) * dist);

        return spawnGiantVexAt(spawnLoc, player);
    }

    public Vex spawnGiantVexAt(Location loc, Player target) {
        try {
            World world = loc.getWorld();
            if (world == null) return null;

            Vex vex = (Vex) world.spawnEntity(loc, EntityType.VEX);

            double scale = plugin.getConfig().getDouble("spawning.giant-vex.scale", 2.8);
            double maxHealth = plugin.getConfig().getDouble("spawning.giant-vex.health", 280.0);
            double attackDamage = plugin.getConfig().getDouble("spawning.giant-vex.attack-damage", 12.0);

            // ขยายสเกล Vex ให้ตัวใหญ่ยักษ์
            AttributeInstance scaleAttr = vex.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
            }

            // เลือด 20 เท่า (280 HP)
            AttributeInstance healthAttr = vex.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(maxHealth);
                vex.setHealth(maxHealth);
            }

            // พลังโจมตี
            AttributeInstance dmgAttr = vex.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(attackDamage);
            }

            // ปิดระบบการสลายตัว (ไม่หมดอายุตายเอง)
            vex.setLimitedLifetime(false);

            // ใส่อาวุธดาบเนเธอไรต์
            if (vex.getEquipment() != null) {
                vex.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                vex.getEquipment().setItemInMainHandDropChance(0.03f);
            }

            vex.customName(Component.text("✦ ภูตทมิฬกลืนวิญญาณ (Abyssal Shade)", NamedTextColor.RED, TextDecoration.BOLD));
            vex.setCustomNameVisible(true);
            if (target != null) {
                vex.setTarget(target);
            }

            vex.getPersistentDataContainer().set(plugin.getKeyShadowStalker(), PersistentDataType.BYTE, (byte) 1);

            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 15, 0.4, 0.4, 0.4, 0.02);
            world.playSound(loc, Sound.ENTITY_VEX_CHARGE, 1.4f, 0.5f);

            if (target != null) {
                target.sendMessage(Component.text("⚔️ ภูตทมิฬขนาดยักษ์ปรากฏกายขึ้นพร้อมคมดาบกลืนวิญญาณ!", NamedTextColor.DARK_RED));
            }

            return vex;
        } catch (Exception e) {
            plugin.getLogger().warning("เกิดข้อผิดพลาดขณะเสก Giant Vex: " + e.getMessage());
            return null;
        }
    }

    /**
     * เสก Shadow Stalker (เงาแห่งความมืด) - Wither Skeleton ขยายร่าง
     */
    public WitherSkeleton spawnShadowStalker(Player player) {
        Location pLoc = player.getLocation();
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        double angle = rnd.nextDouble() * 2 * Math.PI;
        double dist = 8 + rnd.nextDouble() * 6;
        Location spawnLoc = pLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
        spawnLoc.setY(pLoc.getY());

        return spawnShadowStalkerAt(spawnLoc, player);
    }

    public WitherSkeleton spawnShadowStalkerAt(Location loc, Player target) {
        try {
            World world = loc.getWorld();
            if (world == null) return null;

            WitherSkeleton stalker = (WitherSkeleton) world.spawnEntity(loc, EntityType.WITHER_SKELETON);

            double scale = plugin.getConfig().getDouble("spawning.shadow-stalker.scale", 1.5);
            double maxHealth = plugin.getConfig().getDouble("spawning.shadow-stalker.health", 200.0);
            double attackDamage = plugin.getConfig().getDouble("spawning.shadow-stalker.attack-damage", 14.0);

            AttributeInstance scaleAttr = stalker.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
            }

            AttributeInstance healthAttr = stalker.getAttribute(Attribute.MAX_HEALTH);
            if (healthAttr != null) {
                healthAttr.setBaseValue(maxHealth);
                stalker.setHealth(maxHealth);
            }

            AttributeInstance dmgAttr = stalker.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(attackDamage);
            }

            stalker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));
            stalker.customName(Component.text("✦ เงาแห่งความมืด (Shadow Stalker)", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            stalker.setCustomNameVisible(true);
            if (target != null) {
                stalker.setTarget(target);
            }

            stalker.getPersistentDataContainer().set(plugin.getKeyShadowStalker(), PersistentDataType.BYTE, (byte) 1);

            world.spawnParticle(Particle.SMOKE, loc.clone().add(0, 1, 0), 15, 0.3, 0.4, 0.3, 0.02);
            world.playSound(loc, Sound.ENTITY_WITHER_SKELETON_AMBIENT, 1.2f, 0.6f);

            if (target != null) {
                target.sendMessage(Component.text("⚠️ คุณรู้สึกได้ถึงสายตาอันมืดมิดกำลังจ้องมองคุณ...", NamedTextColor.DARK_RED));
            }

            return stalker;
        } catch (Exception e) {
            plugin.getLogger().warning("เกิดข้อผิดพลาดขณะเสก Shadow Stalker: " + e.getMessage());
            return null;
        }
    }
}
