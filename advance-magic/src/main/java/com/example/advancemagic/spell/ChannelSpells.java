package com.example.advancemagic.spell;

import org.bukkit.Particle;
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
            if(age>0&&age%20==0) {
                if(!c.affect(p,target,Spell.SOUL_DRAIN))return false;
                double drained=c.damage(p,target,40,DamageType.MAGIC);
                c.heal(p,drained);
            }
            return true;
        });return true;
    }
}
