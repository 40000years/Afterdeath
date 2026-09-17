package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import java.io.File;
import java.util.*;

/** Persistent custom portal cells; no vanilla portal texture overrides. */
public final class PortalVisuals {
    private final VoidscapePlugin plugin;
    private final Map<String, Axis> cells = new HashMap<>();
    private final Map<String, UUID> displays = new HashMap<>();
    private final File file;
    private final NamespacedKey rendererRevision;
    private final Random random = new Random();
    private final Particle.DustOptions cyanDust = new Particle.DustOptions(Color.fromRGB(25, 215, 255), 0.9f);
    private final Particle.DustOptions deepBlueDust = new Particle.DustOptions(Color.fromRGB(10, 100, 240), 0.8f);

    public PortalVisuals(VoidscapePlugin plugin) {
        this.plugin = plugin;
        rendererRevision = plugin.key("portal_renderer_revision");
        file = new File(plugin.getDataFolder(), "portals.yml");
        var config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try { cells.put(key, Axis.valueOf(config.getString(key))); }
            catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Invalid portal cell: " + key); }
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::spawnPortalParticles, 10L, 3L);
    }
    private String key(Block b) { return b.getWorld().getUID()+","+b.getX()+","+b.getY()+","+b.getZ(); }
    public boolean contains(Block b) { return b.getType()==Material.STRUCTURE_VOID && cells.containsKey(key(b)); }
    public void add(Block b, Axis axis) {
        b.setType(Material.STRUCTURE_VOID, false);
        cells.put(key(b), axis);
    }
    public void remove(Block b) {
        String key=key(b);
        cells.remove(key);
        UUID uuid=displays.remove(key);
        Entity e=uuid==null?null:Bukkit.getEntity(uuid);
        if(e!=null)e.remove();
    }
    public void save() {
        var config=new YamlConfiguration();
        cells.forEach((key,axis)->config.set(key,axis.name()));
        try { config.save(file); }
        catch(java.io.IOException e) { plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cannot save portal cells",e); }
    }
    public void tick() {
        boolean changed=false;
        for(var entry : new HashMap<>(cells).entrySet()) {
            String key=entry.getKey(); String[] parts=key.split(",");
            World w=Bukkit.getWorld(UUID.fromString(parts[0]));
            if(w==null)continue;
            int x=Integer.parseInt(parts[1]),y=Integer.parseInt(parts[2]),z=Integer.parseInt(parts[3]);
            if(!w.isChunkLoaded(x>>4,z>>4))continue;
            Block block=w.getBlockAt(x,y,z);
            if(block.getType()!=Material.STRUCTURE_VOID) {remove(block);changed=true;continue;}
            UUID uuid=displays.get(key); Entity old=uuid==null?null:Bukkit.getEntity(uuid);
            if(old instanceof ArmorStand stand&&old.isValid()) {configure(stand,key);continue;}
            Location loc=new Location(w,x+0.5,y-1.5,z+0.5,entry.getValue()==Axis.X?0:90,0);
            ArmorStand found=null;
            for(Entity nearby:w.getNearbyEntities(loc,0.2,0.2,0.2)) {
                if(nearby instanceof ArmorStand stand && key.equals(stand.getPersistentDataContainer().get(plugin.key("portal_visual"),PersistentDataType.STRING))) {found=stand;break;}
            }
            if(found==null) found=w.spawn(loc,ArmorStand.class,stand->{
                stand.setInvisible(true);stand.setGravity(false);stand.setMarker(false);
                stand.setCollidable(false);stand.setInvulnerable(true);stand.setSilent(true);
                stand.setBasePlate(false);stand.setPersistent(true);
                stand.getPersistentDataContainer().set(plugin.key("portal_visual"),PersistentDataType.STRING,key);
                configure(stand,key);
            });
            configure(found,key);
            displays.put(key,found.getUniqueId());
        }
        if(changed)save();
    }

    private void configure(ArmorStand stand,String key) {
        // Keep the base item model aligned with Geyser's mapping. The worn armor
        // asset must be absent so Java uses the custom item model on the head.
        if(Integer.valueOf(6).equals(stand.getPersistentDataContainer().get(rendererRevision,PersistentDataType.INTEGER)))return;
        stand.setInvisible(true);stand.setGravity(false);stand.setMarker(false);
        stand.setCollidable(false);stand.setInvulnerable(true);stand.setSilent(true);
        stand.setBasePlate(false);stand.setPersistent(true);
        stand.getPersistentDataContainer().set(plugin.key("portal_visual"),PersistentDataType.STRING,key);
        ItemStack item=new ItemStack(Material.IRON_HELMET);
        var meta=item.getItemMeta();
        meta.setItemModel(null);
        var equipment=meta.getEquippable();
        equipment.setSlot(EquipmentSlot.HEAD);equipment.setModel(null);
        meta.setEquippable(equipment);
        var cmd=meta.getCustomModelDataComponent();
        cmd.setStrings(List.of("voidscape:azure_portal"));
        meta.setCustomModelDataComponent(cmd);item.setItemMeta(meta);
        stand.getEquipment().setHelmet(item,true);
        for(var lock:ArmorStand.LockType.values())stand.addEquipmentLock(EquipmentSlot.HEAD,lock);
        stand.getPersistentDataContainer().set(rendererRevision,PersistentDataType.INTEGER,6);
    }

    public void spawnPortalParticles() {
        if (cells.isEmpty()) return;
        for (var entry : new ArrayList<>(cells.entrySet())) {
            String key = entry.getKey();
            String[] parts = key.split(",");
            World w = Bukkit.getWorld(UUID.fromString(parts[0]));
            if (w == null) continue;
            int x = Integer.parseInt(parts[1]), y = Integer.parseInt(parts[2]), z = Integer.parseInt(parts[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) continue;

            Location center = new Location(w, x + 0.5, y + 0.5, z + 0.5);
            if (w.getNearbyPlayers(center, 32.0).isEmpty()) continue;

            Axis axis = entry.getValue();
            double px, py, pz;
            if (axis == Axis.X) {
                px = x + random.nextDouble();
                py = y + random.nextDouble();
                pz = z + 0.5 + (random.nextBoolean() ? 0.08 : -0.08);
            } else {
                px = x + 0.5 + (random.nextBoolean() ? 0.08 : -0.08);
                py = y + random.nextDouble();
                pz = z + random.nextDouble();
            }

            // Upward gentle soul flame stream
            w.spawnParticle(Particle.SOUL_FIRE_FLAME, px, py, pz, 1, 0.0, 0.015, 0.0, 0.005);

            // Ambient azure/cyan dust mist
            if (random.nextInt(3) == 0) {
                Particle.DustOptions dust = random.nextBoolean() ? cyanDust : deepBlueDust;
                w.spawnParticle(Particle.DUST, px, py, pz, 1, 0.0, 0.0, 0.0, dust);
            }

            // Ghostly cyan soul wisp drifting out of the portal
            if (random.nextInt(5) == 0) {
                w.spawnParticle(Particle.SCULK_SOUL, px, py, pz, 1, 0.0, 0.02, 0.0, 0.01);
            }

            // Subtle mystical portal hum
            if (random.nextInt(70) == 0) {
                w.playSound(center, Sound.BLOCK_PORTAL_AMBIENT, SoundCategory.BLOCKS, 0.2f, 1.35f);
            }
        }
    }
}
