package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.generator.VoidChunkGenerator;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * VoidDiveListener: ควบคุมระบบดำดิ่งข้ามชั้นแบบไร้รอยต่อ (Seamless Vertical Dive)
 * 1. Layer 1 (X: 0) -> ทิ้งตัวลง Abyssal Vortex (Y < -48) -> ทะลุสู่ Layer 2 (X: 4000)
 * 2. Layer 2 (X: 4000) -> ทิ้งตัวลง Null Chasm (Y < -48) -> ทะลุสู่ Layer 3 (X: 8000)
 * 3. Layer 3 (X: 8000): ชั้นล่างสุด ลานประลอง Abyssal Warden Boss
 * 4. Ascension Rift: เดินเข้าเสาแสงแห่งชัยชนะที่ Layer 3 เพื่อวาร์ปกลับสู่ Overworld
 */
public class VoidDiveListener implements Listener {

    private final VoidscapePlugin plugin;
    private final Map<UUID, Long> diveCooldowns = new HashMap<>();

    public VoidDiveListener(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(plugin.getVoidWorldName())) return;

        Location loc = player.getLocation();
        double x = loc.getX();
        double y = loc.getY();
        double z = loc.getZ();

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        if (now - diveCooldowns.getOrDefault(uuid, 0L) < 2000L) {
            return; // ป้องกันการทริกเกอร์ซ้ำซ้อนใน 2 วินาที
        }

        // =========================================================================
        // 1. ดำดิ่งจาก Layer 1 (X: 0) สู่ Layer 2 (X: 4000)
        // =========================================================================
        if (Math.abs(x) < 40.0 && Math.abs(z) < 40.0 && y < -48.0) {
            diveCooldowns.put(uuid, now);

            // คำนวณความเร่งการตกเพื่อคงความต่อเนื่องของการดิ่ง
            Vector vel = player.getVelocity();
            double downwardVel = Math.min(-1.0, vel.getY());

            double relX = Math.max(-15.0, Math.min(15.0, x));
            double relZ = Math.max(-15.0, Math.min(15.0, z));
            Location targetLoc = new Location(player.getWorld(), VoidChunkGenerator.SECTOR_2_CENTER_X + relX, 155.0, relZ, loc.getYaw(), loc.getPitch());

            // เอฟเฟกต์ภาพและเสียง: มืดวูบชั่วขณะ พร้อมเสียงลมกระโชกคำราม
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 0, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 25, 0, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 160, 0, false, false, true));

            player.setFallDistance(0.0f);
            player.teleport(targetLoc);
            player.setFallDistance(0.0f);
            player.setVelocity(new Vector(vel.getX() * 0.5, downwardVel, vel.getZ() * 0.5));
            if (plugin.getEjectionListener() != null) {
                plugin.getEjectionListener().grantFallImmunity(uuid, 10000L);
            }

            player.playSound(targetLoc, Sound.ITEM_ELYTRA_FLYING, 2.0f, 0.7f);
            player.playSound(targetLoc, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.5f);
            player.getWorld().spawnParticle(Particle.PORTAL, targetLoc, 40, 1.0, 1.0, 1.0, 0.2);

            Title title = Title.title(
                Component.text("✦ THE NULL CATACOMBS ✦", NamedTextColor.DARK_AQUA, TextDecoration.BOLD),
                Component.text("ชั้นที่ 2: สุสานเศษซากมิติไร้รูป", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(2200), Duration.ofMillis(800))
            );
            player.showTitle(title);
            player.sendActionBar(Component.text("🌌 คุณกำลังดำดิ่งลึกผ่านชั้นบรรยากาศมิติทมิฬสู่ชั้นที่ 2...", NamedTextColor.AQUA));
            return;
        }

        // =========================================================================
        // 2. ดำดิ่งจาก Layer 2 (X: 4000) สู่ Layer 3 (X: 8000)
        // =========================================================================
        if (Math.abs(x - VoidChunkGenerator.SECTOR_2_CENTER_X) < 40.0 && Math.abs(z) < 40.0 && y < -48.0) {
            diveCooldowns.put(uuid, now);

            Vector vel = player.getVelocity();
            double relX = Math.max(-15.0, Math.min(15.0, x - VoidChunkGenerator.SECTOR_2_CENTER_X));
            double relZ = Math.max(-15.0, Math.min(15.0, z));
            Location targetLoc = new Location(player.getWorld(), VoidChunkGenerator.SECTOR_3_CENTER_X + relX, 110.0, relZ, loc.getYaw(), loc.getPitch());

            // เอฟเฟกต์ภาพและเสียง: เสียงคำรามของราชันย์ก้นบึ้ง
            player.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 50, 0, false, false, false));
            player.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 400, 0, false, false, true)); // ร่อนลงสู่ลานประลอง 20 วิ

            player.setFallDistance(0.0f);
            player.teleport(targetLoc);
            player.setFallDistance(0.0f);
            player.setVelocity(new Vector(vel.getX() * 0.3, -0.8, vel.getZ() * 0.3));
            if (plugin.getEjectionListener() != null) {
                plugin.getEjectionListener().grantFallImmunity(uuid, 15000L);
            }

            player.playSound(targetLoc, Sound.ENTITY_WARDEN_ROAR, 2.0f, 0.6f);
            player.playSound(targetLoc, Sound.ENTITY_WARDEN_HEARTBEAT, 2.0f, 1.0f);
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, targetLoc, 50, 1.5, 1.5, 1.5, 0.05);

            Title title = Title.title(
                Component.text("💀 THE ABYSSAL FOUNDATION 💀", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("ชั้นที่ 3: ก้นบึ้งรังราชันย์ไททัน", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))
            );
            player.showTitle(title);
            player.sendMessage(Component.text("💀 คุณได้มาถึงก้นบึ้งชั้นสุดท้ายของ The Void แล้ว! จงเตรียมพร้อมเผชิญหน้ากับราชันย์แห่งมิติมืด!", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            return;
        }

        // =========================================================================
        // 3. ป้องกันตก Void ที่ลานประลอง Layer 3 (ดึงกลับสู่ลานประลอง)
        // =========================================================================
        if (Math.abs(x - VoidChunkGenerator.SECTOR_3_CENTER_X) < 200.0 && y < -58.0) {
            diveCooldowns.put(uuid, now);
            Location safeLoc = new Location(player.getWorld(), VoidChunkGenerator.SECTOR_3_CENTER_X + 0.5, -50.0, 0.5);
            player.setFallDistance(0.0f);
            player.teleport(safeLoc);
            player.setFallDistance(0.0f);
            player.setVelocity(new Vector(0, 0.2, 0));
            if (plugin.getEjectionListener() != null) {
                plugin.getEjectionListener().grantFallImmunity(uuid, 5000L);
            }
            player.playSound(safeLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);
            player.sendActionBar(Component.text("🛡 กำแพงพลังก้นบึ้งดีดตัวคุณกลับสู่ใจกลางลานประลอง!", NamedTextColor.GOLD));
            return;
        }

        // =========================================================================
        // 4. เดินเข้าเสาแสงแห่งชัยชนะ (Ascension Rift) เพื่อกลับสู่ Overworld
        // =========================================================================
        if (plugin.getBossManager().isAscensionRiftActive()) {
            Location riftLoc = plugin.getBossManager().getAscensionRiftLocation();
            if (riftLoc != null && riftLoc.getWorld().equals(player.getWorld())) {
                double distToRift = loc.distance(riftLoc);
                if (distToRift <= 3.0) {
                    diveCooldowns.put(uuid, now);

                    // อนุญาตให้ผ่าน Lockdown
                    player.setMetadata("void_authorized_escape", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

                    World overworld = Bukkit.getWorlds().get(0);
                    Location returnLoc = player.getRespawnLocation();
                    if (returnLoc == null || returnLoc.getWorld() == null) {
                        returnLoc = overworld.getSpawnLocation();
                    }

                    player.setFallDistance(0.0f);
                    player.teleport(returnLoc);
                    player.setFallDistance(0.0f);
                    player.setVelocity(new Vector(0, 0, 0));
                    if (plugin.getEjectionListener() != null) {
                        plugin.getEjectionListener().grantFallImmunity(uuid, 10000L);
                    }

                    // ล้างเอฟเฟกต์มืด
                    player.removePotionEffect(PotionEffectType.DARKNESS);
                    player.removePotionEffect(PotionEffectType.BLINDNESS);

                    player.playSound(returnLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.5f, 1.0f);
                    player.playSound(returnLoc, Sound.ENTITY_PLAYER_LEVELUP, 1.5f, 1.2f);
                    player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, returnLoc.clone().add(0, 1, 0), 60, 0.8, 1.2, 0.8, 0.3);

                    Title victoryTitle = Title.title(
                        Component.text("👑 ชัยชนะเหนือ THE VOID 👑", NamedTextColor.GOLD, TextDecoration.BOLD),
                        Component.text("คุณได้พิชิตราชันย์ก้นบึ้งและกลับสู่ Overworld อย่างผู้ชนะ!", NamedTextColor.GREEN),
                        Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(4), Duration.ofSeconds(1))
                    );
                    player.showTitle(victoryTitle);
                    player.sendMessage(Component.text("🌌 ยินดีด้วย! คุณได้พิชิตทั้ง 3 ชั้นของ The Void และนำชัยชนะกลับสู่โลกเบื้องบนสำเร็จ!", NamedTextColor.GOLD, TextDecoration.BOLD));
                }
            }
        }
    }
}
