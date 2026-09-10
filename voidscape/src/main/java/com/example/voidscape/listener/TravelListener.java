package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.world.DungeonLayout.Site;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Orientable;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;
import java.util.*;

public final class TravelListener implements Listener {
    private final VoidscapePlugin plugin;
    private final Map<UUID,Long> standing=new HashMap<>(),pending=new HashMap<>(),fallGrace=new HashMap<>();
    public TravelListener(VoidscapePlugin plugin){this.plugin=plugin;}

    private boolean allowedEntryWorld(Player p){
        List<String> list=plugin.getConfig().getStringList("portal.entry-worlds");
        return list.isEmpty() || list.contains(p.getWorld().getName()) || (p.getWorld().getEnvironment()==World.Environment.NORMAL && list.contains("world"));
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e) {
        Player p=e.getPlayer();
        if(!e.getAction().isRightClick()||e.getClickedBlock()==null)return;
        Block clicked=e.getClickedBlock();

        // Spawn Lectern Guide Book interaction
        if(clicked.getWorld()==plugin.world()&&clicked.getType()==Material.LECTERN&&clicked.getX()==0&&clicked.getZ()==4) {
            e.setCancelled(true);
            p.openBook(plugin.relics().createGuideBook());
            p.playSound(p.getLocation(),Sound.ITEM_BOOK_PAGE_TURN,0.8f,1.0f);
            return;
        }

        ItemStack hand=p.getInventory().getItem(e.getHand());
        if(hand==null)return;

        // Anti-Boat Cheese: prevent placing boats/minecarts near shrines in the void
        if(clicked.getWorld()==plugin.world()&&isVehicleItem(hand.getType())) {
            Site site=plugin.layout().at(clicked.getX(),clicked.getZ(),8);
            if(site!=null) {
                e.setCancelled(true);
                plugin.message(p,"มนต์สะกดของวิหารขัดขวางไม่ให้ใช้เรือหรือยานพาหนะ!");
                return;
            }
        }

        // Crying Obsidian Portal ignition with Fire Charge or Eye of Ender
        if(hand.getType()==Material.FIRE_CHARGE||hand.getType()==Material.ENDER_EYE) {
            if(!allowedEntryWorld(p)||p.getWorld()==plugin.world())return;
            Block target=clicked.getType()==Material.CRYING_OBSIDIAN?clicked.getRelative(e.getBlockFace()):clicked;
            if(tryIgnitePortal(target,p)) {
                e.setCancelled(true);
                if(p.getGameMode()!=GameMode.CREATIVE) hand.subtract(1);
            }
        }
    }

    private boolean isVehicleItem(Material m) {
        String name=m.name();
        return name.endsWith("_BOAT")||name.endsWith("_CHEST_BOAT")||name.endsWith("_RAFT")||name.endsWith("_MINECART")||name.equals("MINECART");
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void vehicleEnter(VehicleEnterEvent e) {
        if(e.getEntered() instanceof LivingEntity living) {
            if(living.getPersistentDataContainer().has(plugin.key("dungeon_mob"),PersistentDataType.STRING)) {
                e.setCancelled(true);
                if(e.getVehicle() instanceof Boat||e.getVehicle() instanceof Minecart) {
                    Bukkit.getScheduler().runTask(plugin,()->e.getVehicle().remove());
                }
            }
        }
    }

    private boolean tryIgnitePortal(Block inner,Player p) {
        if(inner.getType()!=Material.AIR&&inner.getType()!=Material.FIRE)return false;
        // Test X-Axis orientation
        if(checkAndFillPortal(inner,Axis.X)) {
            portalIgniteFeedback(inner,p);
            return true;
        }
        // Test Z-Axis orientation
        if(checkAndFillPortal(inner,Axis.Z)) {
            portalIgniteFeedback(inner,p);
            return true;
        }
        return false;
    }

    private void portalIgniteFeedback(Block b,Player p) {
        Location loc=b.getLocation().add(0.5,0.5,0.5);
        p.getWorld().playSound(loc,Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN,1.0f,0.8f);
        p.getWorld().playSound(loc,Sound.BLOCK_END_PORTAL_SPAWN,0.8f,1.2f);
        p.getWorld().spawnParticle(Particle.PORTAL,loc,40,1.0,1.5,1.0,0.1);
        p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,loc,20,0.5,1.0,0.5,0.03);
        plugin.message(p,"ประตูมิติความว่างเปล่า (Voidscape Portal) เปิดออกแล้ว!");
    }

    private boolean checkAndFillPortal(Block start,Axis axis) {
        World w=start.getWorld();
        int y=start.getY(),x=start.getX(),z=start.getZ();
        // Find bottom inner Y
        while(y>w.getMinHeight()+1&&isInnerBlock(w.getBlockAt(x,y-1,z))) y--;
        int minY=y;
        if(w.getBlockAt(x,minY-1,z).getType()!=Material.CRYING_OBSIDIAN)return false;

        // Find min and max along axis
        int minD=axis==Axis.X?x:z,maxD=minD;
        while(isInnerBlock(axis==Axis.X?w.getBlockAt(minD-1,minY,z):w.getBlockAt(x,minY,minD-1))) minD--;
        while(isInnerBlock(axis==Axis.X?w.getBlockAt(maxD+1,minY,z):w.getBlockAt(x,minY,maxD+1))) maxD++;

        int width=maxD-minD+1;
        if(width<2||width>21)return false;

        // Check bottom border
        for(int d=minD;d<=maxD;d++) {
            Block b=axis==Axis.X?w.getBlockAt(d,minY-1,z):w.getBlockAt(x,minY-1,d);
            if(b.getType()!=Material.CRYING_OBSIDIAN)return false;
        }

        // Find height
        int curY=minY;
        while(curY<w.getMaxHeight()-1) {
            boolean allInner=true;
            for(int d=minD;d<=maxD;d++) {
                Block b=axis==Axis.X?w.getBlockAt(d,curY,z):w.getBlockAt(x,curY,d);
                if(!isInnerBlock(b)){allInner=false;break;}
            }
            if(!allInner)break;
            // Check side frames
            Block left=axis==Axis.X?w.getBlockAt(minD-1,curY,z):w.getBlockAt(x,curY,minD-1);
            Block right=axis==Axis.X?w.getBlockAt(maxD+1,curY,z):w.getBlockAt(x,curY,maxD+1);
            if(left.getType()!=Material.CRYING_OBSIDIAN||right.getType()!=Material.CRYING_OBSIDIAN)return false;
            curY++;
        }
        int maxY=curY-1;
        int height=maxY-minY+1;
        if(height<3||height>21)return false;

        // Check top border
        for(int d=minD;d<=maxD;d++) {
            Block b=axis==Axis.X?w.getBlockAt(d,maxY+1,z):w.getBlockAt(x,maxY+1,d);
            if(b.getType()!=Material.CRYING_OBSIDIAN)return false;
        }

        // Fill inner with NETHER_PORTAL
        for(int h=minY;h<=maxY;h++) {
            for(int d=minD;d<=maxD;d++) {
                Block b=axis==Axis.X?w.getBlockAt(d,h,z):w.getBlockAt(x,h,d);
                b.setType(Material.NETHER_PORTAL,false);
                if(b.getBlockData() instanceof Orientable orient) {
                    orient.setAxis(axis);
                    b.setBlockData(orient,false);
                }
            }
        }
        return true;
    }

    private boolean isInnerBlock(Block b) {
        Material m=b.getType();
        return m==Material.AIR||m==Material.CAVE_AIR||m==Material.FIRE||m==Material.SOUL_FIRE||m==Material.NETHER_PORTAL;
    }

    private boolean isCryingObsidianPortal(Block portalBlock) {
        if(portalBlock.getType()!=Material.NETHER_PORTAL)return false;
        for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN}) {
            Block adj=portalBlock.getRelative(face);
            if(adj.getType()==Material.CRYING_OBSIDIAN)return true;
        }
        return false;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void portalEvent(PlayerPortalEvent e) {
        Player p=e.getPlayer();
        if(p.getWorld()==plugin.world()) {
            e.setCancelled(true);
            leave(p,false);
            return;
        }
        Block block=p.getLocation().getBlock();
        if(isCryingObsidianPortal(block)||isCryingObsidianPortal(block.getRelative(BlockFace.UP))) {
            e.setCancelled(true);
            enter(p);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        if(!e.hasChangedBlock())return;
        Player p=e.getPlayer();
        Block b=p.getLocation().getBlock();
        if(b.getType()!=Material.NETHER_PORTAL)return;
        if(p.getWorld()==plugin.world()) {
            // Return portal at spawn island
            if(Math.abs(b.getX())<=3&&b.getZ()<=-4&&b.getZ()>=-6) {
                leave(p,false);
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void breakFrame(BlockBreakEvent e) {
        handlePortalBreak(e.getBlock());
    }

    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void explode(EntityExplodeEvent e) {
        for(Block b:e.blockList()) handlePortalBreak(b);
    }

    private void handlePortalBreak(Block broken) {
        if(broken.getType()!=Material.CRYING_OBSIDIAN&&broken.getType()!=Material.NETHER_PORTAL)return;
        for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN}) {
            Block adj=broken.getRelative(face);
            if(adj.getType()==Material.NETHER_PORTAL) {
                clearPortal(adj,new HashSet<>());
            }
        }
    }

    private void clearPortal(Block b,Set<Block> seen) {
        if(b.getType()!=Material.NETHER_PORTAL||!seen.add(b)||seen.size()>100)return;
        b.setType(Material.AIR);
        for(BlockFace face:new BlockFace[]{BlockFace.NORTH,BlockFace.SOUTH,BlockFace.EAST,BlockFace.WEST,BlockFace.UP,BlockFace.DOWN}) {
            clearPortal(b.getRelative(face),seen);
        }
    }

    public void enter(Player p) {
        if(p.getWorld()==plugin.world()||pending.containsKey(p.getUniqueId()))return;
        Location from=p.getLocation();
        teleport(p,new Location(plugin.world(),0.5,97,0.5),()->{
            p.getPersistentDataContainer().set(plugin.key("return_location"),PersistentDataType.STRING,
                from.getWorld().getUID()+","+from.getX()+","+from.getY()+","+from.getZ()+","+from.getYaw()+","+from.getPitch());
            // Deliver guide book if first time
            if(!p.getPersistentDataContainer().has(plugin.key("has_guide"),PersistentDataType.BYTE)) {
                p.getPersistentDataContainer().set(plugin.key("has_guide"),PersistentDataType.BYTE,(byte)1);
                ItemStack book=plugin.relics().createGuideBook();
                var leftover=p.getInventory().addItem(book);
                if(!leftover.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(),book);
                plugin.message(p,"ยินดีต้อนรับสู่มิติความว่างเปล่า! มอบคู่มือสำรวจให้แล้ว");
            }
            plugin.message(p,"✦ สวมใส่ Elytra และใช้พลุบินค้นหาวิหารทั้ง 3 แห่ง!");
            p.playSound(p.getLocation(),Sound.BLOCK_PORTAL_TRAVEL,0.7f,1.0f);
        });
    }

    public void leave(Player p,boolean rescued) {
        if(pending.containsKey(p.getUniqueId()))return;
        if(!rescued&&plugin.dungeons().inCombat(p)){plugin.message(p,"ยังอยู่ระหว่างต่อสู้ · ออกจากเขตดันแล้วรอ 10 วินาที");return;}
        Location to=returnLocation(p);
        teleport(p,to,()->{
            if(rescued){p.setHealth(Math.min(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),8));plugin.message(p,"มิติส่งคุณกลับ · เก็บอุปกรณ์ไว้ แต่การต่อสู้ยังไม่สำเร็จ");}
            else plugin.message(p,"กลับจากมิติ Voidscape แล้ว");
        });
    }

    private Location returnLocation(Player p) {
        String value=p.getPersistentDataContainer().get(plugin.key("return_location"),PersistentDataType.STRING);
        if(value!=null)try {
            String[] a=value.split(",");World w=Bukkit.getWorld(UUID.fromString(a[0]));
            if(w!=null&&w!=plugin.world()) {
                Location location=new Location(w,Double.parseDouble(a[1]),Double.parseDouble(a[2]),Double.parseDouble(a[3]),Float.parseFloat(a[4]),Float.parseFloat(a[5]));
                if(location.getY()>w.getMinHeight()+2&&w.getWorldBorder().isInside(location))return location;
            }
        }catch(RuntimeException ignored){}
        Location bed=p.getRespawnLocation();
        if(bed!=null&&bed.getWorld()!=plugin.world())return bed;
        return Bukkit.getWorlds().stream().filter(w->w!=plugin.world()&&w.getEnvironment()==World.Environment.NORMAL).findFirst().orElse(Bukkit.getWorlds().get(0)).getSpawnLocation();
    }

    private void teleport(Player p,Location destination,Runnable done) {
        UUID id=p.getUniqueId();pending.put(id,System.currentTimeMillis()+15000);
        p.teleportAsync(destination).whenComplete((success,error)->Bukkit.getScheduler().runTask(plugin,()->{
            pending.remove(id);
            if(!p.isOnline())return;
            if(error==null&&Boolean.TRUE.equals(success)) {p.setFallDistance(0);p.setVelocity(new Vector());fallGrace.put(id,System.currentTimeMillis()+5000);done.run();}
            else plugin.message(p,"วาร์ปไม่สำเร็จหรือถูกระบบอื่นปฏิเสธ");
        }));
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e) {
        if(!(e.getEntity() instanceof Player p))return;
        if(p.getWorld()!=plugin.world())return;
        if(p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR)return;
        long now=System.currentTimeMillis();
        if(plugin.relics().immune(p)||pending.getOrDefault(p.getUniqueId(),0L)>now){e.setCancelled(true);return;}
        // Safe fall damage inside void dimension
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL) {
            if(fallGrace.getOrDefault(p.getUniqueId(),0L)>now) { e.setCancelled(true); return; }
            e.setDamage(Math.min(e.getDamage()*0.2,4.0));
            return;
        }
        // Abyss void fall rescue
        if(e.getCause()==EntityDamageEvent.DamageCause.VOID||(p.getLocation().getY()<-30&&e.getFinalDamage()>=p.getHealth())) {
            e.setCancelled(true);leave(p,true);return;
        }
        // Lethal combat rescue
        if(e.getFinalDamage()>=p.getHealth()) {
            e.setCancelled(true);leave(p,true);
        }
    }

    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();standing.remove(id);pending.remove(id);fallGrace.remove(id);}

    public void tick() {
        long now=System.currentTimeMillis();
        pending.values().removeIf(end->end<now);
        fallGrace.values().removeIf(end->end<now);
    }
}
