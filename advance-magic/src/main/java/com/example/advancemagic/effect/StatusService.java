package com.example.advancemagic.effect;

import com.destroystokyo.paper.event.player.PlayerArmorChangeEvent;
import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.*;

public final class StatusService implements Listener {
    private record Status(Player caster,LivingEntity target,Location anchor,long end) {}
    private final AdvanceMagicPlugin plugin;
    private final Map<UUID,Status> roots=new HashMap<>(),frozen=new HashMap<>(),shrouds=new HashMap<>(),armor=new HashMap<>();
    private long tick;
    public StatusService(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    private Status status(Player p,LivingEntity e,int ticks){return new Status(p,e,e.getLocation(),tick+ticks);}
    public void freeze(Player p,LivingEntity e){frozen.put(e.getUniqueId(),status(p,e,120));}
    public void root(Player p,LivingEntity e){roots.put(e.getUniqueId(),status(p,e,30));plugin.context().potion(e,PotionEffectType.SLOWNESS,30,127);}
    public void armor(Player p){armor.put(p.getUniqueId(),status(p,p,1200));}
    public boolean armored(Player p){return armor.containsKey(p.getUniqueId());}
    public boolean isShrouded(Player p){return shrouds.containsKey(p.getUniqueId());}

    public void hideEquipment(Player p) {
        ItemStack air=new ItemStack(Material.AIR);
        for(EquipmentSlot slot:EquipmentSlot.values()) {
            p.sendEquipmentChange(p,slot,air);
            for(Player viewer:Bukkit.getOnlinePlayers())viewer.sendEquipmentChange(p,slot,air);
        }
    }

    public void restoreEquipment(Player p) {
        for(EquipmentSlot slot:EquipmentSlot.values()) {
            ItemStack item=p.getInventory().getItem(slot);
            ItemStack send=item==null?new ItemStack(Material.AIR):item;
            p.sendEquipmentChange(p,slot,send);
            for(Player viewer:Bukkit.getOnlinePlayers())viewer.sendEquipmentChange(p,slot,send);
        }
    }

    public void shroud(Player p) {
        reveal(p);
        shrouds.put(p.getUniqueId(),status(p,p,600));
        plugin.context().potion(p,PotionEffectType.INVISIBILITY,600,0);
        plugin.context().potion(p,PotionEffectType.SPEED,600,2);
        plugin.context().potion(p,PotionEffectType.NIGHT_VISION,600,0);
        plugin.context().potion(p,PotionEffectType.RESISTANCE,600,1);
        hideEquipment(p);
        for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.equals(p))viewer.hidePlayer(plugin,p);
        for(Entity e:p.getNearbyEntities(48,48,48))if(e instanceof Mob mob&&p.equals(mob.getTarget()))mob.setTarget(null);
    }
    private void removeOwnedPotion(Player p,PotionEffectType type,int amp,long remaining) {
        var effect=p.getPotionEffect(type);
        if(effect!=null&&effect.getAmplifier()==amp&&effect.getDuration()<=remaining+2)p.removePotionEffect(type);
    }
    public void reveal(Player p) {
        Status s=shrouds.remove(p.getUniqueId());if(s==null)return;
        restoreEquipment(p);
        for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.equals(p))viewer.showPlayer(plugin,p);
        removeOwnedPotion(p,PotionEffectType.INVISIBILITY,0,Math.max(0,s.end-tick));
        removeOwnedPotion(p,PotionEffectType.SPEED,2,Math.max(0,s.end-tick));
        removeOwnedPotion(p,PotionEffectType.NIGHT_VISION,0,Math.max(0,s.end-tick));
        removeOwnedPotion(p,PotionEffectType.RESISTANCE,1,Math.max(0,s.end-tick));
    }
    public void joined(Player p){
        for(Status s:shrouds.values())if(!s.target.equals(p)&&s.target instanceof Player shrouded){
            p.hidePlayer(plugin,shrouded);
            ItemStack air=new ItemStack(Material.AIR);
            for(EquipmentSlot slot:EquipmentSlot.values())p.sendEquipmentChange(shrouded,slot,air);
        }
    }
    private boolean expired(Status s){return tick>=s.end||!s.caster.isOnline()||s.caster.isDead()||!s.target.isValid()||s.target.isDead()||s.caster.getWorld()!=s.anchor.getWorld()||s.target.getWorld()!=s.anchor.getWorld();}
    public void tick() {
        tick++;
        roots.values().removeIf(this::expired);
        for(Status s:roots.values())s.target.setVelocity(new Vector());
        frozen.values().removeIf(this::expired);
        for(Status s:frozen.values())s.target.setFreezeTicks(Math.max(0,s.target.getMaxFreezeTicks()-1));
        armor.values().removeIf(this::expired);
        for(Status s:List.copyOf(shrouds.values()))if(expired(s))reveal((Player)s.target);
        if(tick%10==0) {
            for(Status s:shrouds.values())if(s.target instanceof Player p&&p.isOnline()){
                hideEquipment(p);
                for(Entity e:p.getNearbyEntities(48,48,48))if(e instanceof Mob mob&&p.equals(mob.getTarget()))mob.setTarget(null);
            }
        }
    }
    public void clear(Player p) {
        reveal(p);
        roots.values().removeIf(s->s.caster.equals(p)||s.target.equals(p));
        frozen.values().removeIf(s->s.caster.equals(p)||s.target.equals(p));
        armor.remove(p.getUniqueId());
    }
    public void close(){for(Status s:List.copyOf(shrouds.values()))reveal((Player)s.target);roots.clear();frozen.clear();armor.clear();}
    @EventHandler(ignoreCancelled=true) public void move(PlayerMoveEvent e) {
        Status s=roots.get(e.getPlayer().getUniqueId());Location to=e.getTo();
        if(s==null||to==null||e instanceof PlayerTeleportEvent||to.getWorld()!=s.anchor.getWorld())return;
        if(to.getX()!=s.anchor.getX()||to.getY()!=s.anchor.getY()||to.getZ()!=s.anchor.getZ()) {
            Location fixed=s.anchor.clone();fixed.setYaw(to.getYaw());fixed.setPitch(to.getPitch());e.setTo(fixed);
        }
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void teleport(PlayerTeleportEvent e){roots.remove(e.getPlayer().getUniqueId());}
    @EventHandler(priority=EventPriority.LOWEST) public void attack(EntityDamageByEntityEvent e) {
        Player attacker=e.getDamager() instanceof Player p?p:e.getDamager() instanceof Projectile pr&&pr.getShooter() instanceof Player p?p:null;
        if(attacker!=null)reveal(attacker);
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true) public void reflect(EntityDamageByEntityEvent e) {
        if(!(e.getEntity() instanceof Player p)||!armored(p)||!(e.getDamager() instanceof LivingEntity attacker))return;
        double amount=Math.max(4.0,e.getFinalDamage()*1.0);
        if(amount<=0||!plugin.context().enemy(p,attacker))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(p.isOnline()&&!p.isDead()&&attacker.isValid()&&!attacker.isDead()&&p.getWorld()==attacker.getWorld()
                &&plugin.context().affect(p,attacker,com.example.advancemagic.spell.Spell.IRON_ARMOR)) {
                plugin.context().damage(p,attacker,amount,DamageType.THORNS);
                attacker.getWorld().playSound(attacker.getLocation(),Sound.BLOCK_ANVIL_LAND,0.6f,1.5f);
            }
        });
    }
    @EventHandler(ignoreCancelled=true) public void launch(ProjectileLaunchEvent e){if(e.getEntity().getShooter() instanceof Player p)reveal(p);}
    @EventHandler(ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){if(e.getTarget()!=null&&shrouds.containsKey(e.getTarget().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.MONITOR) public void armorChange(PlayerArmorChangeEvent e){if(isShrouded(e.getPlayer()))Bukkit.getScheduler().runTask(plugin,()->hideEquipment(e.getPlayer()));}
    @EventHandler(priority=EventPriority.MONITOR) public void heldItem(PlayerItemHeldEvent e){if(isShrouded(e.getPlayer()))Bukkit.getScheduler().runTask(plugin,()->hideEquipment(e.getPlayer()));}
    @EventHandler(priority=EventPriority.MONITOR) public void swapHand(PlayerSwapHandItemsEvent e){if(isShrouded(e.getPlayer()))Bukkit.getScheduler().runTask(plugin,()->hideEquipment(e.getPlayer()));}
}
