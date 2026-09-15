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
    public PortalVisuals(VoidscapePlugin plugin) {
        this.plugin = plugin;
        file = new File(plugin.getDataFolder(), "portals.yml");
        var config = YamlConfiguration.loadConfiguration(file);
        for (String key : config.getKeys(false)) {
            try { cells.put(key, Axis.valueOf(config.getString(key))); }
            catch (IllegalArgumentException ignored) { plugin.getLogger().warning("Invalid portal cell: " + key); }
        }
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
            if(old!=null&&old.isValid())continue;
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
                ItemStack item=new ItemStack(Material.IRON_HELMET);
                var meta=item.getItemMeta();var cmd=meta.getCustomModelDataComponent();
                cmd.setStrings(List.of("voidscape:azure_portal"));meta.setCustomModelDataComponent(cmd);item.setItemMeta(meta);
                stand.getEquipment().setHelmet(item,true);
                for(var lock:ArmorStand.LockType.values())stand.addEquipmentLock(EquipmentSlot.HEAD,lock);
            });
            displays.put(key,found.getUniqueId());
        }
        if(changed)save();
    }
}
