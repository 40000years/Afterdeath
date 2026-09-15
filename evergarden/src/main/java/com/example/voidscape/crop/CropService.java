package com.example.voidscape.crop;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public final class CropService implements Listener, AutoCloseable {
    private final VoidscapePlugin plugin;
    private final CropItemFactory factory;
    private final Map<String, PlantedCrop> plantedCrops = new ConcurrentHashMap<>();
    private final Map<UUID, PlantedCrop> entityUuidToCrop = new ConcurrentHashMap<>();
    private final NamespacedKey cropEntityKey;
    private final File saveFile;
    private final AtomicBoolean dirty = new AtomicBoolean(false);
    private final AtomicBoolean saving = new AtomicBoolean(false);
    private int tickCounter = 0;

    public CropService(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.factory = new CropItemFactory(plugin);
        this.cropEntityKey = new NamespacedKey("voidscape", "crop_entity");
        this.saveFile = new File(plugin.getDataFolder(), "crops.yml");
        loadCrops();
    }

    public CropItemFactory factory() {
        return factory;
    }

    public Collection<PlantedCrop> getPlantedCrops() {
        return Collections.unmodifiableCollection(plantedCrops.values());
    }

    public PlantedCrop getCropAt(Location loc) {
        if (loc == null || loc.getWorld() == null) return null;
        return plantedCrops.get(locKey(loc));
    }

    public PlantedCrop getCropByEntityUuid(UUID uuid) {
        if (uuid == null) return null;
        return entityUuidToCrop.get(uuid);
    }

    private String locKey(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    private Location parseKey(String key) {
        String[] parts = key.split(",");
        if (parts.length != 4) return null;
        World w = Bukkit.getWorld(parts[0]);
        if (w == null) return null;
        return new Location(w, Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3]));
    }

    public void tick() {
        tickCounter++;
        boolean doAmbient = (tickCounter % 8 == 0); // every 4s (8 * 0.5s)

        for (PlantedCrop crop : plantedCrops.values()) {
            Location loc = crop.getLocation();
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;

            // Check if farmland underneath still exists
            Block soil = loc.subtract(0, 1, 0).getBlock();
            if (soil.getType() != Material.FARMLAND) {
                harvest(crop, null, true);
                continue;
            }

            // Ensure ItemDisplay and Interaction are present
            ItemDisplay display = getOrSpawnDisplay(crop);
            getOrSpawnInteraction(crop);
            if (display == null) continue;

            // Update growth stage
            int targetStage = crop.getStage();
            long elapsed = crop.elapsedSeconds();
            long total = crop.getType().tier.growthSeconds;

            if (elapsed >= total) {
                targetStage = 2;
            } else if (elapsed >= total / 3) {
                targetStage = 1;
            } else {
                targetStage = 0;
            }

            if (targetStage > crop.getStage()) {
                advanceStage(crop, targetStage, display);
            } else if (crop.isMature() && doAmbient) {
                spawnAmbientParticles(crop);
            }
        }

        // Periodic async autosave every 60s (120 ticks * 0.5s) if modified
        if (tickCounter % 120 == 0 && dirty.get()) {
            saveCropsAsync();
        }
    }

    private void advanceStage(PlantedCrop crop, int newStage, ItemDisplay display) {
        crop.setStage(newStage);
        if (display != null && display.isValid()) {
            display.setItemStack(factory.createPlantDisplay(crop.getType(), newStage));
        }

        Location loc = crop.getLocation().add(0.5, 0.5, 0.5);
        loc.getWorld().playSound(loc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.3f);

        if (newStage == 2) {
            spawnMatureParticles(crop);
        } else {
            loc.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, loc, 12, 0.3, 0.3, 0.3, 0.05);
        }
        dirty.set(true);
    }

    private void spawnMatureParticles(PlantedCrop crop) {
        Location loc = crop.getLocation().add(0.5, 0.6, 0.5);
        World w = loc.getWorld();
        switch (crop.getType().tier) {
            case TIER_1 -> {
                w.spawnParticle(Particle.HAPPY_VILLAGER, loc, 25, 0.4, 0.4, 0.4, 0.08);
                w.spawnParticle(Particle.COMPOSTER, loc, 15, 0.3, 0.3, 0.3, 0.05);
            }
            case TIER_2 -> {
                w.spawnParticle(Particle.CRIT, loc, 25, 0.4, 0.4, 0.4, 0.15);
                w.spawnParticle(Particle.FLAME, loc, 15, 0.3, 0.3, 0.3, 0.03);
            }
            case TIER_3 -> {
                w.spawnParticle(Particle.PORTAL, loc, 35, 0.5, 0.5, 0.5, 0.2);
                w.spawnParticle(Particle.WITCH, loc, 15, 0.3, 0.3, 0.3, 0.05);
            }
            case TIER_4 -> {
                w.spawnParticle(Particle.SCRAPE, loc, 20, 0.4, 0.4, 0.4, 0.1);
                w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 20, 0.4, 0.4, 0.4, 0.15);
            }
            case TIER_5 -> {
                w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 0.5, 0.6, 0.5, 0.25);
                w.spawnParticle(Particle.END_ROD, loc, 20, 0.3, 0.5, 0.3, 0.05);
            }
        }
    }

    private void spawnAmbientParticles(PlantedCrop crop) {
        Location loc = crop.getLocation().add(0.5, 0.6, 0.5);
        World w = loc.getWorld();
        switch (crop.getType().tier) {
            case TIER_1 -> w.spawnParticle(Particle.HAPPY_VILLAGER, loc, 2, 0.2, 0.2, 0.2, 0.02);
            case TIER_2 -> w.spawnParticle(Particle.CRIT, loc, 3, 0.2, 0.2, 0.2, 0.05);
            case TIER_3 -> w.spawnParticle(Particle.PORTAL, loc, 4, 0.2, 0.2, 0.2, 0.08);
            case TIER_4 -> w.spawnParticle(Particle.ELECTRIC_SPARK, loc, 3, 0.2, 0.2, 0.2, 0.05);
            case TIER_5 -> {
                w.spawnParticle(Particle.END_ROD, loc, 3, 0.2, 0.3, 0.2, 0.02);
                w.spawnParticle(Particle.SOUL_FIRE_FLAME, loc, 2, 0.2, 0.2, 0.2, 0.02);
            }
        }
    }

    private ItemDisplay getOrSpawnDisplay(PlantedCrop crop) {
        Location loc = crop.getLocation();
        if (crop.getItemDisplayUuid() != null) {
            Entity ent = Bukkit.getEntity(crop.getItemDisplayUuid());
            if (ent instanceof ItemDisplay id && ent.isValid()) {
                return id;
            }
        }

        Location center = loc.add(0.5, 0.45, 0.5);
        for (Entity nearby : loc.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (nearby instanceof ItemDisplay id && nearby.getPersistentDataContainer().has(cropEntityKey, PersistentDataType.STRING)) {
                crop.setItemDisplayUuid(id.getUniqueId());
                entityUuidToCrop.put(id.getUniqueId(), crop);
                id.setItemStack(factory.createPlantDisplay(crop.getType(), crop.getStage()));
                dirty.set(true);
                return id;
            }
        }

        ItemDisplay display = loc.getWorld().spawn(center, ItemDisplay.class, d -> {
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
            d.setItemStack(factory.createPlantDisplay(crop.getType(), crop.getStage()));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(64f);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(cropEntityKey, PersistentDataType.STRING, locKey(crop.getLocation()));
        });
        crop.setItemDisplayUuid(display.getUniqueId());
        entityUuidToCrop.put(display.getUniqueId(), crop);
        dirty.set(true);
        return display;
    }

    private Interaction getOrSpawnInteraction(PlantedCrop crop) {
        Location loc = crop.getLocation();
        if (crop.getInteractionUuid() != null) {
            Entity ent = Bukkit.getEntity(crop.getInteractionUuid());
            if (ent instanceof Interaction it && ent.isValid()) {
                return it;
            }
        }

        Location center = loc.add(0.5, 0.0, 0.5);
        for (Entity nearby : loc.getWorld().getNearbyEntities(center, 0.8, 0.8, 0.8)) {
            if (nearby instanceof Interaction it && nearby.getPersistentDataContainer().has(cropEntityKey, PersistentDataType.STRING)) {
                crop.setInteractionUuid(it.getUniqueId());
                entityUuidToCrop.put(it.getUniqueId(), crop);
                dirty.set(true);
                return it;
            }
        }

        Interaction it = loc.getWorld().spawn(center, Interaction.class, i -> {
            i.setInteractionWidth(0.8f);
            i.setInteractionHeight(0.9f);
            i.setResponsive(true);
            i.setPersistent(true);
            i.getPersistentDataContainer().set(cropEntityKey, PersistentDataType.STRING, locKey(crop.getLocation()));
        });
        crop.setInteractionUuid(it.getUniqueId());
        entityUuidToCrop.put(it.getUniqueId(), crop);
        dirty.set(true);
        return it;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlant(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        // Block bone meal if clicking farmland or crop
        ItemStack hand = e.getItem();
        if (hand != null && hand.getType() == Material.BONE_MEAL) {
            Block cropBlock = clicked.getType() == Material.FARMLAND ? clicked.getRelative(BlockFace.UP) : clicked;
            if (getCropAt(cropBlock.getLocation()) != null) {
                e.setCancelled(true);
                e.getPlayer().getWorld().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                e.getPlayer().sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal ได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
                return;
            }
        }

        if (clicked.getType() != Material.FARMLAND) return;
        if (e.getBlockFace() != BlockFace.UP) return;

        CropType cropType = factory.getSeedType(hand);
        if (cropType == null) return;

        Block above = clicked.getRelative(BlockFace.UP);
        if (above.getType() != Material.AIR && above.getType() != Material.CAVE_AIR && above.getType() != Material.VOID_AIR) {
            return;
        }

        if (plantedCrops.containsKey(locKey(above.getLocation()))) return;

        Player p = e.getPlayer();
        org.bukkit.event.block.BlockPlaceEvent placeEvent = new org.bukkit.event.block.BlockPlaceEvent(above, above.getState(), clicked, hand, p, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled() || !placeEvent.canBuild()) {
            return;
        }

        e.setCancelled(true);

        // Leave block above as AIR (NO tripwire string!)
        above.setType(Material.AIR, false);

        // Spawn ItemDisplay
        Location displayLoc = above.getLocation().add(0.5, 0.45, 0.5);
        ItemDisplay display = above.getWorld().spawn(displayLoc, ItemDisplay.class, d -> {
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setTransformation(new Transformation(new Vector3f(0, 0, 0), new AxisAngle4f(), new Vector3f(1.0f, 1.0f, 1.0f), new AxisAngle4f()));
            d.setItemStack(factory.createPlantDisplay(cropType, 0));
            d.setBrightness(new Display.Brightness(15, 15));
            d.setViewRange(64f);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(cropEntityKey, PersistentDataType.STRING, locKey(above.getLocation()));
        });

        // Spawn Interaction hitbox
        Location interactLoc = above.getLocation().add(0.5, 0.0, 0.5);
        Interaction interaction = above.getWorld().spawn(interactLoc, Interaction.class, i -> {
            i.setInteractionWidth(0.8f);
            i.setInteractionHeight(0.9f);
            i.setResponsive(true);
            i.setPersistent(true);
            i.getPersistentDataContainer().set(cropEntityKey, PersistentDataType.STRING, locKey(above.getLocation()));
        });

        PlantedCrop crop = new PlantedCrop(above.getLocation(), cropType, 0, System.currentTimeMillis(), display.getUniqueId(), interaction.getUniqueId());
        plantedCrops.put(locKey(above.getLocation()), crop);
        entityUuidToCrop.put(display.getUniqueId(), crop);
        entityUuidToCrop.put(interaction.getUniqueId(), crop);

        if (p.getGameMode() != GameMode.CREATIVE) {
            hand.setAmount(hand.getAmount() - 1);
        }

        above.getWorld().playSound(displayLoc, Sound.ITEM_CROP_PLANT, 1.0f, 1.1f);
        above.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, displayLoc, 10, 0.3, 0.3, 0.3, 0.05);

        p.sendActionBar(Component.text("🌱 ปลูก " + cropType.thaiName + " สำเร็จ! (โตเต็มที่ใน " + cropType.tier.formattedTime() + ")", NamedTextColor.GREEN));
        dirty.set(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractFarmland(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;

        Block clicked = e.getClickedBlock();
        if (clicked == null) return;

        Block cropBlock = clicked.getType() == Material.FARMLAND ? clicked.getRelative(BlockFace.UP) : clicked;
        PlantedCrop crop = getCropAt(cropBlock.getLocation());
        if (crop == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemStack hand = e.getItem();

        // Bone Meal is completely disabled!
        if (hand != null && hand.getType() == Material.BONE_MEAL) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            p.sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal ได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
            return;
        }

        if (crop.isMature()) {
            harvest(crop, p, false);
        } else {
            p.sendActionBar(Component.text("⏳ " + crop.getType().thaiName + " กำลังเติบโต (" + (int)(crop.growthProgress() * 100) + "% · เหลือ " + crop.secondsRemaining() + " วินาที)", NamedTextColor.YELLOW));
            cropBlock.getWorld().playSound(cropBlock.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_HIT, 0.6f, 1.5f);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractAtEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity target = e.getRightClicked();
        PlantedCrop found = entityUuidToCrop.get(target.getUniqueId());
        if (found == null) return;

        e.setCancelled(true);
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItem(e.getHand());

        // Bone Meal is completely disabled!
        if (hand != null && hand.getType() == Material.BONE_MEAL) {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
            p.sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal ได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
            return;
        }

        if (found.isMature()) {
            harvest(found, p, false);
        } else {
            p.sendActionBar(Component.text("⏳ " + found.getType().thaiName + " กำลังเติบโต (" + (int)(found.growthProgress() * 100) + "% · เหลือ " + found.secondsRemaining() + " วินาที)", NamedTextColor.YELLOW));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamageCrop(EntityDamageByEntityEvent e) {
        Entity target = e.getEntity();
        if (!(target instanceof Interaction) && !(target instanceof ItemDisplay)) return;

        PlantedCrop found = entityUuidToCrop.get(target.getUniqueId());
        if (found == null) return;

        e.setCancelled(true);
        Player p = e.getDamager() instanceof Player pl ? pl : null;
        harvest(found, p, true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.FARMLAND) {
            PlantedCrop cropAbove = getCropAt(b.getRelative(BlockFace.UP).getLocation());
            if (cropAbove != null) {
                harvest(cropAbove, e.getPlayer(), true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandTrample(PlayerInteractEvent e) {
        if (e.getAction() != Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.FARMLAND) return;
        if (getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmlandFade(BlockFadeEvent e) {
        Block b = e.getBlock();
        if (b.getType() == Material.FARMLAND && getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMoistureChange(MoistureChangeEvent e) {
        Block b = e.getBlock();
        if (getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent e) {
        Block b = e.getBlock();
        if (plugin.world() != null && b.getWorld().equals(plugin.world())) {
            e.setCancelled(true);
            if (e.getPlayer() != null) {
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                e.getPlayer().sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal หรือปุ๋ยได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
            }
            return;
        }
        if (getCropAt(b.getLocation()) != null || getCropAt(b.getRelative(BlockFace.UP).getLocation()) != null) {
            e.setCancelled(true);
            if (e.getPlayer() != null) {
                e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1.0f, 1.0f);
                e.getPlayer().sendActionBar(Component.text("✦ พืชเวทมนตร์ Evergarden ไม่สามารถเร่งโตด้วย Bone Meal หรือปุ๋ยได้ (ต้องเติบโตตามกาลเวลา)", NamedTextColor.RED));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent e) {
        if (e.getItem() != null && e.getItem().getType() == Material.BONE_MEAL) {
            if (plugin.world() != null && e.getBlock().getWorld().equals(plugin.world())) {
                e.setCancelled(true);
                return;
            }
            if (e.getBlock().getBlockData() instanceof org.bukkit.block.data.Directional dir) {
                Block target = e.getBlock().getRelative(dir.getFacing());
                if (getCropAt(target.getLocation()) != null || getCropAt(target.getRelative(BlockFace.UP).getLocation()) != null) {
                    e.setCancelled(true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreakWildFlora(BlockBreakEvent e) {
        Block b = e.getBlock();
        if (b.getWorld() != plugin.world()) return;
        Player player = e.getPlayer();
        if (player.getGameMode() != GameMode.SURVIVAL) return;

        Material mat = b.getType();
        boolean isFlora = mat == Material.SHORT_GRASS || mat == Material.TALL_GRASS
            || mat == Material.FERN || mat == Material.LARGE_FERN
            || mat == Material.PINK_PETALS || Tag.FLOWERS.isTagged(mat);

        if (isFlora) {
            double roll = java.util.concurrent.ThreadLocalRandom.current().nextDouble();
            Location dropLoc = b.getLocation().add(0.5, 0.3, 0.5);

            // 12% chance for Tier 1 seed
            if (roll < 0.12 && factory != null) {
                CropType[] tier1 = Arrays.stream(CropType.values())
                    .filter(c -> c.tier == CropTier.TIER_1)
                    .toArray(CropType[]::new);
                CropType picked = tier1[java.util.concurrent.ThreadLocalRandom.current().nextInt(tier1.length)];
                ItemStack seed = factory.createSeed(picked, 1);
                b.getWorld().dropItemNaturally(dropLoc, seed);
                b.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, dropLoc, 8, 0.3, 0.3, 0.3, 0.05);
                player.playSound(dropLoc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.4f);
                player.sendActionBar(Component.text("🌿 ค้นพบเมล็ดพันธุ์ลอยฟ้า: " + picked.thaiName + "!", NamedTextColor.GREEN));
            }
            // 8% chance for Astral Dust
            else if (roll < 0.20 && plugin.relics() != null) {
                ItemStack dust = plugin.relics().createAstralDust(1);
                b.getWorld().dropItemNaturally(dropLoc, dust);
                b.getWorld().spawnParticle(Particle.FIREWORK, dropLoc, 6, 0.2, 0.2, 0.2, 0.05);
                player.playSound(dropLoc, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.8f);
                player.sendActionBar(Component.text("✦ ค้นพบละอองดาว (Astral Dust) ในพุ่มพฤกษา!", NamedTextColor.AQUA));
            }
        }
    }

    // Protection against liquids, pistons, and explosions
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFluidFlow(BlockFromToEvent e) {
        Block to = e.getToBlock();
        PlantedCrop crop = getCropAt(to.getLocation());
        if (crop != null) {
            harvest(crop, null, true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        handlePiston(e.getBlocks());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        handlePiston(e.getBlocks());
    }

    private void handlePiston(List<Block> blocks) {
        for (Block b : blocks) {
            PlantedCrop crop = getCropAt(b.getLocation());
            if (crop != null) {
                harvest(crop, null, true);
            }
            if (b.getType() == Material.FARMLAND) {
                PlantedCrop cropAbove = getCropAt(b.getRelative(BlockFace.UP).getLocation());
                if (cropAbove != null) {
                    harvest(cropAbove, null, true);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        handleExplosion(e.blockList());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        handleExplosion(e.blockList());
    }

    private void handleExplosion(List<Block> blocks) {
        for (Block b : blocks) {
            PlantedCrop crop = getCropAt(b.getLocation());
            if (crop != null) {
                harvest(crop, null, true);
            }
            if (b.getType() == Material.FARMLAND) {
                PlantedCrop cropAbove = getCropAt(b.getRelative(BlockFace.UP).getLocation());
                if (cropAbove != null) {
                    harvest(cropAbove, null, true);
                }
            }
        }
    }

    public void harvest(PlantedCrop crop, Player player, boolean isBreak) {
        if (player != null) {
            org.bukkit.event.block.BlockBreakEvent breakEvent = new org.bukkit.event.block.BlockBreakEvent(crop.getLocation().getBlock(), player);
            Bukkit.getPluginManager().callEvent(breakEvent);
            if (breakEvent.isCancelled()) return;
        }

        Location loc = crop.getLocation();
        World w = loc.getWorld();
        Location dropLoc = loc.add(0.5, 0.3, 0.5);

        // Hoe handling
        ItemStack tool = player != null ? player.getInventory().getItemInMainHand() : null;
        boolean isHoe = tool != null && (tool.getType().name().endsWith("_HOE") || Tag.ITEMS_HOES.isTagged(tool.getType()));

        if (isHoe && player != null && player.getGameMode() != GameMode.CREATIVE) {
            if (tool.getItemMeta() instanceof Damageable d) {
                d.setDamage(d.getDamage() + 1);
                tool.setItemMeta(d);
            }
            w.playSound(dropLoc, Sound.ITEM_HOE_TILL, 0.9f, 1.2f);
        }

        if (crop.isMature()) {
            int fortune = 0;
            if (tool != null) {
                fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE);
            }

            int foodCount = 1 + (Math.random() < (0.4 + fortune * 0.15) ? 1 : 0);
            ItemStack food = factory.createFood(crop.getType(), foodCount);
            w.dropItemNaturally(dropLoc, food);

            int seedCount = 1 + (Math.random() < (0.3 + fortune * 0.1) ? 1 : 0);
            ItemStack seed = factory.createSeed(crop.getType(), seedCount);
            w.dropItemNaturally(dropLoc, seed);

            w.playSound(dropLoc, Sound.BLOCK_SWEET_BERRY_BUSH_PICK_BERRIES, 1.0f, 1.2f);
            w.playSound(dropLoc, Sound.ENTITY_ITEM_PICKUP, 0.6f, 1.4f);
            spawnMatureParticles(crop);

            if (player != null) {
                player.sendActionBar(Component.text("🌾 เก็บเกี่ยว " + crop.getType().thaiName + " ได้รับ x" + foodCount + " ชิ้น!", NamedTextColor.GREEN));
            }

            if (!isBreak) {
                // Auto-replant at Stage 0 with fresh timestamp!
                crop.setStage(0);
                crop.setPlantedAt(System.currentTimeMillis());
                ItemDisplay display = getOrSpawnDisplay(crop);
                if (display != null) {
                    display.setItemStack(factory.createPlantDisplay(crop.getType(), 0));
                }
                dirty.set(true);
                return;
            }
        } else {
            // Unripe break
            ItemStack seed = factory.createSeed(crop.getType(), 1);
            w.dropItemNaturally(dropLoc, seed);
            w.playSound(dropLoc, Sound.BLOCK_CROP_BREAK, 1.0f, 0.9f);
            if (player != null) {
                player.sendActionBar(Component.text("พืชยังไม่โตเต็มที่ (ได้รับเมล็ดคืน)", NamedTextColor.GRAY));
            }
        }

        // Cleanup entities and mapping
        removeEntities(crop);
        loc.getBlock().setType(Material.AIR, false);
        plantedCrops.remove(locKey(loc));
        dirty.set(true);
    }

    private void removeEntities(PlantedCrop crop) {
        if (crop.getItemDisplayUuid() != null) {
            entityUuidToCrop.remove(crop.getItemDisplayUuid());
            Entity ent = Bukkit.getEntity(crop.getItemDisplayUuid());
            if (ent != null) ent.remove();
        }
        if (crop.getInteractionUuid() != null) {
            entityUuidToCrop.remove(crop.getInteractionUuid());
            Entity ent = Bukkit.getEntity(crop.getInteractionUuid());
            if (ent != null) ent.remove();
        }
        Location center = crop.getLocation().add(0.5, 0.45, 0.5);
        for (Entity nearby : crop.getLocation().getWorld().getNearbyEntities(center, 0.9, 0.9, 0.9)) {
            if ((nearby instanceof ItemDisplay || nearby instanceof Interaction) &&
                nearby.getPersistentDataContainer().has(cropEntityKey, PersistentDataType.STRING)) {
                nearby.remove();
            }
        }
    }

    public void markDirty() {
        dirty.set(true);
    }

    public void saveCropsAsync() {
        if (!dirty.compareAndSet(true, false)) return;
        if (saving.get()) {
            dirty.set(true);
            return;
        }
        saving.set(true);
        Map<String, PlantedCrop> snapshot = new HashMap<>(plantedCrops);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                writeCropsYaml(snapshot);
            } finally {
                saving.set(false);
            }
        });
    }

    public void saveCropsSync() {
        writeCropsYaml(new HashMap<>(plantedCrops));
        dirty.set(false);
    }

    public void saveCrops() {
        saveCropsSync();
    }

    private void writeCropsYaml(Map<String, PlantedCrop> map) {
        try {
            YamlConfiguration cfg = new YamlConfiguration();
            for (Map.Entry<String, PlantedCrop> entry : map.entrySet()) {
                String key = entry.getKey();
                PlantedCrop c = entry.getValue();
                cfg.set(key + ".type", c.getType().id);
                cfg.set(key + ".stage", c.getStage());
                cfg.set(key + ".plantedAt", c.getPlantedAt());
                if (c.getItemDisplayUuid() != null) {
                    cfg.set(key + ".display", c.getItemDisplayUuid().toString());
                }
                if (c.getInteractionUuid() != null) {
                    cfg.set(key + ".interaction", c.getInteractionUuid().toString());
                }
            }
            File tmp = new File(saveFile.getParentFile(), "crops.yml.tmp");
            cfg.save(tmp);
            if (tmp.exists()) {
                if (saveFile.exists()) saveFile.delete();
                tmp.renameTo(saveFile);
            }
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to save crops.yml: " + e.getMessage());
        }
    }

    public void loadCrops() {
        plantedCrops.clear();
        entityUuidToCrop.clear();
        if (!saveFile.exists()) return;
        try {
            YamlConfiguration cfg = YamlConfiguration.loadConfiguration(saveFile);
            for (String key : cfg.getKeys(false)) {
                Location loc = parseKey(key);
                if (loc == null) continue;
                String typeId = cfg.getString(key + ".type");
                CropType type = CropType.fromId(typeId);
                if (type == null) continue;

                int stage = cfg.getInt(key + ".stage", 0);
                long plantedAt = cfg.getLong(key + ".plantedAt", System.currentTimeMillis());
                String dUuidStr = cfg.getString(key + ".display");
                UUID displayUuid = dUuidStr != null ? UUID.fromString(dUuidStr) : null;
                String iUuidStr = cfg.getString(key + ".interaction");
                UUID interactUuid = iUuidStr != null ? UUID.fromString(iUuidStr) : null;

                PlantedCrop crop = new PlantedCrop(loc, type, stage, plantedAt, displayUuid, interactUuid);
                plantedCrops.put(key, crop);
                if (displayUuid != null) entityUuidToCrop.put(displayUuid, crop);
                if (interactUuid != null) entityUuidToCrop.put(interactUuid, crop);
            }
            plugin.getLogger().info("Loaded " + plantedCrops.size() + " planted crops from crops.yml");
        } catch (Exception e) {
            plugin.getLogger().warning("Failed to load crops.yml: " + e.getMessage());
        }
    }

    @Override
    public void close() {
        if (dirty.get()) {
            saveCropsSync();
        }
    }
}
