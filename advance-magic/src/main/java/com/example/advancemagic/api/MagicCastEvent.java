package com.example.advancemagic.api;

import com.example.advancemagic.spell.Spell;
import org.bukkit.entity.Player;
import org.bukkit.event.*;

/** Region/arena integrations may cancel a cast before mana or effects are committed. */
public final class MagicCastEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS=new HandlerList();
    private final Player caster;
    private final Spell spell;
    private boolean cancelled;
    public MagicCastEvent(Player caster,Spell spell){this.caster=caster;this.spell=spell;}
    public Player getCaster(){return caster;}
    public Spell getSpell(){return spell;}
    public boolean isCancelled(){return cancelled;}
    public void setCancelled(boolean value){cancelled=value;}
    public HandlerList getHandlers(){return HANDLERS;}
    public static HandlerList getHandlerList(){return HANDLERS;}
}
