package com.example.advancemagic.spell;

import com.example.advancemagic.effect.EffectEngine;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import java.util.*;
import java.util.function.*;

public final class ProjectileSpells implements Listener {
    private record Shot(Player owner,Spell spell,Projectile projectile,Consumer<Location> impact) {}
    private final MagicContext c;
    private final NamespacedKey managed;
    private final Map<UUID,Shot> shots=new HashMap<>();
    public ProjectileSpells(MagicContext c){this.c=c;managed=new NamespacedKey(c.plugin,"projectile");}
    private <T extends Projectile> T launch(EffectEngine.Effect scope,Spell spell,Class<T> type,Vector velocity,Consumer<T> setup,Consumer<Location> impact) {
        T projectile=scope.track(scope.owner.launchProjectile(type,velocity));
        projectile.getPersistentDataContainer().set(managed,PersistentDataType.BYTE,(byte)1);
        setup.accept(projectile);
        shots.put(projectile.getUniqueId(),new Shot(scope.owner,spell,projectile,impact));
        scope.onClose(()->shots.remove(projectile.getUniqueId()));return projectile;
    }
    private boolean flight(EffectEngine.Effect effect,int age) {
        for(Shot shot:List.copyOf(shots.values()))if(shot.owner.equals(effect.owner)) {
            Projectile p=shot.projectile;
            if(!p.isValid()||!c.loaded(p.getLocation())||p.getLocation().distanceSquared(shot.owner.getLocation())>4096) {
                p.remove();shots.remove(p.getUniqueId());continue;
            }
            Vector from=p.getLocation().toVector();
            if(c.plugin.areas().blocksProjectile(p.getWorld(),from,from.clone().add(p.getVelocity()))) {
                p.remove();shots.remove(p.getUniqueId());continue;
            }
            if(age%3==0)c.particles(p.getLocation(),shot.spell==Spell.POISON_SPORES?Particle.HAPPY_VILLAGER:Particle.PORTAL,2,0.06);
        }
        return true;
    }
    public boolean spores(Player p) {
        var effect=c.plugin.effects().start(p,60,this::flight);
        launch(effect,Spell.POISON_SPORES,Snowball.class,p.getEyeLocation().getDirection().multiply(1.4),ball->{ball.setGravity(false);ball.setItem(new org.bukkit.inventory.ItemStack(Material.SPORE_BLOSSOM));},at->{
            c.particles(at,Particle.HAPPY_VILLAGER,50,1.5);c.ring(at,4,Spell.POISON_SPORES);
            for(var e:c.nearby(p,at,4,false))if(c.affect(p,e,Spell.POISON_SPORES)) {
                c.potion(e,PotionEffectType.POISON,120,1);c.potion(e,PotionEffectType.NAUSEA,120,0);
            }
        });return true;
    }
    public boolean voidPull(Player p) {
        var effect=c.plugin.effects().start(p,60,this::flight);
        launch(effect,Spell.VOID_PULL,Snowball.class,p.getEyeLocation().getDirection().multiply(1.1),ball->{ball.setGravity(false);ball.setItem(new org.bukkit.inventory.ItemStack(Material.ENDER_PEARL));},at->gravity(p,at));
        return true;
    }
    private void gravity(Player p,Location at) {
        if(!c.plugin.effects().hasCapacity())return;
        c.plugin.effects().start(p,40,(effect,age)->{
            if(!c.loaded(at))return false;
            if(age%5==0)c.ring(at,8,Spell.VOID_PULL);
            c.particles(at,Particle.REVERSE_PORTAL,12,0.4);
            if(age%2==0)for(var e:c.nearby(p,at,8,false))if(c.affect(p,e,Spell.VOID_PULL)) {
                if(age>=30||e.getLocation().distanceSquared(at)<2.25)c.plugin.statuses().root(p,e);
                else {
                    Vector velocity=at.clone().add(0,0.5,0).toVector().subtract(e.getLocation().toVector());
                    if(velocity.lengthSquared()>0.01)e.setVelocity(velocity.normalize().multiply(0.65));
                }
            }
            return true;
        });
    }
    public boolean wither(Player p) {
        c.plugin.effects().start(p,100,(effect,age)->{
            if(age==0||age==6||age==12)launch(effect,Spell.WITHER_RAY,WitherSkull.class,p.getEyeLocation().getDirection().multiply(1.2),skull->{skull.setYield(0);skull.setIsIncendiary(false);skull.setCharged(false);},at->{
                c.particles(at,Particle.EXPLOSION,1,0);
                at.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,0.5f,1.4f);
                for(var e:c.nearby(p,at,3,false))if(c.affect(p,e,Spell.WITHER_RAY)) {
                    c.damage(p,e,c.configuredDamage("damage.wither-skull",40),DamageType.EXPLOSION);
                    c.potion(e,PotionEffectType.WITHER,100,1);
                }
            });
            return flight(effect,age);
        });return true;
    }
    public boolean shulker(Player p) {
        LivingEntity target=c.targetEntity(p,30);if(target==null)return false;
        var effect=c.plugin.effects().start(p,100,this::flight);
        launch(effect,Spell.SHULKER_LEVITATION,ShulkerBullet.class,p.getEyeLocation().getDirection().multiply(0.6),bullet->bullet.setTarget(target),at->{
            // The homing projectile may strike a different entity; resolve the actual contact in hit().
        });return true;
    }
    public boolean dragon(Player p) {
        Location start=p.getEyeLocation().add(p.getEyeLocation().getDirection());Vector dir=p.getEyeLocation().getDirection();
        if(!c.loaded(start))return false;
        AreaEffectCloud[] cloud=new AreaEffectCloud[1];
        boolean[] settled={false};
        Map<UUID,Integer> nextDamage=new HashMap<>();
        var effect=c.plugin.effects().start(p,120,(scope,age)->{
            AreaEffectCloud entity=cloud[0];if(entity==null||!entity.isValid()||!c.loaded(entity.getLocation()))return false;
            Location at=entity.getLocation();
            if(!settled[0]&&age<20) {
                var hit=at.getWorld().rayTraceBlocks(at,dir,0.7,FluidCollisionMode.NEVER,true);
                if(hit!=null)settled[0]=true;
                else {Location next=at.clone().add(dir.clone().multiply(0.7));if(!c.loaded(next))return false;entity.teleport(next);at=next;}
            }
            if(age%5==0)for(var target:c.nearby(p,at,4,false))
                if(age>=nextDamage.getOrDefault(target.getUniqueId(),0)&&c.affect(p,target,Spell.DRAGONS_BREATH)) {
                    c.damage(p,target,c.configuredDamage("damage.dragon-per-second",30),DamageType.MAGIC);
                    nextDamage.put(target.getUniqueId(),age+20);
                }
            return true;
        });
        try {
            cloud[0]=effect.track(p.getWorld().spawn(start,AreaEffectCloud.class));
            cloud[0].setSource(p);cloud[0].setRadius(4);cloud[0].setRadiusPerTick(0);cloud[0].setRadiusOnUse(0);
            cloud[0].setDuration(125);cloud[0].setWaitTime(0);
            if(Particle.DRAGON_BREATH.getDataType()==Float.class)cloud[0].setParticle(Particle.DRAGON_BREATH,1.0f);
            else cloud[0].setParticle(Particle.DRAGON_BREATH);
            cloud[0].clearCustomEffects();
        }catch(RuntimeException ex){effect.close();throw ex;}
        return true;
    }
    public boolean meteor(Player p) {
        var hit=c.target(p,30);if(hit==null)return false;
        Location marker=hit.getHitPosition().toLocation(p.getWorld());
        var down=marker.getWorld().rayTraceBlocks(marker.clone().add(0,0.1,0),new Vector(0,-1,0),8,FluidCollisionMode.NEVER,true);
        if(hit.getHitBlock()!=null)marker=hit.getHitBlock().getLocation().add(0.5,1,0.5);
        else if(down!=null)marker=down.getHitPosition().toLocation(p.getWorld()).add(0,0.05,0);
        else return false;
        final Location at=marker;
        Location sky=at.clone().add(0,18,0);if(!c.loaded(sky))return false;
        LargeFireball[] meteor=new LargeFireball[1];
        c.plugin.effects().start(p,90,(effect,age)->{
            if(!c.loaded(at))return false;
            if(age<30){if(age%5==0)c.ring(at,6,Spell.METEOR_STRIKE);return true;}
            if(age==30) {
                meteor[0]=effect.track(at.getWorld().spawn(sky,LargeFireball.class));
                meteor[0].setShooter(p);meteor[0].setYield(0);meteor[0].setIsIncendiary(false);
                meteor[0].setDirection(new Vector(0,-1,0));meteor[0].setVelocity(new Vector(0,-1.2,0));
                meteor[0].getPersistentDataContainer().set(managed,PersistentDataType.BYTE,(byte)1);
                UUID id=meteor[0].getUniqueId();
                shots.put(id,new Shot(p,Spell.METEOR_STRIKE,meteor[0],impact->meteorImpact(p,impact)));
                effect.onClose(()->shots.remove(id));
            }
            if(meteor[0]!=null&&meteor[0].isValid())c.particles(meteor[0].getLocation(),Particle.FLAME,30,1.2);
            return flight(effect,age);
        });return true;
    }
    private void meteorImpact(Player p,Location at) {
        c.particles(at,Particle.EXPLOSION_EMITTER,1,0);c.ring(at,6,Spell.METEOR_STRIKE);
        at.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,1.5f,0.6f);
        for(var e:c.nearby(p,at,6,false))if(c.affect(p,e,Spell.METEOR_STRIKE)) {
            c.damage(p,e,c.configuredDamage("meteor.damage",90),DamageType.EXPLOSION);
            var ignite=new EntityCombustByEntityEvent(p,e,4.0f);Bukkit.getPluginManager().callEvent(ignite);
            if(!ignite.isCancelled())e.setFireTicks(Math.max(e.getFireTicks(),(int)(ignite.getDuration()*20)));
        }
        if(!c.plugin.getConfig().getBoolean("meteor.ignite-terrain",true))return;
        for(int x=-5;x<=5;x++)for(int z=-5;z<=5;z++) {
            if(x*x+z*z>30||(x+z)%3!=0)continue;
            for(int y=1;y>=-2;y--) {
                Location pos=at.clone().add(x,y,z);if(!c.loaded(pos)||!c.loaded(pos.clone().add(0,-1,0)))continue;
                Block b=pos.getBlock();if(!b.getType().isAir()||!b.getRelative(0,-1,0).getType().isOccluding())continue;
                var ignite=new BlockIgniteEvent(b,BlockIgniteEvent.IgniteCause.FIREBALL,p);
                Bukkit.getPluginManager().callEvent(ignite);
                if(!ignite.isCancelled()&&b.getType().isAir())b.setType(Material.FIRE);
                break;
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void hit(ProjectileHitEvent e) {
        Shot shot=shots.remove(e.getEntity().getUniqueId());if(shot==null)return;
        boolean denied=e.isCancelled();e.setCancelled(true);
        Location at=e.getEntity().getLocation();e.getEntity().remove();
        if(denied||!shot.owner.isOnline()||shot.owner.isDead()||shot.owner.getWorld()!=at.getWorld()||!c.loaded(at))return;
        if(shot.spell==Spell.SHULKER_LEVITATION) {
            if(e.getHitEntity() instanceof LivingEntity target&&c.enemy(shot.owner,target)&&c.affect(shot.owner,target,shot.spell)) {
                c.damage(shot.owner,target,c.configuredDamage("damage.shulker-impact",20),DamageType.MAGIC);
                c.potion(target,PotionEffectType.LEVITATION,80,1);
            }
        } else shot.impact.accept(at);
    }
    private boolean managed(Entity e){return e.getPersistentDataContainer().has(managed,PersistentDataType.BYTE);}
    @EventHandler(priority=EventPriority.HIGHEST) public void nativeDamage(EntityDamageByEntityEvent e){if(managed(e.getDamager()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void nativeExplosion(EntityExplodeEvent e){if(managed(e.getEntity()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST) public void nativeIgnite(BlockIgniteEvent e){if(e.getIgnitingEntity()!=null&&managed(e.getIgnitingEntity()))e.setCancelled(true);}
    public void close(){shots.values().forEach(s->s.projectile.remove());shots.clear();}
}
