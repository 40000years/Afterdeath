package com.example.advancemagic.spell;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.damage.DamageType;
import org.bukkit.entity.*;

public final class ChannelSpells {
    private final MagicContext c;
    public ChannelSpells(MagicContext c){this.c=c;}
    public boolean soulDrain(Player p) {
        LivingEntity target=c.targetEntity(p,20);if(target==null)return false;
        c.plugin.effects().start(p,61,(effect,age)->{
            if(!c.enemy(p,target)||p.getWorld()!=target.getWorld()||p.getLocation().distanceSquared(target.getLocation())>400
                ||!c.clear(p.getEyeLocation(),target.getEyeLocation()))return false;
            if(age%3==0)c.beam(p.getEyeLocation(),target.getEyeLocation(),Particle.SOUL);
            // Stage 1: Tri-phase Soul Drain pulses
            if(age>0&&age%20==0) {
                if(!c.affect(p,target,Spell.SOUL_DRAIN))return false;
                double drained=c.damage(p,target,40,DamageType.MAGIC);
                c.heal(p,drained);
            }
            // Stage 2: Soul Nova Burst upon successful channel completion
            if(age==60) {
                Location at=target.getLocation();
                at.getWorld().playSound(at,Sound.BLOCK_SCULK_CATALYST_BLOOM,1.4f,1.2f);
                c.ring(at,5,Spell.SOUL_DRAIN);
                c.particles(at.clone().add(0,1,0),Particle.SOUL,35,1.2);
                c.particles(at.clone().add(0,1,0),Particle.SOUL_FIRE_FLAME,25,1.0);
                for(var e:c.nearby(p,at,5,false))if(c.affect(p,e,Spell.SOUL_DRAIN)) {
                    c.damage(p,e,c.configuredDamage("damage.soul-nova",35),DamageType.MAGIC);
                }
                c.heal(p,20.0);
                for(var ally:c.nearby(p,at,5,true))if(ally instanceof Player pl)c.heal(pl,15.0);
            }
            return true;
        });return true;
    }
}
