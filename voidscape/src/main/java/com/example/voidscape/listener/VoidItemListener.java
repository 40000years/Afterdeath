package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.VoidItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class VoidItemListener implements Listener {

    private final VoidscapePlugin plugin;
    private final VoidItemManager itemManager;
    private final Map<UUID, Long> dashCooldowns = new HashMap<>();

    public VoidItemListener(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    // 1. ดรอปผลึก Voidic Crystal / Echo Shard และ Null Fruit จากมอนสเตอร์แห่งความมืด
    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.getPersistentDataContainer().has(plugin.getKeyShadowStalker(), PersistentDataType.BYTE)) {
            event.getDrops().clear();

            // โอกาส 70% ดรอป Echo Shard (Voidic Crystal) 1-2 ชิ้น สำหรับใช้เบิกเนตร
            if (ThreadLocalRandom.current().nextDouble() < 0.70) {
                int count = 1 + (ThreadLocalRandom.current().nextDouble() < 0.35 ? 1 : 0);
                event.getDrops().add(itemManager.createVoidCrystal(count));
            }

            // โอกาส 35% ดรอปผลไม้ Null Fruit
            if (ThreadLocalRandom.current().nextDouble() < 0.35) {
                event.getDrops().add(itemManager.createNullFruit(1));
            }
        }
    }

    // 2. กินผลไม้ Null Fruit ดึงหลอดเลือดสูงสุดกลับมา และฉีกมิติวาร์ปหนีออกจาก The Void
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!itemManager.isVoidItem(item, "NULL_FRUIT")) return;

        Player player = event.getPlayer();
        AttributeInstance attr = player.getAttribute(Attribute.MAX_HEALTH);
        if (attr != null) {
            double currentMax = attr.getBaseValue();
            double restored = Math.min(20.0, currentMax + 4.0); // คืนทีละ 2 หัวใจ
            attr.setBaseValue(restored);
        }

        // ถ้ากินขณะที่ติดอยู่ในมิติ The Void -> ฉีกมิติวาร์ปหนีกลับสู่ Overworld ทันที!
        if (player.getWorld().getName().equals(plugin.getVoidWorldName())) {
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.setFallDistance(0.0f);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));

            // อนุญาตให้ผ่านระบบ Lockdown
            player.setMetadata("void_authorized_escape", new org.bukkit.metadata.FixedMetadataValue(plugin, true));

            org.bukkit.World overworld = Bukkit.getWorlds().get(0);
            Location returnLoc = player.getRespawnLocation();
            if (returnLoc == null || returnLoc.getWorld() == null) {
                returnLoc = overworld.getSpawnLocation();
            }

            player.teleport(returnLoc);
            player.setFallDistance(0.0f);
            player.setVelocity(new org.bukkit.util.Vector(0, 0, 0));
            if (plugin.getEjectionListener() != null) {
                plugin.getEjectionListener().grantFallImmunity(player.getUniqueId(), 10000L);
            }

            // เอฟเฟกต์ประตูมิติแตกออก
            player.playSound(returnLoc, Sound.ITEM_CHORUS_FRUIT_TELEPORT, 1.2f, 0.8f);
            player.playSound(returnLoc, Sound.BLOCK_PORTAL_TRAVEL, 0.8f, 1.2f);
            player.getWorld().spawnParticle(Particle.PORTAL, returnLoc.clone().add(0, 1, 0), 40, 0.5, 0.5, 0.5, 0.1);

            Title title = Title.title(
                Component.text("✦ ESCAPED THE VOID ✦", NamedTextColor.LIGHT_PURPLE),
                Component.text("พลังของ Null Fruit ได้ฉีกมิตินำคุณกลับสู่ Overworld ปลอดภัย!", NamedTextColor.GREEN),
                Title.Times.times(Duration.ofMillis(300), Duration.ofSeconds(3), Duration.ofSeconds(1))
            );
            player.showTitle(title);
            player.sendMessage(Component.text("🌌 คุณได้กิน Null Fruit ฉีกมิติหลบหนีออกจาก The Void กลับสู่บ้านเรียบร้อยแล้ว!", NamedTextColor.LIGHT_PURPLE));
        } else {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.5f);
            player.getWorld().spawnParticle(Particle.HEART, player.getLocation().add(0, 1.5, 0), 8, 0.3, 0.3, 0.3);
            player.sendMessage(Component.text("✔ พลังของ Null Fruit ฟื้นฟูหลอดเลือดของคุณให้สมบูรณ์ขึ้น", NamedTextColor.GREEN));
        }
    }

    // 3. คลิกขวาใช้งานไอเทมพิเศษ (Echo Shard / Pocket Void / Shadow Blade / Chests)
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;

        Player player = event.getPlayer();

        // 3.0 เปิดหีบสมบัติโบราณใน The Void ครั้งแรก -> เติมไอเทมสุ่มให้อัตโนมัติ
        if (event.getAction() == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            if (event.getClickedBlock().getType() == Material.CHEST && player.getWorld().getName().equals(plugin.getVoidWorldName())) {
                if (event.getClickedBlock().getState() instanceof org.bukkit.block.Chest chest) {
                    if (chest.getInventory().isEmpty()) {
                        populateVoidChest(chest.getInventory());
                        player.playSound(event.getClickedBlock().getLocation(), Sound.BLOCK_CHEST_OPEN, 1.0f, 0.8f);
                        player.sendMessage(Component.text("✨ คุณค้นพบหีบสมบัติโบราณแห่งมิติมืด!", NamedTextColor.GOLD));
                    }
                }
            }
        }

        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = event.getItem();
        if (item == null) return;

        // 3.1 เบิกเนตรแห่งความมืด (Echo Shard / Voidic Crystal -> Void Sight 60 วินาที)
        if (item.getType() == Material.ECHO_SHARD) {
            event.setCancelled(true);
            if (player.getGameMode() != org.bukkit.GameMode.CREATIVE) {
                item.subtract(1);
            }

            itemManager.grantVoidSight(player, 60000L);

            player.removePotionEffect(PotionEffectType.BLINDNESS);
            player.removePotionEffect(PotionEffectType.DARKNESS);
            player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 1200, 0, false, false, true));

            Location loc = player.getLocation();
            player.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.8f, 1.2f);
            player.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 2.0f, 0.8f);
            player.getWorld().playSound(loc, Sound.ENTITY_WARDEN_HEARTBEAT, 1.5f, 1.4f);
            player.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0, 1, 0), 25, 0.5, 0.5, 0.5, 0.05);

            player.sendActionBar(Component.text("👁️ เบิกเนตรแห่งความมืด (Void Sight) ทำงาน! มองเห็นสว่างชัดเจน 60 วินาที", NamedTextColor.AQUA));
            player.sendMessage(Component.text("👁️ คุณใช้ Echo Shard เบิกเนตรแห่งความมืด! ต้านทานหมอกมืดและมองเห็นชัดเจนเป็นเวลา 1 นาที", NamedTextColor.AQUA, TextDecoration.BOLD));
            return;
        }

        // 3.2 กระเป๋าหลุมดำ (Pocket Void)
        if (itemManager.isVoidItem(item, "POCKET_VOID")) {
            event.setCancelled(true);
            player.playSound(player.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1.0f, 0.9f);
            player.openInventory(player.getEnderChest());
            return;
        }

        // 3.3 ดาบเงาทมิฬ (Shadow Blade - Shadow Dash)
        if (itemManager.isVoidItem(item, "SHADOW_BLADE")) {
            event.setCancelled(true);
            handleShadowDash(player);
        }
    }

    private void populateVoidChest(org.bukkit.inventory.Inventory inv) {
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        inv.clear();

        // คลังแร่เล็กๆ น้อยๆ สำหรับกล่องสมบัติโบราณใน The Void
        List<ItemStack> minorOrePool = new ArrayList<>();
        minorOrePool.add(new ItemStack(Material.COAL, 1 + rnd.nextInt(4)));
        minorOrePool.add(new ItemStack(Material.IRON_NUGGET, 2 + rnd.nextInt(6)));
        minorOrePool.add(new ItemStack(Material.RAW_IRON, 1 + rnd.nextInt(2)));
        minorOrePool.add(new ItemStack(Material.COPPER_INGOT, 1 + rnd.nextInt(3)));
        minorOrePool.add(new ItemStack(Material.GOLD_NUGGET, 2 + rnd.nextInt(5)));
        minorOrePool.add(new ItemStack(Material.LAPIS_LAZULI, 1 + rnd.nextInt(3)));
        minorOrePool.add(new ItemStack(Material.AMETHYST_SHARD, 1 + rnd.nextInt(2)));

        // โอกาสเล็กน้อย 12% ได้ Echo Shard (Voidic Crystal) 1 ชิ้น สำหรับเบิกเนตร
        if (rnd.nextDouble() < 0.12) {
            minorOrePool.add(itemManager.createVoidCrystal(1));
        }

        // โอกาสเล็กน้อย 6% ได้ผลไม้ Null Fruit 1 ชิ้น
        if (rnd.nextDouble() < 0.06) {
            minorOrePool.add(itemManager.createNullFruit(1));
        }

        // สุ่มหยิบแร่เล็กๆ 2-4 กอง แล้ววางสุ่มตามช่องในหีบ
        int minItems = plugin.getConfig().getInt("chests.min-items", 2);
        int maxItems = plugin.getConfig().getInt("chests.max-items", 4);
        int totalItems = minItems + rnd.nextInt(Math.max(1, maxItems - minItems + 1));

        Collections.shuffle(minorOrePool);
        int chestSize = inv.getSize();
        for (int i = 0; i < totalItems && i < minorOrePool.size(); i++) {
            int slot = rnd.nextInt(chestSize);
            if (inv.getItem(slot) == null) {
                inv.setItem(slot, minorOrePool.get(i));
            } else {
                inv.addItem(minorOrePool.get(i));
            }
        }
    }

    private void handleShadowDash(Player player) {
        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();
        long lastUsed = dashCooldowns.getOrDefault(uuid, 0L);
        long cooldownMs = 6000L; // 6 วินาที

        if (now - lastUsed < cooldownMs) {
            long remainingSec = (cooldownMs - (now - lastUsed)) / 1000L + 1;
            player.sendActionBar(Component.text(
                String.format("⚡ Shadow Dash กำลังติดคูลดาวน์ (%d วิ)", remainingSec),
                NamedTextColor.RED
            ));
            return;
        }

        dashCooldowns.put(uuid, now);

        Location start = player.getLocation();
        Vector dir = start.getDirection().normalize();

        // พุ่งไปข้างหน้า 8 บล็อก
        Location target = start.clone().add(dir.clone().multiply(8.0));
        target.setY(start.getY()); // รักษาระดับความสูงเพื่อไม่ให้พุ่งมุดดิน

        // วาดเอฟเฟกต์หมอกควันตามทางพุ่ง
        for (double d = 0; d < 8.0; d += 1.0) {
            Location pLoc = start.clone().add(dir.clone().multiply(d));
            player.getWorld().spawnParticle(Particle.LARGE_SMOKE, pLoc.add(0, 1, 0), 4, 0.1, 0.1, 0.1, 0.02);
        }

        player.teleport(target);
        player.playSound(target, Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
        player.playSound(target, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.0f, 0.8f);

        // ฟันศัตรูรอบๆ จุดลงถึง 4 บล็อก
        for (LivingEntity nearby : target.getNearbyLivingEntities(4.0)) {
            if (nearby.equals(player)) continue;
            nearby.damage(10.0, player);
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 60, 0));
            nearby.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 60, 0));
        }

        player.sendActionBar(Component.text("⚡ Shadow Dash!", NamedTextColor.GOLD));
    }

    // 4. บัฟ Voidic Armor (ป้องกันการตก Void ในโลก End / Overworld)
    @EventHandler(priority = EventPriority.HIGH)
    public void onVoidArmorProtection(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        if (event.getCause() == EntityDamageEvent.DamageCause.VOID) {
            // ไม่ทำงานใน The Void (เพราะมีระบบ Ejection แยกอยู่แล้ว)
            if (player.getWorld().getName().equals(plugin.getVoidWorldName())) return;

            // ตรวจสอบว่าใส่ชิ้นส่วนเกราะ Voidic หรือไม่
            boolean hasVoidArmor = false;
            for (ItemStack piece : player.getInventory().getArmorContents()) {
                if (piece != null && itemManager.isVoidItem(piece, "VOID_ARMOR")) {
                    hasVoidArmor = true;
                    break;
                }
            }

            if (hasVoidArmor) {
                event.setCancelled(true);
                // ดีดผู้เล่นวาร์ปกลับขึ้นมาบนจุดเกิดหรือบล็อกสูงสุด
                Location safe = player.getWorld().getSpawnLocation();
                player.setFallDistance(0.0f);
                player.teleport(safe);
                player.setFallDistance(0.0f);
                player.setVelocity(new Vector(0, 0, 0));
                if (plugin.getEjectionListener() != null) {
                    plugin.getEjectionListener().grantFallImmunity(player.getUniqueId(), 10000L);
                }
                player.playSound(safe, Sound.ITEM_TOTEM_USE, 1.0f, 1.0f);
                player.sendMessage(Component.text("🛡 เกราะ Voidic Armor ช่วยชีวิตคุณจากการตกเหว Void เอาไว้ได้!", NamedTextColor.GOLD));
            }
        }
    }
}
