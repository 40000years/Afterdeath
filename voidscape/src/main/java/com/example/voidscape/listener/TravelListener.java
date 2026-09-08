package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.*;
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
        if(!allowedEntryWorld(p)||p.getWorld()==plugin.world()||!e.getAction().isRightClick()||e.getClickedBlock()==null)return;
        Material block=e.getClickedBlock().getType();
        if(block!=Material.BEDROCK&&block!=Material.CRYING_OBSIDIAN)return;
        ItemStack hand=p.getInventory().getItemInMainHand();
        if(hand.getType()==Material.ECHO_SHARD||hand.getType()==Material.ENDER_PEARL||hand.getType()==Material.ENDER_EYE) {
            e.setCancelled(true);
            p.playSound(e.getClickedBlock().getLocation(),Sound.BLOCK_END_PORTAL_SPAWN,0.7f,1.0f);
            p.getWorld().spawnParticle(Particle.PORTAL,e.getClickedBlock().getLocation().add(0.5,1.2,0.5),30,0.5,0.5,0.5,0.1);
            p.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME,e.getClickedBlock().getLocation().add(0.5,1.0,0.5),15,0.3,0.3,0.3,0.02);
            enter(p);
        }
    }
    public void enter(Player p) {
        if(p.getWorld()==plugin.world()||pending.containsKey(p.getUniqueId()))return;
        Location from=p.getLocation();
        teleport(p,new Location(plugin.world(),0.5,97,0.5),()->{
            p.getPersistentDataContainer().set(plugin.key("return_location"),PersistentDataType.STRING,
                from.getWorld().getUID()+","+from.getX()+","+from.getY()+","+from.getZ()+","+from.getYaw()+","+from.getPitch());
            plugin.message(p,"VOIDSCAPE · สำรวจหาเรือเหนือคฤหาสน์ /void help · /void leave เพื่อกลับ");
        });
    }
    public void leave(Player p,boolean rescued) {
        if(pending.containsKey(p.getUniqueId()))return;
        if(!rescued&&plugin.dungeons().inCombat(p)){plugin.message(p,"ยังอยู่ระหว่างต่อสู้ · ออกจากเขตดันแล้วรอ 10 วินาที");return;}
        Location to=returnLocation(p);
        teleport(p,to,()->{
            if(rescued){p.setHealth(Math.min(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),8));plugin.message(p,"มิติส่งคุณกลับ · เก็บอุปกรณ์ไว้ แต่การต่อสู้ยังไม่สำเร็จ");}
            else plugin.message(p,"กลับจาก Void แล้ว");
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
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void sneak(PlayerToggleSneakEvent e) {
        Player p=e.getPlayer();
        if(!allowedEntryWorld(p))return;
        if(e.isSneaking()&&onBedrock(p)) {
            standing.putIfAbsent(p.getUniqueId(),System.currentTimeMillis());
            p.playSound(p.getLocation(),Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,0.5f,0.8f);
        } else {
            standing.remove(p.getUniqueId());
        }
    }
    private boolean onBedrock(Player p) {
        if(p.getWorld()==plugin.world())return false;
        if(p.getLocation().getY()>plugin.integer("portal.bedrock-trigger-y",-50,-64,64))return false;
        Location loc=p.getLocation();
        return loc.getBlock().getType()==Material.BEDROCK
            || loc.clone().subtract(0,0.5,0).getBlock().getType()==Material.BEDROCK
            || loc.clone().subtract(0,1.0,0).getBlock().getType()==Material.BEDROCK
            || loc.clone().subtract(0,0.2,0).getBlock().getType()==Material.CRYING_OBSIDIAN;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageEvent e) {
        if(!(e.getEntity() instanceof Player p))return;
        if(p.getWorld()!=plugin.world())return;
        if(p.getGameMode()==GameMode.CREATIVE||p.getGameMode()==GameMode.SPECTATOR)return;
        long now=System.currentTimeMillis();
        if(plugin.relics().immune(p)||pending.getOrDefault(p.getUniqueId(),0L)>now){e.setCancelled(true);return;}
        // Fall damage inside the floating island dimension: reduce significantly so exploring island cliffs is fun and safe!
        if(e.getCause()==EntityDamageEvent.DamageCause.FALL) {
            if(fallGrace.getOrDefault(p.getUniqueId(),0L)>now) { e.setCancelled(true); return; }
            e.setDamage(Math.min(e.getDamage()*0.2, 4.0));
            return;
        }
        // Void damage: only rescue if player falls below Y < -30 into the abyss
        if(e.getCause()==EntityDamageEvent.DamageCause.VOID || (p.getLocation().getY()<-30 && e.getFinalDamage()>=p.getHealth())) {
            e.setCancelled(true);leave(p,true);return;
        }
        // Lethal combat damage: rescue player safely before dying
        if(e.getFinalDamage()>=p.getHealth()) {
            e.setCancelled(true);leave(p,true);
        }
    }
    @EventHandler public void quit(PlayerQuitEvent e){UUID id=e.getPlayer().getUniqueId();standing.remove(id);pending.remove(id);fallGrace.remove(id);}
    public void tick() {
        long now=System.currentTimeMillis();
        long standDuration=plugin.integer("portal.stand-seconds",3,1,30)*1000L;
        for(Player p:Bukkit.getOnlinePlayers()) {
            if(!allowedEntryWorld(p)||p.getWorld()==plugin.world())continue;
            UUID id=p.getUniqueId();
            if(p.isSneaking()&&onBedrock(p)) {
                long start=standing.computeIfAbsent(id,k->now);
                long elapsed=now-start;
                double progress=Math.min(1.0,(double)elapsed/standDuration);
                int bars=(int)(progress*10);
                String bar="█".repeat(bars)+"░".repeat(10-bars);
                double remaining=Math.max(0.0,(standDuration-elapsed)/1000.0);
                p.sendActionBar(net.kyori.adventure.text.Component.text("✦ กำลังเปิดมิติ Voidscape... ["+bar+"] "+String.format("%.1f",remaining)+"s",net.kyori.adventure.text.format.NamedTextColor.LIGHT_PURPLE));
                p.spawnParticle(Particle.PORTAL,p.getLocation().add(0,0.2,0),6,0.25,0.1,0.25,0.05);
                p.spawnParticle(Particle.SOUL_FIRE_FLAME,p.getLocation().add(0,0.1,0),2,0.15,0.05,0.15,0.01);
                if(elapsed>=standDuration) {
                    standing.remove(id);
                    p.playSound(p.getLocation(),Sound.BLOCK_PORTAL_TRIGGER,0.8f,1.2f);
                    enter(p);
                }
            } else if(standing.remove(id)!=null) {
                p.sendActionBar(net.kyori.adventure.text.Component.text("✖ ยกเลิกการเปิดมิติ Voidscape",net.kyori.adventure.text.format.NamedTextColor.RED));
            }
        }
        pending.values().removeIf(end->end<now);fallGrace.values().removeIf(end->end<now);
    }
}
