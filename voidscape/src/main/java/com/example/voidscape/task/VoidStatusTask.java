package com.example.voidscape.task;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.mob.VoidMobManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * VoidStatusTask: ควบคุมสภาพแวดล้อมความมืดและมอนสเตอร์ยักษ์ใน The Void
 * 1. ควบคุมระดับความมืดแบบไร้อาการแลค (Flicker-free Blindness + Darkness)
 * 2. แรงกดดันก้นบึ้งมิติมืด (Abyssal Pressure) เมื่อดิ่งลึกเกิน Y = -600
 * 3. ระบบ Voidic Infusion ลดเลือดสูงสุดทุกๆ 60 วินาที
 * 4. ระบบเสกมอนสเตอร์ยักษ์อัตโนมัติ (Giant Phantom / Giant Vex) สม่ำเสมอ ไม่ว่างเปล่า
 */
public class VoidStatusTask extends BukkitRunnable {

    private final VoidscapePlugin plugin;
    private final VoidMobManager mobManager;
    private final Map<UUID, Integer> playerSecondsInVoid = new HashMap<>();
    private final Map<UUID, Long> lastInfusionTimes = new HashMap<>();
    private long lastVoidThunderTime = 0L;

    public VoidStatusTask(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.mobManager = plugin.getMobManager();
    }

    @Override
    public void run() {
        World voidWorld = plugin.getVoidWorld();
        if (voidWorld == null) return;

        // ดับฝน/พายุทันทีหากตรวจพบในโลก The Void
        if (plugin.getConfig().getBoolean("performance.disable-storm", true) && voidWorld.hasStorm()) {
            voidWorld.setStorm(false);
            voidWorld.setThundering(false);
            voidWorld.setWeatherDuration(0);
        }

        boolean enableDarkness = plugin.getConfig().getBoolean("performance.enable-darkness", true);
        boolean enableBlindness = plugin.getConfig().getBoolean("performance.enable-blindness", true);
        double spawnChance = plugin.getConfig().getDouble("spawning.chance", 0.80);
        int maxMobsPerPlayer = plugin.getConfig().getInt("spawning.max-mobs-per-player", 8);
        int batchSize = plugin.getConfig().getInt("spawning.spawn-batch-size", 2);
        long infusionIntervalMs = plugin.getConfig().getLong("infusion.interval-seconds", 60L) * 1000L;

        long now = System.currentTimeMillis();

        // บรรยากาศฟ้าร้องมิติมืด Void Thunder (เสียงคำรามก้องกังวานทุ้มต่ำลึก ทุกๆ 35-50 วินาที)
        if (now - lastVoidThunderTime >= 38000L) {
            lastVoidThunderTime = now;
            for (Player p : voidWorld.getPlayers()) {
                p.playSound(p.getLocation(), Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2.5f, 0.45f);
                p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_AMBIENT, 1.2f, 0.5f);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 3, 0), 20, 2.0, 1.0, 2.0, 0.1);
            }
        }

        for (Player player : voidWorld.getPlayers()) {
            if (!player.isOnline() || player.isDead()) continue;

            UUID uuid = player.getUniqueId();

            // 1. ระบบความมืดแบบ Flicker-Free (ให้ Effect นาน 160 ticks ซ้อนทับการรันทุก 40 ticks ป้องกัน Shader รีเซ็ตกระตุก)
            boolean hasVoidArmor = false;
            for (ItemStack piece : player.getInventory().getArmorContents()) {
                if (piece != null && plugin.getItemManager().isVoidItem(piece, "VOID_ARMOR")) {
                    hasVoidArmor = true;
                    break;
                }
            }

            boolean hasVoidSight = plugin.getItemManager().hasVoidSight(player);

            if (!hasVoidArmor && !hasVoidSight) {
                if (enableBlindness) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 160, 0, false, false, false));
                } else {
                    player.removePotionEffect(PotionEffectType.BLINDNESS);
                }
                if (enableDarkness) {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 160, 0, false, false, false));
                } else {
                    player.removePotionEffect(PotionEffectType.DARKNESS);
                }
            } else {
                player.removePotionEffect(PotionEffectType.BLINDNESS);
                player.removePotionEffect(PotionEffectType.DARKNESS);
                player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 160, 0, false, false, false));

                // แจ้งเตือนเมื่อเนตรแห่งความมืดใกล้หมด
                if (hasVoidSight) {
                    long remaining = plugin.getItemManager().getVoidSightRemainingSeconds(player);
                    if (remaining > 0 && remaining <= 5) {
                        player.sendActionBar(Component.text("⚠️ เนตรแห่งความมืด (Void Sight) กำลังจะหมดใน " + remaining + " วินาที!", NamedTextColor.YELLOW));
                    }
                }
            }

            // นับเวลาที่อยู่ใน Void
            int secs = playerSecondsInVoid.getOrDefault(uuid, 0) + 2;
            playerSecondsInVoid.put(uuid, secs);

            double currentY = player.getLocation().getY();

            // 2. ระบบดิ่งลงก้นบึ้ง (Abyssal Pressure)
            if (currentY < -100.0) {
                handleAbyssalDepth(player, currentY);
            }

            // 3. ระบบ Voidic Infusion (ลดเลือดสูงสุดตามเวลาที่กำหนด)
            long lastInfusion = lastInfusionTimes.getOrDefault(uuid, now);
            if (lastInfusionTimes.containsKey(uuid) && (now - lastInfusion >= infusionIntervalMs)) {
                applyInfusion(player);
                lastInfusionTimes.put(uuid, now);
            } else if (!lastInfusionTimes.containsKey(uuid)) {
                lastInfusionTimes.put(uuid, now);
            }

            // 4. ระบบเสกมอนสเตอร์ยักษ์สม่ำเสมอ (Dense & Thrilling Void Spawning)
            handleMonsterSpawning(player, spawnChance, maxMobsPerPlayer, batchSize);
        }
    }

    /**
     * เสกมอนสเตอร์ยักษ์รอบตัวผู้เล่นตามจำนวนและโอกาสที่กำหนด
     */
    private void handleMonsterSpawning(Player player, double spawnChance, int maxMobs, int batchSize) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();

        // เล่นเสียงหลอนในบรรยากาศเป็นครั้งคราว (โอกาส 25%)
        if (rnd.nextDouble() < 0.25) {
            Sound[] spookySounds = {
                Sound.ENTITY_WARDEN_HEARTBEAT,
                Sound.AMBIENT_CAVE,
                Sound.BLOCK_SCULK_SHRIEKER_SHRIEK,
                Sound.ENTITY_PHANTOM_SWOOP,
                Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD
            };
            Sound sound = spookySounds[rnd.nextInt(spookySounds.length)];
            player.playSound(player.getLocation(), sound, 0.8f, 0.7f);
        }

        // ตรวจสอบโอกาสสปอว์นมอนสเตอร์
        if (rnd.nextDouble() < spawnChance) {
            int currentMobs = mobManager.getNearbyVoidMonsterCount(player, 45.0);
            if (currentMobs < maxMobs) {
                int toSpawn = Math.min(batchSize, maxMobs - currentMobs);
                for (int i = 0; i < toSpawn; i++) {
                    mobManager.spawnRandomVoidMonster(player);
                }
            }
        }
    }

    /**
     * ดิ่งลงก้นบึ้งมิติมืด: ยิ่งดิ่งลึก ยิ่งหลอน และเริ่มโดนแรงกดดันกลืนกินที่ Y < -600
     */
    private void handleAbyssalDepth(Player player, double y) {
        Location loc = player.getLocation();

        if (y >= -300.0) {
            player.sendActionBar(Component.text(String.format("🕳️ คุณกำลังร่วงหล่นลงสู่ความว่างเปล่าไร้ก้นบึ้ง... (Y: %.0f)", y), NamedTextColor.GRAY));
            if (ThreadLocalRandom.current().nextDouble() < 0.2) {
                player.playSound(loc, Sound.AMBIENT_SOUL_SAND_VALLEY_MOOD, 1.0f, 0.5f);
            }
        } else if (y >= -600.0) {
            player.sendActionBar(Component.text(String.format("⚠️ จิตใจเริ่มถูกบีบคั้นในความมืดมิด... (Y: %.0f)", y), NamedTextColor.DARK_PURPLE));
            player.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.1f, 0.8f);
            player.getWorld().spawnParticle(Particle.SQUID_INK, loc.clone().add(0, 1, 0), 6, 0.3, 0.3, 0.3, 0.02);
        } else {
            // Y < -600: แรงกดดันก้นบึ้งเริ่มฉีกร่างผู้เล่น
            player.damage(4.0); // ดาเมจ 2 หัวใจ
            player.playSound(loc, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.5f);
            player.getWorld().spawnParticle(Particle.SONIC_BOOM, loc, 1);
            player.sendActionBar(Component.text(String.format("💀 มัจจุราชแห่งก้นบึ้งกำลังกลืนกินคุณ! (Y: %.0f) [กิน Null Fruit ด่วน!]", y), NamedTextColor.RED));
        }
    }

    private void applyInfusion(Player player) {
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr == null) return;

        double currentMax = attr.getBaseValue();
        double minHp = plugin.getConfig().getDouble("infusion.min-health", 4.0);
        double reduction = plugin.getConfig().getDouble("infusion.reduction-amount", 2.0);

        if (currentMax > minHp) {
            double newMax = Math.max(minHp, currentMax - reduction);
            attr.setBaseValue(newMax);

            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_HURT, 0.8f, 0.5f);
            player.getWorld().spawnParticle(Particle.SOUL, player.getLocation().add(0, 1, 0), 8, 0.2, 0.2, 0.2, 0.03);

            player.sendActionBar(Component.text(
                String.format("✦ พลังความมืดกัดกร่อนดวงใจของคุณ... (เหลือสูงสุด %.0f หัวใจ)", newMax / 2.0),
                NamedTextColor.RED
            ));
        }
    }

    public void cleanupPlayer(UUID uuid) {
        playerSecondsInVoid.remove(uuid);
        lastInfusionTimes.remove(uuid);
    }
}
