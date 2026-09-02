package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * VoidPortalListener: จัดการการเข้าสู่ The Void อย่างถูกต้อง
 * 1. ยืนบน Bedrock ลึกสุด (Y <= -58) ต่อเนื่อง 3 วินาที (ต้องยืนแช่จนครบ 3 วิถึงจะวาร์ป)
 * 2. ตกทะลุช่องว่างใต้โลกจริง ๆ (Y < -75) ถึงจะวาร์ปทันที
 */
public class VoidPortalListener implements Listener {

    private final VoidscapePlugin plugin;
    private final Map<UUID, BukkitTask> standingTasks = new HashMap<>();

    public VoidPortalListener(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        // ทำงานเฉพาะโลกที่ไม่ใช่ The Void
        if (world.getName().equals(plugin.getVoidWorldName())) return;

        Location to = event.getTo();
        if (to == null) return;

        // 1. ตกช่องว่างใต้โลกจริง ๆ (ต้องหลุดต่ำกว่า -75 เท่านั้น ถึงจะวาร์ปทันที)
        if (to.getY() < -75.0) {
            cancelStandTask(player.getUniqueId());
            teleportToVoid(player);
            return;
        }

        // 2. ยืนบนบล็อก Bedrock ชั้นล่างสุด (ตรวจสอบบล็อกใต้เท้า)
        Block blockUnder = to.clone().subtract(0, 0.2, 0).getBlock();
        boolean isOnBedrock = blockUnder.getType() == Material.BEDROCK && to.getY() <= -56.0;

        UUID uuid = player.getUniqueId();
        if (isOnBedrock) {
            // ถ้ายังไม่เริ่มนับ 3 วิ ให้เริ่มนับ
            if (!standingTasks.containsKey(uuid)) {
                startStandTask(player);
            }
        } else {
            // ถ้าเดินออกจาก Bedrock หรือกระโดด ให้ยกเลิกการนับ
            cancelStandTask(uuid);
        }
    }

    private void startStandTask(Player player) {
        UUID uuid = player.getUniqueId();
        BukkitTask task = new BukkitRunnable() {
            int seconds = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    standingTasks.remove(uuid);
                    return;
                }

                // ตรวจสอบซ้ำว่ายังยืนอยู่บน Bedrock ไหม
                Block currentBlockUnder = player.getLocation().clone().subtract(0, 0.2, 0).getBlock();
                if (currentBlockUnder.getType() != Material.BEDROCK) {
                    cancelStandTask(uuid);
                    player.sendActionBar(Component.text("❌ ยกเลิกการเปิดประตูมิติ", NamedTextColor.GRAY));
                    return;
                }

                seconds++;
                Location loc = player.getLocation();

                // เอฟเฟกต์หมอกควันดำและเสียงหัวใจเต้น
                player.getWorld().spawnParticle(Particle.LARGE_SMOKE, loc.add(0, 0.2, 0), 18, 0.3, 0.2, 0.3, 0.03);
                player.playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.0f, 0.7f + (seconds * 0.3f));

                player.sendActionBar(Component.text(
                    String.format("✦ พลังมิติมืดกำลังเปิดออก... (%d/3 วิ)", seconds),
                    NamedTextColor.DARK_PURPLE
                ));

                if (seconds >= 3) {
                    cancel();
                    standingTasks.remove(uuid);
                    teleportToVoid(player);
                }
            }
        }.runTaskTimer(plugin, 0L, 20L);

        standingTasks.put(uuid, task);
    }

    private void cancelStandTask(UUID uuid) {
        BukkitTask task = standingTasks.remove(uuid);
        if (task != null) {
            task.cancel();
        }
    }

    public void teleportToVoid(Player player) {
        World voidWorld = plugin.getVoidWorld();
        if (voidWorld == null) {
            player.sendMessage(Component.text("ไม่พบโลก The Void บนเซิร์ฟเวอร์", NamedTextColor.RED));
            return;
        }

        cancelStandTask(player.getUniqueId());

        // รีเซ็ตระยะตกเพื่อความปลอดภัย
        player.setFallDistance(0.0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        // วาร์ปไปที่จุดกึ่งกลางของเกาะหิน Tier 1 Zenith Altar (0.5, 261.0, 0.5) อย่างปลอดภัย
        Location spawnLoc = new Location(voidWorld, 0.5, 261.0, 0.5, 0f, 0f);
        player.teleport(spawnLoc);

        player.setFallDistance(0.0f);
        player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

        // กำจัดมังกรและซ่อนหลอดเลือด Ender Dragon จากผู้เล่นทันที
        plugin.suppressEnderDragon(voidWorld);

        player.playSound(spawnLoc, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.2f, 0.5f);
        player.playSound(spawnLoc, Sound.AMBIENT_CAVE, 1.5f, 0.5f);

        Title title = Title.title(
                Component.text("✦ THE VOID ✦", NamedTextColor.DARK_PURPLE),
                Component.text("คุณได้ก้าวเข้าสู่ห้วงความว่างเปล่าใต้พิภพ", NamedTextColor.GRAY),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(3), Duration.ofSeconds(1))
        );
        player.showTitle(title);
    }

    @EventHandler
    public void onDragonSpawn(org.bukkit.event.entity.EntitySpawnEvent event) {
        if (event.getEntity() instanceof org.bukkit.entity.EnderDragon) {
            if (event.getEntity().getWorld().getName().equals(plugin.getVoidWorldName())) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onVoidDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        // ดักจับดาเมจ Void ในโลกอื่น เพื่อวาร์ปเข้า The Void แทนการตาย
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            if (!player.getWorld().getName().equals(plugin.getVoidWorldName())) {
                event.setCancelled(true);
                teleportToVoid(player);
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        cancelStandTask(event.getPlayer().getUniqueId());
    }
}
