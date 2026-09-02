package com.example.voidscape.task;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Phantom;
import org.bukkit.entity.Player;
import org.bukkit.entity.WitherSkeleton;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * VoidStatusTask: ควบคุมสภาพแวดล้อมความมืดและมอนสเตอร์ใน The Void
 * 1. ควบคุมระดับความมืด (Blindness + Darkness)
 * 2. แรงกดดันก้นบึ้งมิติมืด (Abyssal Pressure) เมื่อดิ่งลึกเกิน Y = -600
 * 3. เสกมอนสเตอร์สยองขวัญ:
 *    - Void Wraith (วิญญาณเวหาทมิฬ โฉบจากความมืด)
 *    - Shadow Stalker (เงาไร้ตัวตน ตามเสียงหัวใจเต้น)
 */
public class VoidStatusTask extends BukkitRunnable {

    private final VoidscapePlugin plugin;
    private final Map<UUID, Integer> playerSecondsInVoid = new HashMap<>();

    public VoidStatusTask(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        World voidWorld = plugin.getVoidWorld();
        if (voidWorld == null) return;

        for (Player player : voidWorld.getPlayers()) {
            if (!player.isOnline() || player.isDead()) continue;

            // มืดสนิท 100%: ใช้ Blindness ตัดระยะการมองเห็นเหลือ 5 บล็อกรอบตัว + Darkness เพิ่มหมอกควันดำทมิฬ
            boolean hasVoidArmor = false;
            for (ItemStack piece : player.getInventory().getArmorContents()) {
                if (piece != null && plugin.getItemManager().isVoidItem(piece, "VOID_ARMOR")) {
                    hasVoidArmor = true;
                    break;
                }
            }

            if (!hasVoidArmor) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 140, 0, false, false, false));
                player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 140, 0, false, false, false));
            } else {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.DARKNESS);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 140, 0, false, false, false));
            }

            UUID uuid = player.getUniqueId();
            int secs = playerSecondsInVoid.getOrDefault(uuid, 0) + 10;
            playerSecondsInVoid.put(uuid, secs);

            double currentY = player.getLocation().getY();

            // 1. ระบบดิ่งลงก้นบึ้ง (Abyssal Pressure)
            if (currentY < -100.0) {
                handleAbyssalDepth(player, currentY);
            }

            // 2. ระบบ Voidic Infusion (ทุกๆ 60 วินาที ลดเลือดสูงสุด 1 ดวง)
            if (secs % 60 == 0) {
                applyInfusion(player);
            }

            // 3. ระบบ Paranoia และเสกมอนสเตอร์สยองขวัญ
            handleParanoiaAndMonsters(player);
        }
    }

    /**
     * ดิ่งลงก้นบึ้งมิติมืด: ยิ่งดิ่งลึก ยิ่งหลอน และเริ่มโดนแรงกดดันกลืนกินที่ Y < -600
     */
    private void handleAbyssalDepth(Player player, double y) {
        Location loc = player.getLocation();

        if (y >= -300.0) {
            player.sendActionBar(Component.text(String.format("🕳️ คุณกำลังร่วงหล่นลงสู่ความว่างเปล่าไร้ก้นบึ้ง... (Y: %.0f)", y), NamedTextColor.GRAY));
            if (ThreadLocalRandom.current().nextDouble() < 0.3) {
                player.playSound(loc, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0f, 0.5f);
            }
        } else if (y >= -600.0) {
            player.sendActionBar(Component.text(String.format("⚠️ จิตใจเริ่มถูกบีบคั้นในความมืดมิด... (Y: %.0f)", y), NamedTextColor.DARK_PURPLE));
            player.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.8f);
            player.getWorld().spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 1, 0), 12, 0.4, 0.4, 0.4, 0.05);
        } else {
            // Y < -600: แรงกดดันก้นบึ้งเริ่มฉีกร่างผู้เล่น!
            player.damage(4.0); // ดาเมจ 2 หัวใจทุกรอบ
            player.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.8f, 1.5f);
            player.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 1);
            player.sendActionBar(Component.text(String.format("💀 มัจจุราชแห่งก้นบึ้งกำลังกลืนกินคุณ! (Y: %.0f) [กิน Null Fruit ด่วน!]", y), NamedTextColor.RED));
        }
    }

    private void applyInfusion(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        double currentMax = attr.getBaseValue();
        double minHp = 4.0; // ต่ำสุด 2 หัวใจ

        if (currentMax > minHp) {
            double newMax = Math.max(minHp, currentMax - 2.0);
            attr.setBaseValue(newMax);

            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 0.5f);
            player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 10, 0.2, 0.2, 0.2, 0.05);

            player.sendActionBar(Component.text(
                String.format("✦ พลังความมืดกัดกร่อนดวงใจของคุณ... (เหลือสูงสุด %.0f หัวใจ)", newMax / 2.0),
                NamedTextColor.RED
            ));
        }
    }

    private void handleParanoiaAndMonsters(Player player) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // 40% สุ่มเกิดเหตุการณ์หลอน
        if (rnd.nextDouble() < 0.40) {
            Location loc = player.getLocation();

            // สุ่มเสียงหลอน
            Sound[] spookySounds = {
                Sound.ENTITY_WARDEN_HEARTBEAT,
                Sound.AMBIENT_CAVE,
                Sound.ENTITY_ELDER_GUARDIAN_CURSE,
                Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
                Sound.ENTITY_PHANTOM_SWOOP
            };
            Sound sound = spookySounds[rnd.nextInt(spookySounds.length)];
            player.playSound(loc, sound, 0.9f, 0.7f);

            // 30% สุ่มเสกมอนสเตอร์
            if (rnd.nextDouble() < 0.30) {
                if (rnd.nextBoolean()) {
                    spawnVoidWraith(player);
                } else {
                    spawnShadowStalker(player);
                }
            }
        }
    }

    /**
     * เสก Void Wraith (วิญญาณเวหาทมิฬ) - บินโฉบจากความมืด ดรอป Void Crystal และ Null Fruit
     */
    private void spawnVoidWraith(Player player) {
        try {
            Location pLoc = player.getLocation();
            ThreadLocalRandom rnd = ThreadLocalRandom.current();

            double angle = rnd.nextDouble() * 2 * Math.PI;
            double dist = 10 + rnd.nextDouble() * 6;
            Location spawnLoc = pLoc.clone().add(Math.cos(angle) * dist, 6 + rnd.nextDouble() * 4, Math.sin(angle) * dist);

            World world = player.getWorld();
            Phantom wraith = (Phantom) world.spawnEntity(spawnLoc, EntityType.PHANTOM);
            wraith.customName(Component.text("✦ วิญญาณเวหาทมิฬ (Void Wraith)", NamedTextColor.DARK_PURPLE));
            wraith.setCustomNameVisible(true);
            wraith.setSize(2);
            wraith.setTarget(player);

            // ติดบัฟล่องหนรางๆ + พลังโจมตี
            wraith.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 99999, 0, false, false));
            wraith.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 99999, 0, false, false));

            wraith.getPersistentDataContainer().set(plugin.getKeyShadowStalker(), PersistentDataType.BYTE, (byte) 1);

            world.spawnParticle(Particle.PORTAL, spawnLoc, 30, 0.5, 0.5, 0.5);
            world.spawnParticle(Particle.LARGE_SMOKE, spawnLoc, 15, 0.3, 0.3, 0.3);
            player.playSound(pLoc, Sound.ENTITY_PHANTOM_SWOOP, 1.2f, 0.5f);
            player.sendMessage(Component.text("🦇 บางสิ่งที่มีปีกกำลังแหวกความมืดมิดลงมาหาคุณ!", NamedTextColor.RED));
        } catch (Exception e) {
            plugin.getLogger().warning("เกิดข้อผิดพลาดขณะเสก Void Wraith: " + e.getMessage());
        }
    }

    /**
     * เสก Shadow Stalker (ผู้แฝงกายในเงามืด) - ร่างเงาเดินดิน ทรงพลัง
     */
    private void spawnShadowStalker(Player player) {
        try {
            Location pLoc = player.getLocation();
            ThreadLocalRandom rnd = ThreadLocalRandom.current();

            double angle = rnd.nextDouble() * 2 * Math.PI;
            double dist = 7 + rnd.nextDouble() * 4;
            Location spawnLoc = pLoc.clone().add(Math.cos(angle) * dist, 0, Math.sin(angle) * dist);
            spawnLoc.setY(pLoc.getY());

            World world = player.getWorld();
            WitherSkeleton stalker = (WitherSkeleton) world.spawnEntity(spawnLoc, EntityType.WITHER_SKELETON);
            stalker.customName(Component.text("✦ เงาแห่งความมืด (Shadow Stalker)", NamedTextColor.DARK_RED));
            stalker.setCustomNameVisible(true);
            stalker.setTarget(player);

            stalker.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 99999, 1, false, false));
            stalker.getPersistentDataContainer().set(plugin.getKeyShadowStalker(), PersistentDataType.BYTE, (byte) 1);

            world.spawnParticle(Particle.LARGE_SMOKE, spawnLoc.clone().add(0, 1, 0), 20, 0.3, 0.5, 0.3);
            player.playSound(pLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.2f, 0.6f);
            player.sendMessage(Component.text("⚠️ คุณรู้สึกได้ถึงสายตาอันมืดมิดกำลังจ้องมองคุณจากข้างหลัง...", NamedTextColor.DARK_RED));
        } catch (Exception e) {
            plugin.getLogger().warning("เกิดข้อผิดพลาดขณะเสก Shadow Stalker: " + e.getMessage());
        }
    }

    public void cleanupPlayer(UUID uuid) {
        playerSecondsInVoid.remove(uuid);
    }
}
