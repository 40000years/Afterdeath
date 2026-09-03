package com.example.voidscape.boss;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.entity.Warden;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * VoidBossManager: ควบคุมระบบ Abyssal Warden Boss (ราชันย์ก้นบึ้งทมิฬ)
 * - เกิดอัตโนมัติประจำการ ณ ชั้นล่างสุด Tier 10 ทันที (ไม่ต้องใช้คำสั่ง)
 * - ขนาดตัวยักษ์ไททันมหึมา Scale 4.5x (สูงกว่า 13 บล็อก!)
 * - เลือดมหาศาล 5,000 HP (2,500 หัวใจ)
 * - การันตี 1-Hit Kill
 * - หลอดเลือด BossBar สีม่วงเรียลไทม์ปรากฏเมื่อผู้เล่นเข้าสู่ชั้นล่าง
 * - ป้องกันตก Void 100% ด้วยระบบ Abyssal Recall
 * - เมื่อตาย: ล้าง Sculk ออก 100% และเสกแท่นบล็อกเนเธอไรต์ 7 บล็อก + หีบสมบัติอุปกรณ์เทพสุ่ม Enchant (Sharpness 15, Protection 20, Fortune 10 ฯลฯ)
 */
public class VoidBossManager implements Listener {

    private final VoidscapePlugin plugin;
    private final NamespacedKey keyAbyssalBoss;

    private UUID currentBossUuid = null;
    private Warden cachedBoss = null;
    private BossBar bossBar = null;
    private BukkitTask bossTrackerTask = null;
    private long lastDeathTime = 0L;
    private long lastGravityPullTime = 0L;

    private double maxBossHealth = 5000.0;
    private double currentBossHealth = 5000.0;
    private boolean usesVirtualHealth = false;

    private boolean isAscensionRiftActive = false;
    private Location ascensionRiftLocation = null;

    public boolean isAscensionRiftActive() {
        return isAscensionRiftActive;
    }

    public Location getAscensionRiftLocation() {
        return ascensionRiftLocation;
    }

    public VoidBossManager(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.keyAbyssalBoss = new NamespacedKey(plugin, "is_abyssal_warden_boss");

        initBossBar();
        ensureArenaChunkLoaded();
        scanExistingBoss();
        startBossTracker();
        Bukkit.getScheduler().runTaskLater(plugin, this::checkAutoSpawnAndRespawn, 20L);
    }

    private void initBossBar() {
        String title = plugin.getConfig().getString("boss.name", "💀 ราชันย์ก้นบึ้งทมิฬ (The Abyssal Warden)");
        bossBar = Bukkit.createBossBar(title, BarColor.PURPLE, BarStyle.SEGMENTED_20);
        bossBar.setVisible(false);
    }

    /**
     * บังคับโหลด Chunk ลานประลอง Layer 3 (8000, 0) เพื่อไม่ให้บอส Despawn หรือหลุดการเชื่อมต่อ
     */
    public void ensureArenaChunkLoaded() {
        World voidWorld = plugin.getVoidWorld();
        if (voidWorld != null) {
            int chunkX = 8000 >> 4; // Chunk 500
            voidWorld.getChunkAt(chunkX, 0).setForceLoaded(true);
            voidWorld.addPluginChunkTicket(chunkX, 0, plugin);
        }
    }

    /**
     * สแกนหาบอสเดิมที่อาจเกิดอยู่แล้วในโลก The Void
     */
    public void scanExistingBoss() {
        World voidWorld = plugin.getVoidWorld();
        if (voidWorld == null) return;

        for (Warden warden : voidWorld.getEntitiesByClass(Warden.class)) {
            if (isBossEntity(warden)) {
                if (warden.isValid() && !warden.isDead()) {
                    cachedBoss = warden;
                    currentBossUuid = warden.getUniqueId();
                    plugin.getLogger().info("✦ ตรวจพบ Abyssal Warden Boss เดิมในระบบ (UUID: " + currentBossUuid + ")");
                    updateBossBar();
                    return;
                }
            }
        }
    }

    private boolean isBossEntity(Warden warden) {
        if (warden == null) return false;
        if (warden.getPersistentDataContainer().has(keyAbyssalBoss, PersistentDataType.BYTE)) return true;
        if (currentBossUuid != null && warden.getUniqueId().equals(currentBossUuid)) return true;
        if (cachedBoss != null && warden.equals(cachedBoss)) return true;
        Component customName = warden.customName();
        if (customName != null) {
            String plain = net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(customName);
            return plain.contains("ราชันย์ก้นบึ้ง") || plain.contains("Abyssal Warden");
        }
        return false;
    }

    /**
     * ตรวจสอบว่าบอสยังมีชีวิตอยู่หรือไม่ (ไม่หลุดแม้ Chunk จะสลับโหลด)
     */
    public boolean isBossAlive() {
        if (cachedBoss != null && cachedBoss.isValid() && !cachedBoss.isDead()) {
            return true;
        }

        World voidWorld = plugin.getVoidWorld();
        if (voidWorld == null) return false;

        for (Warden warden : voidWorld.getEntitiesByClass(Warden.class)) {
            if (isBossEntity(warden) && warden.isValid() && !warden.isDead()) {
                cachedBoss = warden;
                currentBossUuid = warden.getUniqueId();
                return true;
            }
        }

        cachedBoss = null;
        return false;
    }

    public Warden getBoss() {
        if (isBossAlive()) {
            return cachedBoss;
        }
        return null;
    }

    /**
     * เสกหรืออัปเกรด Warden ให้กลายเป็น Abyssal Warden Boss
     */
    public Warden spawnBoss(Location loc) {
        if (isBossAlive()) {
            return cachedBoss; // มีบอสอยู่แล้ว ไม่เสกซ้ำ
        }

        World world = loc.getWorld();
        if (world == null) return null;

        try {
            ensureArenaChunkLoaded();
            Warden warden = (Warden) world.spawnEntity(loc, EntityType.WARDEN);
            applyBossAttributes(warden);

            cachedBoss = warden;
            currentBossUuid = warden.getUniqueId();

            // เอฟเฟกต์เปิดตัวระดับตำนาน
            world.spawnParticle(Particle.SONIC_BOOM, loc, 6);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 2, 0), 80, 2.0, 2.0, 2.0, 0.08);
            world.playSound(loc, Sound.ENTITY_WARDEN_ROAR, 3.0f, 0.4f);
            world.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 2.0f, 0.5f);

            // ประกาศก้องทั่วโลก The Void
            Title title = Title.title(
                Component.text("💀 THE ABYSSAL WARDEN 💀", NamedTextColor.DARK_RED, TextDecoration.BOLD),
                Component.text("ราชันย์ก้นบึ้งทมิฬตื่นขึ้น ณ ฐานรากมิติมืด!", NamedTextColor.RED),
                Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(4), Duration.ofSeconds(1))
            );

            for (Player p : world.getPlayers()) {
                p.showTitle(title);
                p.sendMessage(Component.text("═════════════════════════════════════════", NamedTextColor.DARK_RED));
                p.sendMessage(Component.text("💀 ราชันย์ก้นบึ้งทมิฬ (The Abyssal Warden) ปรากฏกายขึ้นแล้ว!", NamedTextColor.RED, TextDecoration.BOLD));
                p.sendMessage(Component.text("   HP: 5,000 | ขนาดไททัน 4.5x | พลังโจมตี 1-Hit Kill | ชั้นล่างสุด Tier 10", NamedTextColor.GRAY));
                p.sendMessage(Component.text("═════════════════════════════════════════", NamedTextColor.DARK_RED));
            }

            updateBossBar();
            return warden;
        } catch (Exception e) {
            plugin.getLogger().severe("เกิดข้อผิดพลาดขณะเสก Abyssal Warden Boss: " + e.getMessage());
            return null;
        }
    }

    /**
     * อัปเกรด Warden ตัวใดๆ ให้กลายเป็นมหาบอส
    /**
     * อัปเกรด Warden ตัวใดๆ ให้กลายเป็นมหาบอส (พร้อมระบบทำลายและเลี่ยง Attribute Cap 100%)
     */
    public void applyBossAttributes(Warden warden) {
        double scale = plugin.getConfig().getDouble("boss.scale", 4.5);
        double maxHealth = plugin.getConfig().getDouble("boss.health", 5000.0);
        double attackDamage = plugin.getConfig().getDouble("boss.attack-damage", 2000.0);
        String name = plugin.getConfig().getString("boss.name", "💀 ราชันย์ก้นบึ้งทมิฬ (The Abyssal Warden)");

        this.maxBossHealth = maxHealth;
        this.currentBossHealth = maxHealth;

        // 1. ขนาดตัวโมเดลยักษ์ไททันมหึมา (4.5x)
        try {
            AttributeInstance scaleAttr = warden.getAttribute(Attribute.SCALE);
            if (scaleAttr != null) {
                scaleAttr.setBaseValue(scale);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("ไม่สามารถตั้งค่า SCALE: " + e.getMessage());
        }

        // 2. หลอดเลือด 5,000 HP (พร้อมระบบทำลาย Spigot Cap และ Virtual Health Pool อัตโนมัติ)
        tryBreakSpigotHealthCap();

        AttributeInstance healthAttr = warden.getAttribute(Attribute.MAX_HEALTH);
        if (healthAttr != null) {
            try {
                // พยายามตั้งค่า 5,000 HP โดยตรง
                healthAttr.setBaseValue(maxHealth);
                warden.setHealth(maxHealth);
                this.usesVirtualHealth = false;
            } catch (IllegalArgumentException ex) {
                // หากติด Cap (เช่น spigot.yml ล็อคไว้ที่ 2048.0)
                // ให้ตั้งค่าสูงสุดที่ปลอดภัยของเอนจิน และเปิดระบบ Virtual Health Pool 5,000 HP ทันที
                this.usesVirtualHealth = true;
                double safeEngineHp = 2000.0;
                try {
                    healthAttr.setBaseValue(safeEngineHp);
                    warden.setHealth(safeEngineHp);
                } catch (IllegalArgumentException ex2) {
                    safeEngineHp = 500.0;
                    try {
                        healthAttr.setBaseValue(safeEngineHp);
                        warden.setHealth(safeEngineHp);
                    } catch (Exception ignored) {}
                }
                plugin.getLogger().info("✦ ทำลายข้อจำกัด Cap: เปิดระบบ Virtual Health Pool บอสมีเลือด 5,000 HP จริง 100%!");
            }
        }

        // 3. ต้านทาน Knockback 100%
        try {
            AttributeInstance kbAttr = warden.getAttribute(Attribute.KNOCKBACK_RESISTANCE);
            if (kbAttr != null) {
                kbAttr.setBaseValue(1.0);
            }
        } catch (Exception ignored) {}

        // 4. พลังโจมตีการันตี 1-Hit Kill
        try {
            AttributeInstance dmgAttr = warden.getAttribute(Attribute.ATTACK_DAMAGE);
            if (dmgAttr != null) {
                dmgAttr.setBaseValue(attackDamage);
            }
        } catch (Exception ignored) {}

        // 5. ป้องกันมอนสเตอร์หาย (Despawn)
        warden.setRemoveWhenFarAway(false);
        warden.setPersistent(true);

        // 6. ตั้งชื่อ
        warden.customName(Component.text(name, NamedTextColor.DARK_RED, TextDecoration.BOLD));
        warden.setCustomNameVisible(true);

        // 7. ติดแท็กระบุตัวตน
        warden.getPersistentDataContainer().set(keyAbyssalBoss, PersistentDataType.BYTE, (byte) 1);
    }

    /**
     * พยายามปลดล็อค maxHealth cap ของ Spigot ผ่าน Reflection
     */
    private void tryBreakSpigotHealthCap() {
        try {
            Class<?> spigotConfig = Class.forName("org.spigotmc.SpigotConfig");
            java.lang.reflect.Field field = spigotConfig.getDeclaredField("maxHealth");
            field.setAccessible(true);
            field.setDouble(null, 100000.0);
        } catch (Throwable ignored) {}
    }

    /**
     * ดึงตัวบอสกลับสู่ใจกลางลานประลองทันที (Abyssal Recall) ป้องกันบอสตก Void 100%
     */
    public void recallBoss(Warden boss) {
        double arenaX = plugin.getConfig().getDouble("boss.arena-center-x", 8000.5);
        double arenaY = plugin.getConfig().getDouble("boss.arena-center-y", -51.0);
        double arenaZ = plugin.getConfig().getDouble("boss.arena-center-z", 0.5);

        Location safeCenter = new Location(boss.getWorld(), arenaX, arenaY, arenaZ, 0f, 0f);

        boss.getWorld().spawnParticle(Particle.SONIC_BOOM, boss.getLocation(), 3);
        boss.getWorld().playSound(boss.getLocation(), Sound.ENTITY_WARDEN_ROAR, 2.5f, 0.5f);

        boss.teleport(safeCenter);
        boss.setVelocity(new Vector(0, 0, 0));
        boss.setFallDistance(0.0f);

        boss.getWorld().spawnParticle(Particle.PORTAL, safeCenter, 50, 1.5, 1.5, 1.5, 0.1);
        boss.getWorld().playSound(safeCenter, Sound.BLOCK_RESPAWN_ANCHOR_DEPLETE, 1.8f, 0.5f);

        for (Entity nearby : boss.getNearbyEntities(80.0, 40.0, 80.0)) {
            if (nearby instanceof Player player) {
                player.sendMessage(Component.text("💀 ราชันย์ก้นบึ้งทมิฬฉีกมิติกลับสู่ใจกลางลานประลอง!", NamedTextColor.RED));
            }
        }
    }

    /**
     * ลูปติดตามสถานะบอส (Boss Tracker Task) รันทุกๆ 10 ticks (0.5 วินาที)
     */
    private void startBossTracker() {
        bossTrackerTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (!plugin.getConfig().getBoolean("boss.enabled", true)) {
                    if (bossBar != null) bossBar.setVisible(false);
                    return;
                }

                // หากบอสยังไม่มีชีวิตอยู่ ให้ทำการเกิดอัตโนมัติประจำชั้นล่างสุดทันที
                if (!isBossAlive()) {
                    if (bossBar != null) bossBar.setVisible(false);
                    checkAutoSpawnAndRespawn();
                    return;
                }

                Warden boss = getBoss();
                if (boss == null) return;

                // 1. ป้องกันบอสขุดดินหนี (Anti-Digging)
                if (boss.getPose() == Pose.DIGGING) {
                    boss.setPose(Pose.STANDING, true);
                }

                // 2. ป้องกันบอสตก Void (Abyssal Recall)
                double arenaX = plugin.getConfig().getDouble("boss.arena-center-x", 0.5);
                double arenaZ = plugin.getConfig().getDouble("boss.arena-center-z", 0.5);
                double arenaRadius = plugin.getConfig().getDouble("boss.arena-radius", 135.0);

                Location bLoc = boss.getLocation();
                double distFromCenter = Math.hypot(bLoc.getX() - arenaX, bLoc.getZ() - arenaZ);

                if (bLoc.getY() < -58.0 || distFromCenter > arenaRadius) {
                    recallBoss(boss);
                }

                // 3. จัดการเป้าหมายผู้เล่น และสกิลดึงตัว Abyssal Gravity Pull
                handleCombatAndPlayers(boss, arenaX, arenaZ);

                // 4. อัปเดต BossBar ให้ผู้เล่นที่อยู่ชั้นล่างเห็นทุกคน
                updateBossBar();
            }
        }.runTaskTimer(plugin, 40L, 10L);
    }

    private void handleCombatAndPlayers(Warden boss, double arenaX, double arenaZ) {
        Location bLoc = boss.getLocation();
        World world = boss.getWorld();

        Player nearestPlayer = null;
        double nearestDistSq = Double.MAX_VALUE;

        for (Player player : world.getPlayers()) {
            if (!player.isOnline() || player.isDead()) continue;

            double dSq = player.getLocation().distanceSquared(bLoc);
            if (dSq < nearestDistSq) {
                nearestDistSq = dSq;
                nearestPlayer = player;
            }
        }

        if (nearestPlayer != null && nearestDistSq <= (85.0 * 85.0)) {
            boss.increaseAnger(nearestPlayer, 100);
            boss.setDisturbanceLocation(nearestPlayer.getLocation());

            boolean enablePull = plugin.getConfig().getBoolean("boss.enable-gravity-pull", true);
            long now = System.currentTimeMillis();
            if (enablePull && (now - lastGravityPullTime > 6000L)) {
                double dist = Math.sqrt(nearestDistSq);
                if (dist > 25.0 && dist < 80.0) {
                    lastGravityPullTime = now;
                    pullPlayerToArena(nearestPlayer, bLoc);
                }
            }
        }
    }

    private void pullPlayerToArena(Player player, Location bossLoc) {
        Vector dir = bossLoc.toVector().subtract(player.getLocation().toVector()).normalize();
        dir.multiply(2.0);
        dir.setY(0.4);

        player.setVelocity(dir);
        player.playSound(player.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
        player.sendMessage(Component.text("⚠️ แรงดึงดูดก้นบึ้งของราชันย์ทมิฬกระชากคุณเข้าสู่ใจกลางสนามรบ!", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
    }

    /**
     * เกิดอัตโนมัติประจำชั้นล่างสุดทันที และเกิดใหม่อัตโนมัติเมื่อครบเวลา
     */
    private void checkAutoSpawnAndRespawn() {
        World voidWorld = plugin.getVoidWorld();
        if (voidWorld == null) return;

        boolean autoStart = plugin.getConfig().getBoolean("boss.auto-spawn-on-start", true);
        int respawnMin = plugin.getConfig().getInt("boss.respawn-interval-minutes", 60);

        boolean shouldSpawn = false;

        if (lastDeathTime == 0L && autoStart) {
            // เซิร์ฟเวอร์เพิ่งเปิดหรือบอสยังไม่เคยเกิด -> ให้เกิดทันทีประจำชั้นล่างสุด
            shouldSpawn = true;
        } else if (lastDeathTime > 0L && respawnMin > 0) {
            long elapsedMs = System.currentTimeMillis() - lastDeathTime;
            if (elapsedMs >= (respawnMin * 60L * 1000L)) {
                shouldSpawn = true;
            }
        }

        if (shouldSpawn) {
            double arenaX = plugin.getConfig().getDouble("boss.arena-center-x", 8000.5);
            double arenaY = plugin.getConfig().getDouble("boss.arena-center-y", -51.0);
            double arenaZ = plugin.getConfig().getDouble("boss.arena-center-z", 0.5);
            Location spawnLoc = new Location(voidWorld, arenaX, arenaY, arenaZ);
            spawnBoss(spawnLoc);
        }
    }

    /**
     * อัปเดต BossBar ให้ผู้เล่นทุกคนที่ลงมาสู่ชั้นล่าง (Y <= 50 หรือในระยะ 160 บล็อก) เห็นชัดเจน
     */
    private void updateBossBar() {
        if (bossBar == null) return;

        Warden boss = getBoss();
        if (boss == null) {
            bossBar.setVisible(false);
            return;
        }

        double maxHp = maxBossHealth;
        double currentHp;
        if (usesVirtualHealth) {
            currentHp = Math.max(0.0, Math.min(maxHp, currentBossHealth));
        } else {
            AttributeInstance maxHpAttr = boss.getAttribute(Attribute.MAX_HEALTH);
            maxHp = maxHpAttr != null ? maxHpAttr.getBaseValue() : 5000.0;
            currentHp = Math.max(0.0, Math.min(maxHp, boss.getHealth()));
        }

        double progress = Math.max(0.0, Math.min(1.0, currentHp / maxHp));

        bossBar.setProgress(progress);
        bossBar.setTitle(String.format("💀 ราชันย์ก้นบึ้งทมิฬ (The Abyssal Warden) [HP: %.0f / %.0f]", currentHp, maxHp));
        bossBar.setVisible(true);

        World voidWorld = boss.getWorld();
        Location bLoc = boss.getLocation();
        Set<Player> eligiblePlayers = new HashSet<>();

        for (Player p : voidWorld.getPlayers()) {
            if (!p.isOnline()) continue;

            // แสดง BossBar เมื่อผู้เล่นเข้าสู่ Layer 3 (X >= 6500) หรืออยู่ในระยะ 200 บล็อกของบอส
            boolean inLayer3 = p.getLocation().getX() >= 6500.0;
            boolean nearArena = p.getLocation().distanceSquared(bLoc) <= (200.0 * 200.0);

            if (inLayer3 || nearArena) {
                eligiblePlayers.add(p);
                if (!bossBar.getPlayers().contains(p)) {
                    bossBar.addPlayer(p);
                }
            }
        }

        for (Player p : new HashSet<>(bossBar.getPlayers())) {
            if (!eligiblePlayers.contains(p)) {
                bossBar.removePlayer(p);
            }
        }
    }

    // ==========================================
    // Event Listeners: ดาเมจ การเกิด และการตายของบอส
    // ==========================================

    /**
     * หากมี Warden ตัวใดๆ เกิดขึ้นในโลก The Void ให้แปลงเป็น Abyssal Warden Boss ทันที
     */
    @EventHandler(priority = EventPriority.HIGH)
    public void onEntitySpawn(EntitySpawnEvent event) {
        if (event.getEntity() instanceof Warden warden) {
            World world = warden.getWorld();
            if (world.getName().equals(plugin.getVoidWorldName())) {
                if (!isBossAlive()) {
                    applyBossAttributes(warden);
                    cachedBoss = warden;
                    currentBossUuid = warden.getUniqueId();
                    updateBossBar();
                } else if (!isBossEntity(warden)) {
                    // หากมีบอสอยู่แล้วตัวหนึ่ง บล็อกไม่ให้มี Warden ตัวอื่นซ้ำซ้อน
                    event.setCancelled(true);
                }
            }
        }
    }

    /**
     * การันตี 1-Hit Kill
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBossAttack(EntityDamageByEntityEvent event) {
        if (!plugin.getConfig().getBoolean("boss.one-hit-kill", true)) return;

        Entity damager = event.getDamager();
        Entity victim = event.getEntity();

        if (!(victim instanceof Player)) return;

        boolean isFromBoss = false;
        if (damager instanceof Warden warden && isBossEntity(warden)) {
            isFromBoss = true;
        }

        if (isFromBoss) {
            event.setDamage(9999.0);
            damager.getWorld().playSound(victim.getLocation(), Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.8f);
        }
    }

    /**
     * จัดการดาเมจของบอส ป้องกันตก Void และคำนวณเลือด Virtual Health Pool 5,000 HP
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBossDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Warden warden && isBossEntity(warden)) {
            if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
                event.setCancelled(true);
                recallBoss(warden);
                return;
            } else if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
                event.setCancelled(true);
                return;
            }

            // คำนวณเลือด Virtual Health Pool (5,000 HP ทะลุข้อจำกัด Cap)
            if (usesVirtualHealth) {
                double dmg = event.getFinalDamage();
                currentBossHealth -= dmg;

                if (currentBossHealth <= 0.0) {
                    currentBossHealth = 0.0;
                    warden.setHealth(0.0); // บอสตาย
                } else {
                    AttributeInstance healthAttr = warden.getAttribute(Attribute.MAX_HEALTH);
                    double baseMax = (healthAttr != null) ? healthAttr.getBaseValue() : 2000.0;
                    double ratio = currentBossHealth / maxBossHealth;
                    warden.setHealth(Math.max(1.0, ratio * baseMax));
                    event.setDamage(0.0); // ดาเมจถูกหักจาก Virtual Health แล้ว
                }
                updateBossBar();
            }
        }
    }

    /**
     * จัดการการฟื้นฟูเลือดของบอสเมื่ออยู่ในโหมด Virtual Health
     */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBossRegainHealth(org.bukkit.event.entity.EntityRegainHealthEvent event) {
        if (event.getEntity() instanceof Warden warden && isBossEntity(warden)) {
            if (usesVirtualHealth) {
                currentBossHealth = Math.min(maxBossHealth, currentBossHealth + event.getAmount());
                updateBossBar();
            }
        }
    }

    /**
     * เมื่อบอสตาย: ล้าง Sculk ทิ้ง 100%, เสกแท่น Netherite Block 7 บล็อก + หีบอุปกรณ์เทพ
     */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBossDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Warden warden)) return;

        // ตรวจสอบว่าเป็นบอส หรือเป็น Warden ในมิติ The Void
        boolean isBoss = isBossEntity(warden) || warden.getWorld().getName().equals(plugin.getVoidWorldName());
        if (!isBoss) return;

        lastDeathTime = System.currentTimeMillis();
        currentBossUuid = null;
        cachedBoss = null;

        if (bossBar != null) {
            bossBar.setVisible(false);
            bossBar.removeAll();
        }

        // ล้างของดรอปเดิมออก 100% (เพื่อตัด Sculk Catalyst ดรอปของ Vanilla ออกทั้งหมด)
        event.getDrops().clear();
        event.setDroppedExp(8000);

        World world = warden.getWorld();
        Location loc = warden.getLocation();

        // เสกแท่นบูชาสมบัติเนเธอไรต์ 7 บล็อก พร้อมหีบสมบัติอุปกรณ์เทพ
        spawnTreasureShrine(loc);

        // เสกเสาแสงแห่งชัยชนะ (Ascension Rift) เพื่อเปิดทางกลับ Overworld
        spawnAscensionRift(loc);

        // เอฟเฟกต์และเสียงชัยชนะระดับมหากาพย์
        world.spawnParticle(Particle.EXPLOSION_EMITTER, loc, 4);
        world.playSound(loc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.5f, 0.7f);
        world.playSound(loc, Sound.ENTITY_ENDER_DRAGON_DEATH, 2.0f, 0.5f);

        Title victoryTitle = Title.title(
            Component.text("✦ ABYSSAL DEFEATED ✦", NamedTextColor.GOLD, TextDecoration.BOLD),
            Component.text("ราชันย์ก้นบึ้งทมิฬถูกปราบลงแล้ว! แท่นบูชาสมบัติปรากฏขึ้น...", NamedTextColor.YELLOW),
            Title.Times.times(Duration.ofMillis(500), Duration.ofSeconds(5), Duration.ofSeconds(2))
        );

        for (Player p : world.getPlayers()) {
            p.showTitle(victoryTitle);
            p.sendMessage(Component.text("═════════════════════════════════════════", NamedTextColor.GOLD));
            p.sendMessage(Component.text("🏆 มหาศึกสิ้นสุด! ราชันย์ก้นบึ้งทมิฬ (The Abyssal Warden) ถูกกำจัดแล้ว!", NamedTextColor.YELLOW, TextDecoration.BOLD));
            p.sendMessage(Component.text("✨ แท่นบูชาสมบัติเนเธอไรต์ 7 บล็อก และหีบสมบัติอุปกรณ์สุ่ม Enchant ขั้นเทพ ปรากฏขึ้น ณ จุดบอสตาย!", NamedTextColor.GREEN, TextDecoration.BOLD));
            p.sendMessage(Component.text("   (เปิดหีบเพื่อรับอุปกรณ์เนเธอไรต์ Sharpness XV, Protection XX, Fortune X ฯลฯ)", NamedTextColor.AQUA));
            p.sendMessage(Component.text("═════════════════════════════════════════", NamedTextColor.GOLD));
        }
    }

    /**
     * เสกแท่นบล็อก Netherite Block 7 บล็อก พร้อมหีบสมบัติอุปกรณ์เทพ
     */
    private void spawnTreasureShrine(Location deathLoc) {
        World world = deathLoc.getWorld();
        if (world == null) return;

        try {
            int bx = deathLoc.getBlockX();
            int bz = deathLoc.getBlockZ();
            int by = deathLoc.getBlockY();

            // ค้นหาหรือกำหนดระดับความสูงของพื้นผิว (Tier 10 ลานประลองอยู่ที่ Y = -51)
            int groundY = by;
            while (groundY > -58 && world.getBlockAt(bx, groundY, bz).isEmpty()) {
                groundY--;
            }
            if (groundY <= -58) {
                groundY = -52;
            }

            int shrineY = groundY + 1;

            // ล้างบล็อก Sculk หรือ Sculk Catalyst ที่เอนจิน Vanilla อาจจะพยายามสร้างขึ้นเมื่อ Warden ตาย
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    for (int dy = 0; dy <= 2; dy++) {
                        Block b = world.getBlockAt(bx + dx, shrineY + dy, bz + dz);
                        if (b.getType() == Material.SCULK || b.getType() == Material.SCULK_CATALYST || b.getType() == Material.SCULK_VEIN) {
                            b.setType(Material.AIR);
                        }
                    }
                }
            }

            // 1. เสกบล็อก Netherite Block 7 บล็อก ล้อมรอบจุดศูนย์กลาง
            int[][] netheriteOffsets = {
                {1, 0}, {-1, 0}, {0, 1}, {0, -1},
                {1, 1}, {-1, -1}, {1, -1}
            };

            for (int[] offset : netheriteOffsets) {
                Block nb = world.getBlockAt(bx + offset[0], shrineY, bz + offset[1]);
                nb.setType(Material.NETHERITE_BLOCK);
            }

            // 2. วางหีบสมบัติใจกลางแท่น
            Block chestBlock = world.getBlockAt(bx, shrineY, bz);
            chestBlock.setType(Material.CHEST);

            List<ItemStack> godPool = createGodTierGearPool();
            Collections.shuffle(godPool);

            List<ItemStack> rewards = new ArrayList<>();
            int itemCount = 3 + ThreadLocalRandom.current().nextInt(3); // สุ่ม 3-5 ชิ้น
            for (int i = 0; i < itemCount && i < godPool.size(); i++) {
                rewards.add(godPool.get(i));
            }

            rewards.add(plugin.getItemManager().createVoidCrystal(16));
            rewards.add(plugin.getItemManager().createNullFruit(8));
            rewards.add(plugin.getItemManager().createShadowBlade());
            rewards.add(plugin.getItemManager().createPocketVoid());

            Location centerLoc = new Location(world, bx + 0.5, shrineY + 1.0, bz + 0.5);

            if (chestBlock.getState() instanceof Chest chest) {
                Inventory inv = chest.getInventory();
                for (ItemStack item : rewards) {
                    inv.addItem(item);
                }
            } else {
                // หากติดขัดไม่สามารถวางหีบได้ ให้ดรอปไอเทมลงสู่พื้นโดยตรงเพื่อความปลอดภัย 100%
                for (ItemStack item : rewards) {
                    world.dropItemNaturally(centerLoc, item);
                }
            }

            // 3. เอฟเฟกต์แสงและเสียงระดับตำนาน
            world.spawnParticle(Particle.TOTEM_OF_UNDYING, centerLoc, 100, 2.0, 1.5, 2.0, 0.3);
            world.spawnParticle(Particle.SOUL_FIRE_FLAME, centerLoc, 60, 1.5, 1.0, 1.5, 0.05);
            world.playSound(centerLoc, Sound.BLOCK_BEACON_ACTIVATE, 2.5f, 1.0f);
            world.playSound(centerLoc, Sound.UI_TOAST_CHALLENGE_COMPLETE, 2.0f, 0.9f);

        } catch (Exception e) {
            plugin.getLogger().severe("เกิดข้อผิดพลาดขณะเสกแท่นบูชาสมบัติ: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * คลังอุปกรณ์เนเธอไรต์ขั้นเทพสุ่ม Enchantment ทะลุขีดจำกัด
     */
    private List<ItemStack> createGodTierGearPool() {
        List<ItemStack> pool = new ArrayList<>();

        // 1. ดาบเนเธอไรต์: Sharpness 15, Unbreaking 10, Looting 5, Fire Aspect 5, Mending
        ItemStack sword = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta sm = sword.getItemMeta();
        if (sm != null) {
            sm.displayName(Component.text("⚔️ ดาบพิฆาตราชันย์ก้นบึ้ง (Abyssal Slayer)", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            sm.lore(List.of(
                Component.text("ดาบแห่งมหาตำนานที่สังหารราชันย์ก้นบึ้งทมิฬ", NamedTextColor.GRAY),
                Component.text("✦ Sharpness XV (คมกริบขั้น 15)", NamedTextColor.RED),
                Component.text("✦ Unbreaking X / Looting V / Fire Aspect V", NamedTextColor.GOLD)
            ));
            sm.addEnchant(Enchantment.SHARPNESS, 15, true);
            sm.addEnchant(Enchantment.UNBREAKING, 10, true);
            sm.addEnchant(Enchantment.LOOTING, 5, true);
            sm.addEnchant(Enchantment.FIRE_ASPECT, 5, true);
            sm.addEnchant(Enchantment.MENDING, 1, true);
            sword.setItemMeta(sm);
        }
        pool.add(sword);

        // 2. เกราะอกเนเธอไรต์: Protection 20, Unbreaking 10, Mending
        ItemStack chestplate = new ItemStack(Material.NETHERITE_CHESTPLATE);
        ItemMeta cm = chestplate.getItemMeta();
        if (cm != null) {
            cm.displayName(Component.text("🛡️ เกราะอกราชันย์ก้นบึ้ง (Abyssal Aegis)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            cm.lore(List.of(
                Component.text("เกราะเหล็กดำหนาพิเศษ อาบพลังความมืดอันแข็งแกร่ง", NamedTextColor.GRAY),
                Component.text("✦ Protection XX (ป้องกันขั้น 20)", NamedTextColor.AQUA),
                Component.text("✦ Unbreaking X / Mending", NamedTextColor.GOLD)
            ));
            cm.addEnchant(Enchantment.PROTECTION, 20, true);
            cm.addEnchant(Enchantment.UNBREAKING, 10, true);
            cm.addEnchant(Enchantment.MENDING, 1, true);
            chestplate.setItemMeta(cm);
        }
        pool.add(chestplate);

        // 3. หมวกเนเธอไรต์: Protection 20, Respiration 5, Unbreaking 10, Mending
        ItemStack helmet = new ItemStack(Material.NETHERITE_HELMET);
        ItemMeta hm = helmet.getItemMeta();
        if (hm != null) {
            hm.displayName(Component.text("👑 มงกุฎราชันย์แห่งความมืด (Abyssal Crown)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            hm.lore(List.of(
                Component.text("มงกุฎของเจ้าแห่งห้วงมิติมืด", NamedTextColor.GRAY),
                Component.text("✦ Protection XX (ป้องกันขั้น 20)", NamedTextColor.AQUA),
                Component.text("✦ Respiration V / Unbreaking X", NamedTextColor.GOLD)
            ));
            hm.addEnchant(Enchantment.PROTECTION, 20, true);
            hm.addEnchant(Enchantment.RESPIRATION, 5, true);
            hm.addEnchant(Enchantment.UNBREAKING, 10, true);
            hm.addEnchant(Enchantment.MENDING, 1, true);
            helmet.setItemMeta(hm);
        }
        pool.add(helmet);

        // 4. สนับแข้งเนเธอไรต์: Protection 20, Swift Sneak 5, Unbreaking 10, Mending
        ItemStack leggings = new ItemStack(Material.NETHERITE_LEGGINGS);
        ItemMeta lm = leggings.getItemMeta();
        if (lm != null) {
            lm.displayName(Component.text("👖 สนับแข้งก้นบึ้งทมิฬ (Abyssal Leggings)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            lm.lore(List.of(
                Component.text("สนับแข้งก้นบึ้ง เคลื่อนไหวดุจสายลมในเงามืด", NamedTextColor.GRAY),
                Component.text("✦ Protection XX (ป้องกันขั้น 20)", NamedTextColor.AQUA),
                Component.text("✦ Swift Sneak V / Unbreaking X", NamedTextColor.GOLD)
            ));
            lm.addEnchant(Enchantment.PROTECTION, 20, true);
            lm.addEnchant(Enchantment.SWIFT_SNEAK, 5, true);
            lm.addEnchant(Enchantment.UNBREAKING, 10, true);
            lm.addEnchant(Enchantment.MENDING, 1, true);
            leggings.setItemMeta(lm);
        }
        pool.add(leggings);

        // 5. รองเท้าเนเธอไรต์: Protection 20, Feather Falling 10, Soul Speed 5, Depth Strider 5, Unbreaking 10
        ItemStack boots = new ItemStack(Material.NETHERITE_BOOTS);
        ItemMeta bm = boots.getItemMeta();
        if (bm != null) {
            bm.displayName(Component.text("👢 รองเท้าท่องความว่างเปล่า (Abyssal Striders)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            bm.lore(List.of(
                Component.text("รองเท้าที่ทำให้คุณวิ่งเหนือผืนทรายวิญญาณและรอดพ้นการตกที่สูง", NamedTextColor.GRAY),
                Component.text("✦ Protection XX (ป้องกันขั้น 20)", NamedTextColor.AQUA),
                Component.text("✦ Feather Falling X / Soul Speed V", NamedTextColor.GOLD)
            ));
            bm.addEnchant(Enchantment.PROTECTION, 20, true);
            bm.addEnchant(Enchantment.FEATHER_FALLING, 10, true);
            bm.addEnchant(Enchantment.SOUL_SPEED, 5, true);
            bm.addEnchant(Enchantment.DEPTH_STRIDER, 5, true);
            bm.addEnchant(Enchantment.UNBREAKING, 10, true);
            bm.addEnchant(Enchantment.MENDING, 1, true);
            boots.setItemMeta(bm);
        }
        pool.add(boots);

        // 6. ที่ขุดเนเธอไรต์: Fortune 10, Efficiency 10, Unbreaking 10, Mending
        ItemStack pickaxe = new ItemStack(Material.NETHERITE_PICKAXE);
        ItemMeta pm = pickaxe.getItemMeta();
        if (pm != null) {
            pm.displayName(Component.text("⛏️ อีเต้อทะลวงมิติ (Abyssal World Breaker)", NamedTextColor.GOLD, TextDecoration.BOLD));
            pm.lore(List.of(
                Component.text("อีเต้อที่ขุดแร่แล้วแตกตัวดั่งฝนดาวตก", NamedTextColor.GRAY),
                Component.text("✦ Fortune X (โชคลาภขั้น 10 - แร่คูณมหาศาล!)", NamedTextColor.YELLOW),
                Component.text("✦ Efficiency X / Unbreaking X", NamedTextColor.AQUA)
            ));
            pm.addEnchant(Enchantment.FORTUNE, 10, true);
            pm.addEnchant(Enchantment.EFFICIENCY, 10, true);
            pm.addEnchant(Enchantment.UNBREAKING, 10, true);
            pm.addEnchant(Enchantment.MENDING, 1, true);
            pickaxe.setItemMeta(pm);
        }
        pool.add(pickaxe);

        // 7. ขวานเนเธอไรต์: Sharpness 15, Efficiency 10, Fortune 5, Unbreaking 10
        ItemStack axe = new ItemStack(Material.NETHERITE_AXE);
        ItemMeta am = axe.getItemMeta();
        if (am != null) {
            am.displayName(Component.text("🪓 ขวานผ่าก้นบึ้ง (Abyssal Cleaver)", NamedTextColor.RED, TextDecoration.BOLD));
            am.lore(List.of(
                Component.text("ขวานศึกทรงพลัง ฟันต้นไม้และผ่ากะโหลกศัตรูในพริบตา", NamedTextColor.GRAY),
                Component.text("✦ Sharpness XV / Efficiency X", NamedTextColor.RED),
                Component.text("✦ Fortune V / Unbreaking X", NamedTextColor.GOLD)
            ));
            am.addEnchant(Enchantment.SHARPNESS, 15, true);
            am.addEnchant(Enchantment.EFFICIENCY, 10, true);
            am.addEnchant(Enchantment.FORTUNE, 5, true);
            am.addEnchant(Enchantment.UNBREAKING, 10, true);
            am.addEnchant(Enchantment.MENDING, 1, true);
            axe.setItemMeta(am);
        }
        pool.add(axe);

        // 8. พลั่วเนเธอไรต์: Efficiency 10, Fortune 5, Unbreaking 10
        ItemStack shovel = new ItemStack(Material.NETHERITE_SHOVEL);
        ItemMeta shm = shovel.getItemMeta();
        if (shm != null) {
            shm.displayName(Component.text("🛠️ พลั่วขุดความว่างเปล่า (Abyssal Excavator)", NamedTextColor.GOLD, TextDecoration.BOLD));
            shm.lore(List.of(
                Component.text("พลั่วขุดเร็วปานสายฟ้าแลบ", NamedTextColor.GRAY),
                Component.text("✦ Efficiency X / Fortune V / Unbreaking X", NamedTextColor.AQUA)
            ));
            shm.addEnchant(Enchantment.EFFICIENCY, 10, true);
            shm.addEnchant(Enchantment.FORTUNE, 5, true);
            shm.addEnchant(Enchantment.UNBREAKING, 10, true);
            shm.addEnchant(Enchantment.MENDING, 1, true);
            shovel.setItemMeta(shm);
        }
        pool.add(shovel);

        return pool;
    }

    public void spawnAscensionRift(Location deathLoc) {
        this.isAscensionRiftActive = true;
        this.ascensionRiftLocation = deathLoc.clone().add(0, 1, 0);

        World world = deathLoc.getWorld();
        if (world == null) return;

        int cx = deathLoc.getBlockX();
        int cy = deathLoc.getBlockY();
        int cz = deathLoc.getBlockZ();

        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                world.getBlockAt(cx + dx, cy, cz + dz).setType(Material.CRYING_OBSIDIAN);
            }
        }
        world.getBlockAt(cx, cy + 1, cz).setType(Material.BEACON);

        new BukkitRunnable() {
            int ticks = 0;
            @Override
            public void run() {
                if (!isAscensionRiftActive || ticks > 7200) {
                    cancel();
                    return;
                }
                ticks += 10;
                world.spawnParticle(Particle.END_ROD, cx + 0.5, cy + 2.0, cz + 0.5, 15, 0.2, 3.0, 0.2, 0.05);
                world.spawnParticle(Particle.TOTEM_OF_UNDYING, cx + 0.5, cy + 1.5, cz + 0.5, 8, 0.4, 0.4, 0.4, 0.05);
            }
        }.runTaskTimer(plugin, 0L, 10L);

        for (Player p : world.getPlayers()) {
            p.sendMessage(Component.text("🌟 เสาแสงแห่งชัยชนะ (Ascension Rift) ปรากฏขึ้นแล้ว ณ จุดบอสตาย! เดินเข้าเสาแสงเพื่อกลับสู่ Overworld อย่างสมเกียรติ!", NamedTextColor.GOLD, TextDecoration.BOLD));
        }
    }

    public void killCurrentBoss() {
        Warden boss = getBoss();
        if (boss != null) {
            boss.setHealth(0.0);
        }
    }

    public void cleanup() {
        if (bossTrackerTask != null) {
            bossTrackerTask.cancel();
        }
        if (bossBar != null) {
            bossBar.removeAll();
            bossBar.setVisible(false);
        }
    }
}
