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
        var a=plugin.mana().account(p);
        String curStr=(a.manaExact()==(long)a.manaExact())?String.format(Locale.ROOT,"%d",(long)a.manaExact()):String.format(Locale.ROOT,"%.1f",a.manaExact());
        String maxStr=(a.maxMana()==(long)a.maxMana())?String.format(Locale.ROOT,"%d",(long)a.maxMana()):String.format(Locale.ROOT,"%.1f",a.maxMana());
        p.spigot().sendMessage(ChatMessageType.ACTION_BAR,TextComponent.fromLegacyText(ChatColor.AQUA+"Mana "+curStr+"/"+maxStr+"  "+ChatColor.WHITE+message));
    }
    private ItemStack heldItem(Player p,EquipmentSlot hand) {
        if(hand==null)return null;
        // A main-hand wand takes priority, avoiding duplicate casts from the offhand event.
        if(hand==EquipmentSlot.OFF_HAND&&plugin.wands().spell(p.getInventory().getItemInMainHand())!=null)return null;
        return hand==EquipmentSlot.HAND?p.getInventory().getItemInMainHand():p.getInventory().getItemInOffHand();
    }
    private Spell held(Player p,EquipmentSlot hand) {
        return plugin.wands().spell(heldItem(p,hand));
    }
    @EventHandler(priority=EventPriority.HIGH) public void interact(PlayerInteractEvent e) {
        if((e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK))return;
        ItemStack item=e.getItem();
        if(item!=null&&item.getType()==Material.DRAGON_BREATH) {
            e.setCancelled(true);
            plugin.mana().drinkDragonBreath(e.getPlayer(),item,e.getHand());
            return;
        }
        if(e.useItemInHand()==Event.Result.DENY)return;
        ItemStack wandItem=heldItem(e.getPlayer(),e.getHand());
        Spell spell=plugin.wands().spell(wandItem);if(spell==null)return;
        e.setCancelled(true);cast(e.getPlayer(),spell,wandItem);
    }
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true) public void entity(PlayerInteractEntityEvent e) {
        ItemStack wandItem=heldItem(e.getPlayer(),e.getHand());
        Spell spell=plugin.wands().spell(wandItem);if(spell==null)return;
        e.setCancelled(true);cast(e.getPlayer(),spell,wandItem);
    }
    public boolean cast(Player p,Spell spell) {
        ItemStack item=plugin.wands().spell(p.getInventory().getItemInMainHand())==spell?p.getInventory().getItemInMainHand()
            :plugin.wands().spell(p.getInventory().getItemInOffHand())==spell?p.getInventory().getItemInOffHand():null;
        return cast(p,spell,item);
    }
    public boolean cast(Player p,Spell spell,ItemStack wandItem) {
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
            double effectiveCd=wandItem!=null?plugin.wands().getEffectiveCooldown(wandItem,spell):spell.cooldown;
            if(!account.reserve(spell.id(),spell.mana,effectiveCd,now))return false;
            boolean success=false;
            try { success=plugin.spells().cast(p,spell); }
            catch(RuntimeException ex){plugin.getLogger().log(java.util.logging.Level.SEVERE,"Cast failed: "+spell,ex);}
            if(!success){account.refund(spell.id(),spell.mana);actionbar(p,"No valid target or safe destination.");}
            else {
                if(spell!=Spell.INVISIBILITY_SHROUD)plugin.statuses().reveal(p);
                int casts=wandItem!=null?plugin.wands().recordCast(wandItem,spell):0;
                String cdStr=String.format(Locale.ROOT,"%.1f",effectiveCd);
                actionbar(p,spell.title+" | CD "+cdStr+"s"+(casts>0?" ("+casts+" casts)":""));
            }
            plugin.mana().save(p);return success;
        } finally {casting.remove(id);}
    }
    public void quit(Player p){lastInput.remove(p.getUniqueId());casting.remove(p.getUniqueId());}
}
