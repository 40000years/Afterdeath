package com.example.advancemagic.spell;

import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

public final class MobilitySpells {
    private final MagicContext c;
    public MobilitySpells(MagicContext c){this.c=c;}
    public boolean shadowStep(Player p) {
        if(p.isInsideVehicle())return false;
        Location start=p.getLocation(),last=null;
        Vector dir=start.getDirection();double wallStart=-1;boolean passedWall=false;
        for(double d=0.25;d<=12;d+=0.25) {
            Location next=start.clone().add(dir.clone().multiply(d));
            if(!c.loaded(next)||!c.loaded(next.clone().add(0,1.8,0)))break;
            if(!c.safeBody(next)) {
                if(passedWall)break;
                if(wallStart<0)wallStart=d;
                // A one-block wall plus the player's 0.6-block body footprint.
                if(d-wallStart>1.5)break;
            } else {
                if(wallStart>=0){passedWall=true;wallStart=-1;}
                last=next;
            }
        }
        if(last==null||last.distanceSquared(start)<1||!p.teleport(last,PlayerTeleportEvent.TeleportCause.PLUGIN))return false;
        p.setFallDistance(0);
        c.particles(start.clone().add(0,1,0),Particle.PORTAL,28,0.4);
        c.particles(last.clone().add(0,1,0),Particle.PORTAL,28,0.4);
        p.getWorld().playSound(last,Sound.ENTITY_ENDERMAN_TELEPORT,0.7f,1.2f);return true;
    }
    public boolean shroud(Player p){c.plugin.statuses().shroud(p);return true;}
    public boolean armor(Player p) {
        c.potion(p,PotionEffectType.RESISTANCE,1200,3);
        c.potion(p,PotionEffectType.FIRE_RESISTANCE,1200,0);
        c.potion(p,PotionEffectType.ABSORPTION,1200,3);
        c.potion(p,PotionEffectType.STRENGTH,1200,1);
        c.potion(p,PotionEffectType.SPEED,1200,0);
        c.plugin.statuses().armor(p);
        c.ring(p.getLocation(),1.5,Spell.IRON_ARMOR);
        p.getWorld().playSound(p.getLocation(),Sound.ITEM_ARMOR_EQUIP_NETHERITE,1.2f,0.8f);
        p.getWorld().playSound(p.getLocation(),Sound.BLOCK_ANVIL_USE,0.8f,1.2f);
        return true;
    }
    public boolean bloom(Player p) {
        Location center=p.getLocation();
        java.util.List<PotionEffectType> negative=java.util.List.of(
            PotionEffectType.POISON,PotionEffectType.WITHER,PotionEffectType.SLOWNESS,
            PotionEffectType.WEAKNESS,PotionEffectType.BLINDNESS,PotionEffectType.NAUSEA,
            PotionEffectType.DARKNESS,PotionEffectType.MINING_FATIGUE,PotionEffectType.HUNGER
        );
        for(var ally:c.nearby(p,center,8,true))if(c.affect(p,ally,Spell.NATURES_BLOOM)) {
            for(PotionEffectType neg:negative)ally.removePotionEffect(neg);
            c.potion(ally,PotionEffectType.REGENERATION,900,3);
            c.potion(ally,PotionEffectType.ABSORPTION,900,4);
            c.potion(ally,PotionEffectType.STRENGTH,900,1);
            c.potion(ally,PotionEffectType.SPEED,900,1);
            if(ally instanceof Player pl) c.heal(pl,12.0);
            else if(ally.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)!=null)
                ally.setHealth(Math.min(ally.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue(),ally.getHealth()+12.0));
            c.particles(ally.getLocation().add(0,1,0),Particle.HAPPY_VILLAGER,25,0.6);
        }
        c.plugin.effects().start(p,30,(effect,age)->{if(age%3==0)c.ring(center,Math.min(8,age/3.0+0.5),Spell.NATURES_BLOOM);return true;});
        p.getWorld().playSound(center,Sound.BLOCK_BEACON_ACTIVATE,1.0f,1.4f);
        return true;
    }
}
