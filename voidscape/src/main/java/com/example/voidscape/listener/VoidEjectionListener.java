package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * VoidEjectionListener: จัดการการตายในมิติ The Void (Safe Ejection)
 * 1. ยกเลิกความเสียหาย VOID DAMAGE ใน The Void 100% (ผู้เล่นสามารถดิ่งลงสู่ก้นบึ้ง Y = -1000 ได้อย่างอิสระ)
 * 2. ตกเหวไม่เตะกลับโลกเดิม ผู้เล่นจะลอยละลิ่วดิ่งลงเรื่อยๆ
 * 3. จะดีดกลับ Overworld เฉพาะตอนที่เลือดหมดตัว (HP = 0) จากมอนสเตอร์หรือแรงกดดันก้นบึ้งเท่านั้น
 */
public class VoidEjectionListener implements Listener {

    private final VoidscapePlugin plugin;
    private final Map<UUID, Long> fallImmunity = new ConcurrentHashMap<>();

    public VoidEjectionListener(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * มอบบัฟคุ้มกันความเสียหายจากการตก (Fall Immunity) ชั่วคราว
     */
    public void grantFallImmunity(UUID uuid, long durationMs) {
        fallImmunity.put(uuid, System.currentTimeMillis() + durationMs);
    }

    /**
     * ตรวจสอบว่ามีบัฟคุ้มกันความเสียหายจากการตกหรือไม่
     */
    public boolean hasFallImmunity(UUID uuid) {
        return System.currentTimeMillis() < fallImmunity.getOrDefault(uuid, 0L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        World world = player.getWorld();
        boolean isInVoid = world.getName().equals(plugin.getVoidWorldName());

        // 1. ป้องกัน Fall Damage ทั้งหมดใน The Void และตรวจสอบ Fall Immunity หลังวาร์ป
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            boolean disableVoidFall = plugin.getConfig().getBoolean("dimension.disable-fall-damage", true);
            if (isInVoid && disableVoidFall) {
                event.setCancelled(true);
                player.setFallDistance(0.0f);
                return;
            }

            if (hasFallImmunity(player.getUniqueId())) {
                event.setCancelled(true);
                player.setFallDistance(0.0f);
                return;
            }
        }

        if (!isInVoid) return;

        // 2. ยกเลิกดาเมจ Void ภายใน The Void (ทำให้ดิ่งทะลุ Y = -64, -500, -1000 ได้เรื่อยๆ)
        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            event.setCancelled(true);
            return;
        }

        // 3. ถ้าเลือดหมดตัว (เสียชีวิตใน The Void) -> ทำการ Eject ดีดกลับ Overworld โดยไม่ตายจริง
        boolean isFatal = (player.getHealth() - event.getFinalDamage()) <= 0.0;
        if (isFatal) {
            event.setCancelled(true);
            ejectPlayer(player);
        }
    }

    public void ejectPlayer(Player player) {
        // คืนค่าหลอดเลือดหัวใจสูงสุดกลับมาเป็น 10 ดวงปกติ
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            attr.setBaseValue(20.0);
        }
        player.setHealth(20.0);

        // ลบล้างดีบัฟความมืดทั้งหมด
        player.removePotionEffect(PotionEffectType.DARKNESS);
        player.removePotionEffect(PotionEffectType.BLINDNESS);

        // รีเซ็ตระยะตกและความเร็ว
        player.setFallDistance(0.0f);
        player.setVelocity(new Vector(0, 0, 0));

        // ป้องกัน Fall Damage 10 วินาที
        grantFallImmunity(player.getUniqueId(), 10000L);

        // อนุญาตให้ผ่านระบบ Lockdown เพื่อกลับสู่โลก Overworld
        player.setMetadata("void_authorized_escape", new FixedMetadataValue(plugin, true));

        // หาจุดเกิดใน Overworld (เตียงนอน หรือ Spawn)
        World overworld = Bukkit.getWorlds().get(0);
        Location returnLoc = player.getRespawnLocation();
        if (returnLoc == null || returnLoc.getWorld() == null) {
            returnLoc = overworld.getSpawnLocation();
        }

        player.teleport(returnLoc);

        player.setFallDistance(0.0f);
        player.setVelocity(new Vector(0, 0, 0));

        // เล่นเอฟเฟกต์เสียงรอดชีวิต
        player.playSound(returnLoc, Sound.ITEM_TOTEM_USE, 0.8f, 1.2f);
        player.playSound(returnLoc, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 0.8f);

        Title title = Title.title(
                Component.text("✦ THE VOID EXPELLED YOU ✦", NamedTextColor.DARK_PURPLE),
                Component.text("วิญญาณของคุณถูกขับไล่กลับสู่โลกเดิม... ไอเทมในตัวยังปลอดภัย", NamedTextColor.GREEN),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))
        );
        player.showTitle(title);
        player.sendMessage(Component.text("💀 คุณสูญเสียพลังชีวิตจนหมด พลังมิติมืดจึงดีดวิญญาณคุณกลับมาที่โลกเดิม!", NamedTextColor.RED));
    }
}
