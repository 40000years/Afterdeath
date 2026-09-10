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
        void update(){natural.add(projectile.getVelocity().subtract(last));last=natural.clone().multiply(0.1);projectile.setVelocity(last);}
        void restore(){if(projectile.isValid())projectile.setVelocity(natural.add(projectile.getVelocity().subtract(last)));}
    }
    private final Map<UUID,Slowed> slowed=new HashMap<>();
    public AreaSpells(MagicContext c){this.c=c;}
    public boolean lightning(Player p) {
        Location at=c.targetPoint(p,30);if(at==null)return false;
        // The vanilla effect sends native lightning packets without uncontrolled fire or extra damage.
        at.getWorld().strikeLightningEffect(at);
        for(var e:c.nearby(p,at,5,false))if(c.affect(p,e,Spell.LIGHTNING_STRIKE)) {
            c.damage(p,e,c.configuredDamage("damage.lightning",60),DamageType.LIGHTNING_BOLT);
            c.potion(e,PotionEffectType.SLOWNESS,40,2);
        }
        c.ring(at,5,Spell.LIGHTNING_STRIKE);
        // Stage 2: Static Overcharge Wave 10 ticks later (0.5s delay)
        c.plugin.effects().start(p,15,(effect,age)->{
            if(!c.loaded(at))return false;
            if(age==10) {
                at.getWorld().playSound(at,Sound.ENTITY_LIGHTNING_BOLT_IMPACT,1.3f,1.8f);
                c.ring(at,7,Spell.LIGHTNING_STRIKE);
                c.particles(at.clone().add(0,0.5,0),Particle.ELECTRIC_SPARK,45,2.2);
                for(var e:c.nearby(p,at,7,false))if(c.affect(p,e,Spell.LIGHTNING_STRIKE)) {
                    c.damage(p,e,c.configuredDamage("damage.lightning-secondary",30),DamageType.LIGHTNING_BOLT);
                    c.potion(e,PotionEffectType.BLINDNESS,40,0);
                    c.potion(e,PotionEffectType.NAUSEA,60,0);
                }
            }
            return true;
        });
        return true;
    }
    public boolean frost(Player p) {
        Location center=p.getLocation();
        // Stage 1: Flash Freeze
        for(var e:c.nearby(p,center,7,false))if(c.affect(p,e,Spell.FROST_NOVA)) {
            c.potion(e,PotionEffectType.SLOWNESS,120,3);c.plugin.statuses().freeze(p,e);
        }
        c.plugin.effects().start(p,21,(effect,age)->{
            if(age%2==0)c.ring(center,Math.min(7,0.7+age*0.35),Spell.FROST_NOVA);
            c.particles(center.clone().add(0,0.5,0),Particle.SNOWFLAKE,8,2);
            // Stage 2: Glacial Shatter Detonation at culmination (tick 20)
            if(age==20) {
                center.getWorld().playSound(center,Sound.BLOCK_GLASS_BREAK,1.3f,0.6f);
                center.getWorld().playSound(center,Sound.ENTITY_PLAYER_HURT_FREEZE,1.2f,0.9f);
                c.ring(center,7.5,Spell.FROST_NOVA);
                c.particles(center.clone().add(0,0.5,0),Particle.SNOWFLAKE,40,2.5);
                for(var e:c.nearby(p,center,7.5,false))if(c.affect(p,e,Spell.FROST_NOVA)) {
                    c.damage(p,e,c.configuredDamage("damage.frost-shatter",35),DamageType.FREEZE);
                    Vector push=e.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if(push.lengthSquared()>0.01)e.setVelocity(push.normalize().multiply(0.6).setY(0.25));
                }
            }
            return true;
        });return true;
    }
    public boolean wall(Player p) {
        Vector facing=p.getLocation().getDirection().setY(0);
        if(facing.lengthSquared()<0.01)facing=new Vector(0,0,1);else facing.normalize();
        Location center=p.getLocation().add(facing.clone().multiply(3));
        boolean xNormal=Math.abs(facing.getX())>Math.abs(facing.getZ());
        int nx = xNormal ? (facing.getX() > 0 ? 1 : -1) : 0;
        int nz = xNormal ? 0 : (facing.getZ() > 0 ? 1 : -1);
        int cx=center.getBlockX(),cy=center.getBlockY(),cz=center.getBlockZ();
        List<Location> cells=new ArrayList<>();
        // 7 blocks wide, 4 blocks high, 2 blocks thick double-layer fortress
        double minX=Double.MAX_VALUE,minY=Double.MAX_VALUE,minZ=Double.MAX_VALUE;
        double maxX=-Double.MAX_VALUE,maxY=-Double.MAX_VALUE,maxZ=-Double.MAX_VALUE;
        for(int layer=0;layer<2;layer++)for(int side=-3;side<=3;side++)for(int y=0;y<4;y++) {
            int bx = cx + (xNormal ? layer*nx : side);
            int by = cy + y;
            int bz = cz + (xNormal ? side : layer*nz);
            Location cell=new Location(p.getWorld(),bx,by,bz);
            if(!c.loaded(cell))return false;
            if(cell.getBlock().isPassable())cells.add(cell.add(0.5,0,0.5));
            minX=Math.min(minX,bx);minY=Math.min(minY,by);minZ=Math.min(minZ,bz);
            maxX=Math.max(maxX,bx+1);maxY=Math.max(maxY,by+1);maxZ=Math.max(maxZ,bz+1);
        }
        if(cells.isEmpty())return false;
        BoundingBox box=new BoundingBox(minX,minY,minZ,maxX,maxY,maxZ);
        UUID id=UUID.randomUUID();World world=p.getWorld();
        List<FallingBlock> blocks=new ArrayList<>();
        var effect=c.plugin.effects().start(p,100,(scope,age)->{
            if(!c.loaded(center))return false;
            for(FallingBlock block:blocks)if(block.isValid()){block.setVelocity(new Vector());block.setTicksLived(1);}
            // Look ahead across the entire movement segment, including high-speed arrows.
            for(Entity entity:world.getNearbyEntities(center.clone().add(0,2.0,0),8,8,8))
                if(entity instanceof Projectile projectile&&blocksProjectile(world,projectile.getLocation().toVector(),projectile.getLocation().toVector().add(projectile.getVelocity())))projectile.remove();
            if(age%10==0)c.particles(center.clone().add(0,2,0),Particle.CLOUD,12,2.0);
            return true;
        });
        walls.put(id,box);wallWorlds.put(id,world);
        // Stage 2: Fortress crumble burst on wall collapse
        effect.onClose(()->{
            walls.remove(id);wallWorlds.remove(id);blocks.forEach(b->wallBlocks.remove(b.getUniqueId()));
            if(c.loaded(center)) {
                world.playSound(center,Sound.BLOCK_DEEPSLATE_BREAK,1.2f,0.8f);
                c.particles(center.clone().add(0,1.5,0),Particle.CAMPFIRE_COSY_SMOKE,30,2.0);
                for(var e:c.nearby(p,center,4.5,false))if(c.affect(p,e,Spell.EARTH_WALL)) {
                    Vector push=e.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if(push.lengthSquared()>0.01)e.setVelocity(push.normalize().multiply(0.5).setY(0.2));
                }
            }
        });
        try {
            for(Location cell:cells) {
                FallingBlock block=effect.track(world.spawnFallingBlock(cell,Material.DEEPSLATE_BRICKS.createBlockData()));
                block.setGravity(false);block.setDropItem(false);block.setHurtEntities(false);block.setInvulnerable(true);
                wallBlocks.add(block.getUniqueId());blocks.add(block);
            }
            // Stage 1: Tectonic Rupture Shockwave upon creation
            Location ruptureCenter=center.clone().add(facing.clone().multiply(1.5));
            world.playSound(ruptureCenter,Sound.ENTITY_IRON_GOLEM_ATTACK,1.2f,0.6f);
            world.playSound(ruptureCenter,Sound.BLOCK_STONE_BREAK,1.5f,0.8f);
            c.particles(ruptureCenter,Particle.CAMPFIRE_COSY_SMOKE,25,1.5);
            c.particles(ruptureCenter,Particle.EXPLOSION,2,0.8);
            for(var e:c.nearby(p,ruptureCenter,4.5,false))if(c.affect(p,e,Spell.EARTH_WALL)) {
                c.damage(p,e,c.configuredDamage("damage.earth-wall-rupture",25),DamageType.MOB_ATTACK);
                c.potion(e,PotionEffectType.SLOWNESS,60,3);
                Vector knock=e.getLocation().toVector().subtract(center.toVector()).setY(0);
                if(knock.lengthSquared()<0.01)knock=facing.clone();
                e.setVelocity(knock.normalize().multiply(0.8).setY(0.35));
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
        var effect=c.plugin.effects().start(p,300,(scope,age)->{
            if(!c.loaded(center))return false;
            if(age%5==0) {
                c.ring(center,6,Spell.TIME_DILATION);
                c.ring(center.clone().add(0,3,0),Math.sqrt(27),Spell.TIME_DILATION);
                for(var e:c.nearby(p,center,6,false))if(c.affect(p,e,Spell.TIME_DILATION)) {
                    c.potion(e,PotionEffectType.SLOWNESS,10,6);
                    c.potion(e,PotionEffectType.MINING_FATIGUE,10,4);
                    c.potion(e,PotionEffectType.WEAKNESS,10,2);
                }
                for(var ally:c.nearby(p,center,6,true)) {
                    c.potion(ally,PotionEffectType.SPEED,10,1);
                    c.potion(ally,PotionEffectType.HASTE,10,1);
                }
            }
            // Stage 2: Temporal Shockwave Pulses every 50 ticks (2.5s)
            if(age>0&&age%50==0) {
                center.getWorld().playSound(center,Sound.BLOCK_BEACON_POWER_SELECT,1.0f,1.5f);
                c.particles(center.clone().add(0,1,0),Particle.ENCHANT,30,2.5);
                for(var e:c.nearby(p,center,6,false))if(c.affect(p,e,Spell.TIME_DILATION)) {
                    Vector push=e.getLocation().toVector().subtract(center.toVector()).setY(0);
                    if(push.lengthSquared()>0.01)e.setVelocity(push.normalize().multiply(0.6).setY(0.2));
                }
                for(var ally:c.nearby(p,center,6,true)) {
                    c.potion(ally,PotionEffectType.ABSORPTION,60,1);
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
