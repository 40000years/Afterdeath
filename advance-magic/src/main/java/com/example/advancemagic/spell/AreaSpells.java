package com.example.advancemagic.spell;

import com.example.advancemagic.effect.Geometry;
import org.bukkit.*;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import java.util.*;

public final class AreaSpells implements Listener {
    private final MagicContext c;
    private final Set<UUID> wallBlocks=new HashSet<>();
    private final Map<UUID,BoundingBox> walls=new HashMap<>();
    private final Map<UUID,World> wallWorlds=new HashMap<>();
    private final Map<UUID,Location> domes=new HashMap<>();
    private static final class Slowed {
        final Projectile projectile;
        Vector natural,last;
        Slowed(Projectile projectile){this.projectile=projectile;natural=projectile.getVelocity();last=natural.clone();}
        void update(){natural.add(projectile.getVelocity().subtract(last));last=natural.clone().multiply(0.2);projectile.setVelocity(last);}
        void restore(){if(projectile.isValid())projectile.setVelocity(natural.add(projectile.getVelocity().subtract(last)));}
    }
    private final Map<UUID,Slowed> slowed=new HashMap<>();
    public AreaSpells(MagicContext c){this.c=c;}
    public boolean lightning(Player p) {
        Location at=c.targetPoint(p,30);if(at==null)return false;
        // The vanilla effect sends native lightning packets without uncontrolled fire or extra damage.
        at.getWorld().strikeLightningEffect(at);
        for(var e:c.nearby(p,at,5,false))if(c.affect(p,e,Spell.LIGHTNING_STRIKE))
            c.damage(p,e,c.configuredDamage("damage.lightning",12),DamageType.LIGHTNING_BOLT);
        c.ring(at,5,Spell.LIGHTNING_STRIKE);return true;
    }
    public boolean frost(Player p) {
        Location center=p.getLocation();
        for(var e:c.nearby(p,center,7,false))if(c.affect(p,e,Spell.FROST_NOVA)) {
            c.potion(e,PotionEffectType.SLOWNESS,120,3);c.plugin.statuses().freeze(p,e);
        }
        c.plugin.effects().start(p,20,(effect,age)->{
            if(age%2==0)c.ring(center,Math.min(7,0.7+age*0.35),Spell.FROST_NOVA);
            c.particles(center.clone().add(0,0.5,0),Particle.SNOWFLAKE,8,2);return true;
        });return true;
    }
    public boolean wall(Player p) {
        Vector facing=p.getLocation().getDirection().setY(0);
        if(facing.lengthSquared()<0.01)facing=new Vector(0,0,1);else facing.normalize();
        Location center=p.getLocation().add(facing.clone().multiply(3));
        boolean xNormal=Math.abs(facing.getX())>Math.abs(facing.getZ());
        int cx=center.getBlockX(),cy=center.getBlockY(),cz=center.getBlockZ();
        List<Location> cells=new ArrayList<>();
        for(int side=-2;side<=2;side++)for(int y=0;y<3;y++) {
            Location cell=new Location(p.getWorld(),cx+(xNormal?0:side),cy+y,cz+(xNormal?side:0));
            if(!c.loaded(cell)||!cell.getBlock().isPassable())return false;
            cells.add(cell.add(0.5,0,0.5));
        }
        BoundingBox box=new BoundingBox(cx-(xNormal?0:2),cy,cz-(xNormal?2:0),cx+(xNormal?1:3),cy+3,cz+(xNormal?3:1));
        UUID id=UUID.randomUUID();World world=p.getWorld();
        List<FallingBlock> blocks=new ArrayList<>();
        var effect=c.plugin.effects().start(p,100,(scope,age)->{
            if(!c.loaded(center))return false;
            for(FallingBlock block:blocks)if(block.isValid()){block.setVelocity(new Vector());block.setTicksLived(1);}
            // Look ahead across the entire movement segment, including high-speed arrows.
            for(Entity entity:world.getNearbyEntities(center.clone().add(0,1.5,0),6,6,6))
                if(entity instanceof Projectile projectile&&blocksProjectile(world,projectile.getLocation().toVector(),projectile.getLocation().toVector().add(projectile.getVelocity())))projectile.remove();
            if(age%10==0)c.particles(center.clone().add(0,1,0),Particle.CLOUD,6,1);
            return true;
        });
        walls.put(id,box);wallWorlds.put(id,world);
        effect.onClose(()->{walls.remove(id);wallWorlds.remove(id);blocks.forEach(b->wallBlocks.remove(b.getUniqueId()));});
        try {
            for(Location cell:cells) {
                FallingBlock block=effect.track(world.spawnFallingBlock(cell,Material.DEEPSLATE_BRICKS.createBlockData()));
                block.setGravity(false);block.setDropItem(false);block.setHurtEntities(false);block.setInvulnerable(true);
                wallBlocks.add(block.getUniqueId());blocks.add(block);
            }
        }catch(RuntimeException ex){effect.close();throw ex;}
        return true;
    }
    public boolean blocksProjectile(World world,Vector from,Vector to) {
        for(var entry:walls.entrySet())if(wallWorlds.get(entry.getKey())==world&&Geometry.intersects(entry.getValue().clone().expand(0.2),from,to))return true;
        return false;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void land(EntityChangeBlockEvent e){if(wallBlocks.contains(e.getEntity().getUniqueId()))e.setCancelled(true);}
    public boolean time(Player p) {
        Location center=p.getLocation();UUID id=UUID.randomUUID();
        var effect=c.plugin.effects().start(p,80,(scope,age)->{
            if(!c.loaded(center))return false;
            if(age%5==0) {
                c.ring(center,6,Spell.TIME_DILATION);
                c.ring(center.clone().add(0,3,0),Math.sqrt(27),Spell.TIME_DILATION);
                for(var e:c.nearby(p,center,6,false))if(c.affect(p,e,Spell.TIME_DILATION)) {
                    c.potion(e,PotionEffectType.SLOWNESS,6,5);
                    c.potion(e,PotionEffectType.MINING_FATIGUE,6,4);
                }
            }
            return true;
        });
        domes.put(id,center);effect.onClose(()->domes.remove(id));return true;
    }
    public void tick() {
        Set<UUID> inside=new HashSet<>();
        for(Location center:domes.values())if(c.loaded(center))
            for(Entity e:center.getWorld().getNearbyEntities(center,6,6,6))
                if(e instanceof Projectile projectile&&e.getLocation().distanceSquared(center)<=36&&inside.size()<512) {
                    inside.add(e.getUniqueId());slowed.computeIfAbsent(e.getUniqueId(),key->new Slowed(projectile));
                }
        for(var entry:List.copyOf(slowed.entrySet())) {
            Slowed state=entry.getValue();
            if(!state.projectile.isValid()||!inside.contains(entry.getKey())){state.restore();slowed.remove(entry.getKey());}
            else state.update();
        }
    }
    public void close(){slowed.values().forEach(Slowed::restore);slowed.clear();domes.clear();walls.clear();wallWorlds.clear();wallBlocks.clear();}
}
