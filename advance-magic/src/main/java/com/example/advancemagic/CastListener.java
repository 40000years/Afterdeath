package com.example.advancemagic;

import com.example.advancemagic.api.MagicCastEvent;
import com.example.advancemagic.spell.Spell;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import java.util.*;

public final class CastListener implements Listener {
    private final AdvanceMagicPlugin plugin;
    private final Map<UUID,Long> lastInput=new HashMap<>();
    private final Set<UUID> casting=new HashSet<>();
    public CastListener(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    public void actionbar(Player p,String message) {
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,TextComponent.fromLegacyText(ChatColor.AQUA+"Mana "+plugin.mana().account(p).mana()+"/100  "+ChatColor.WHITE+message));
    }
    private Spell held(Player p,EquipmentSlot hand) {
        if(hand==null)return null;
        // A main-hand wand takes priority, avoiding duplicate casts from the offhand event.
        if(hand==EquipmentSlot.OFF_HAND&&plugin.wands().spell(p.getInventory().getItemInMainHand())!=null)return null;
        return plugin.wands().spell(hand==EquipmentSlot.HAND?p.getInventory().getItemInMainHand():p.getInventory().getItemInOffHand());
    }
    @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        if((e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK)||e.useItemInHand()==Event.Result.DENY)return;
        Spell spell=held(e.getPlayer(),e.getHand());if(spell==null)return;
        e.setCancelled(true);cast(e.getPlayer(),spell);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void entity(PlayerInteractEntityEvent e) {
        Spell spell=held(e.getPlayer(),e.getHand());if(spell==null)return;
        e.setCancelled(true);cast(e.getPlayer(),spell);
    }
    public boolean cast(Player p,Spell spell) {
        long now=System.currentTimeMillis();UUID id=p.getUniqueId();
        if(!p.hasPermission("advance-magic.cast")){actionbar(p,"You cannot cast spells.");return false;}
        if(!p.isOnline()||p.isDead()||p.getGameMode()==GameMode.SPECTATOR)return false;
        if(casting.contains(id)||now-lastInput.getOrDefault(id,0L)<150)return false;
        lastInput.put(id,now);casting.add(id);
        try {
            var account=plugin.mana().account(p);
            long remaining=account.remaining(spell.id(),now);
            if(remaining>0){actionbar(p,spell.title+": "+String.format(Locale.ROOT,"%.1fs",remaining/1000.0));return false;}
            if(account.mana()<spell.mana){actionbar(p,"Need "+spell.mana+" mana.");return false;}
            if(!plugin.effects().hasCapacity()){actionbar(p,"Too many active spells. Try again shortly.");return false;}
            MagicCastEvent event=new MagicCastEvent(p,spell);Bukkit.getPluginManager().callEvent(event);
            if(event.isCancelled()){actionbar(p,"Magic is blocked here.");return false;}
            if(!account.reserve(spell.id(),spell.mana,spell.cooldown,now))return false;
            boolean success=false;
            try { success=plugin.spells().cast(p,spell); }
            catch(RuntimeException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cast failed: "+spell,ex);}
            if(!success){account.refund(spell.id(),spell.mana);actionbar(p,"No valid target or safe destination.");}
            else {
                if(spell!=Spell.INVISIBILITY_SHROUD)plugin.statuses().reveal(p);
                actionbar(p,spell.title+" | CD "+spell.cooldown+"s");
            }
            plugin.mana().save(p);return success;
        } finally {casting.remove(id);}
    }
    public void quit(Player p){lastInput.remove(p.getUniqueId());casting.remove(p.getUniqueId());}
}
