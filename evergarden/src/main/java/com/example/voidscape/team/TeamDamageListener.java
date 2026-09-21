package com.example.voidscape.team;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * กันดาเมจระหว่างเพื่อนร่วมทีมเมื่อทีมนั้นปิด PvP
 *
 * ใช้ priority LOWEST เพื่อยกเลิกก่อนที่ listener อื่น (สกิล unique, เวท, ปลั๊กอินอื่น) จะคำนวณดาเมจ
 * และตั้ง ignoreCancelled = true เพราะถ้ามีใครยกเลิกไปแล้วก็ไม่ต้องทำอะไรซ้ำ
 *
 * หมายเหตุ: Titan Breach และ Thunderlord ใน UniqueAbilityListener หักเลือดด้วย setHealth ตรงๆ
 * ไม่ผ่าน event ใดๆ จึงต้องไปเพิ่มการเช็คทีมในไฟล์นั้นแยกต่างหาก
 */
public final class TeamDamageListener implements Listener {

    private final VoidscapePlugin plugin;
    private final TeamService teams;

    /** กันข้อความเตือนสแปม: ผู้เล่น -> เวลาที่เตือนครั้งล่าสุด */
    private final Map<UUID, Long> lastNotice = new HashMap<>();

    public TeamDamageListener(VoidscapePlugin plugin, TeamService teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    // ==========================================================
    // ดาเมจตรงและดาเมจที่มีตัวกระทำ
    // ==========================================================

    /** ตีตรงๆ, ธนูและกระสุนทุกชนิด, TNT, สัตว์เลี้ยง, เขี้ยว Evoker, สกิล unique และเวทจาก advance-magic */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event.getDamager());
        if (!teams.blocked(attacker, victim)) return;
        event.setCancelled(true);
        notice(attacker);
    }

    /** ไฟจาก Fire Aspect และลูกธนูไฟ */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCombust(EntityCombustByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = resolveAttacker(event.getCombuster());
        if (!teams.blocked(attacker, victim)) return;
        event.setCancelled(true);
    }

    // ==========================================================
    // ยาและเมฆเอฟเฟกต์ (กันตั้งแต่ตอนติดสถานะ จึงไม่ต้องไล่ตามดาเมจที่มาทีหลัง)
    // ==========================================================

    /** ยาสาด: ตัดเพื่อนร่วมทีมออกด้วยการตั้งความแรงเป็นศูนย์ คนอื่นยังโดนตามปกติ */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        Player thrower = resolveSource(event.getPotion().getShooter());
        if (thrower == null) return;
        for (LivingEntity affected : event.getAffectedEntities()) {
            if (affected instanceof Player victim && teams.blocked(thrower, victim)) {
                event.setIntensity(affected, 0.0);
            }
        }
    }

    /** ยาโปรย, เมฆ Dragon's Breath และเมฆจากเวท */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        Player owner = resolveSource(event.getEntity().getSource());
        if (owner == null) return;
        event.getAffectedEntities().removeIf(affected ->
                affected instanceof Player victim && teams.blocked(owner, victim));
    }

    // ==========================================================
    // ตัวช่วย
    // ==========================================================

    /**
     * ไล่หาผู้เล่นที่เป็นต้นตอจริงของดาเมจ
     * รองรับกระสุน (ธนู ตรีศูล ลูกไฟ ไข่ หิมะ Wind Charge) TNT เมฆเอฟเฟกต์ สัตว์เลี้ยง และเขี้ยว Evoker
     */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof Projectile projectile) return resolveSource(projectile.getShooter());
        if (damager instanceof TNTPrimed tnt) return asPlayer(tnt.getSource());
        if (damager instanceof AreaEffectCloud cloud) return resolveSource(cloud.getSource());
        if (damager instanceof EvokerFangs fangs) return asPlayer(fangs.getOwner());
        if (damager instanceof Tameable pet && pet.isTamed()) {
            org.bukkit.entity.AnimalTamer owner = pet.getOwner();
            if (owner instanceof Player player) return player;
        }
        return null;
    }

    private Player resolveSource(ProjectileSource source) {
        return source instanceof Player player ? player : null;
    }

    private Player asPlayer(Entity entity) {
        return entity instanceof Player player ? player : null;
    }

    /** บอกคนตีว่าทีมปิด PvP อยู่ แสดงที่ actionbar และเตือนไม่เกินทุก 2 วินาที */
    private void notice(Player attacker) {
        if (attacker == null) return;
        long now = System.currentTimeMillis();
        Long last = lastNotice.get(attacker.getUniqueId());
        if (last != null && now - last < 2000L) return;
        lastNotice.put(attacker.getUniqueId(), now);
        attacker.sendActionBar(Component.text("PvP ในทีมปิดอยู่ (/team pvp on)", NamedTextColor.RED));
    }

    /** เรียกจากตัวจับเวลาหลักของปลั๊กอิน เพื่อเคลียร์ข้อมูลกันสแปมที่ไม่ใช้แล้ว */
    public void tick() {
        if (lastNotice.size() < 200) return;
        long now = System.currentTimeMillis();
        lastNotice.entrySet().removeIf(entry -> now - entry.getValue() > 60000L);
    }
}
