package com.example.advancemagic.effect;

import com.example.advancemagic.AdvanceMagicPlugin;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
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
    public void armor(Player p){armor.put(p.getUniqueId(),status(p,p,160));}
    public boolean armored(Player p){return armor.containsKey(p.getUniqueId());}
    public void shroud(Player p) {
        reveal(p);
        shrouds.put(p.getUniqueId(),status(p,p,200));
        plugin.context().potion(p,PotionEffectType.INVISIBILITY,200,0);
        plugin.context().potion(p,PotionEffectType.SPEED,200,1);
        for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.equals(p))viewer.hidePlayer(plugin,p);
        for(Entity e:p.getNearbyEntities(32,32,32))if(e instanceof Mob mob&&p.equals(mob.getTarget()))mob.setTarget(null);
    }
    private void removeOwnedPotion(Player p,PotionEffectType type,int amp,long remaining) {
        var effect=p.getPotionEffect(type);
        if(effect!=null&&effect.getAmplifier()==amp&&effect.getDuration()<=remaining+2)p.removePotionEffect(type);
    }
    public void reveal(Player p) {
        Status s=shrouds.remove(p.getUniqueId());if(s==null)return;
        for(Player viewer:Bukkit.getOnlinePlayers())if(!viewer.equals(p))viewer.showPlayer(plugin,p);
        removeOwnedPotion(p,PotionEffectType.INVISIBILITY,0,Math.max(0,s.end-tick));
        removeOwnedPotion(p,PotionEffectType.SPEED,1,Math.max(0,s.end-tick));
    }
    public void joined(Player p){for(Status s:shrouds.values())if(!s.target.equals(p))p.hidePlayer(plugin,(Player)s.target);}
    private boolean expired(Status s){return tick>=s.end||!s.caster.isOnline()||s.caster.isDead()||!s.target.isValid()||s.target.isDead()||s.caster.getWorld()!=s.anchor.getWorld()||s.target.getWorld()!=s.anchor.getWorld();}
    public void tick() {
        tick++;
        roots.values().removeIf(this::expired);
        for(Status s:roots.values())s.target.setVelocity(new Vector());
        frozen.values().removeIf(this::expired);
        for(Status s:frozen.values())s.target.setFreezeTicks(Math.max(0,s.target.getMaxFreezeTicks()-1));
        armor.values().removeIf(this::expired);
        for(Status s:List.copyOf(shrouds.values()))if(expired(s))reveal((Player)s.target);
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
        // Teleports remain available to protection/admin plugins and are never turned into cross-world moves.
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
        if(!(e.getEntity() instanceof Player p)||!armored(p)||!(e.getDamager() instanceof LivingEntity attacker)
            ||(e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_ATTACK&&e.getCause()!=EntityDamageEvent.DamageCause.ENTITY_SWEEP_ATTACK))return;
        double amount=e.getFinalDamage()*0.3;
        if(amount<=0||!plugin.context().enemy(p,attacker))return;
        Bukkit.getScheduler().runTask(plugin,()->{
            if(p.isOnline()&&!p.isDead()&&attacker.isValid()&&!attacker.isDead()&&p.getWorld()==attacker.getWorld()
                &&plugin.context().affect(p,attacker,com.example.advancemagic.spell.Spell.IRON_ARMOR))
                plugin.context().damage(p,attacker,amount,DamageType.THORNS);
        });
    }
    @EventHandler(ignoreCancelled=true) public void launch(ProjectileLaunchEvent e){if(e.getEntity().getShooter() instanceof Player p)reveal(p);}
    @EventHandler(ignoreCancelled=true) public void target(EntityTargetLivingEntityEvent e){if(e.getTarget()!=null&&shrouds.containsKey(e.getTarget().getUniqueId()))e.setCancelled(true);}
}
