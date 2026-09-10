package com.example.advancemagic.mana;

import com.example.advancemagic.spell.Spell;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.TextComponent;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class ManaService {
    private final Plugin plugin;
    private final Map<UUID,CastAccount> accounts=new HashMap<>();
    public ManaService(Plugin plugin) { this.plugin=plugin; }
    public NamespacedKey key(String id) { return new NamespacedKey(plugin,id); }

    public CastAccount account(Player p) {
        return accounts.computeIfAbsent(p.getUniqueId(),id->{
            var data=p.getPersistentDataContainer();
            double maxMana=100.0;
            if (data.has(key("max_mana"),PersistentDataType.DOUBLE)) {
                maxMana=data.get(key("max_mana"),PersistentDataType.DOUBLE);
            } else if (data.has(key("max_mana"),PersistentDataType.INTEGER)) {
                maxMana=data.get(key("max_mana"),PersistentDataType.INTEGER);
            }
            double regen=data.getOrDefault(key("mana_regen"),PersistentDataType.DOUBLE,2.0);
            double mana=maxMana;
            if (data.has(key("mana"),PersistentDataType.DOUBLE)) {
                mana=data.get(key("mana"),PersistentDataType.DOUBLE);
            } else if (data.has(key("mana"),PersistentDataType.INTEGER)) {
                mana=data.get(key("mana"),PersistentDataType.INTEGER);
            }
            CastAccount a=new CastAccount(mana,maxMana,regen);
            for (Spell s:Spell.values()) a.restore(s.id(),data.getOrDefault(key("cd_"+s.id()),PersistentDataType.LONG,0L));
            return a;
        });
    }

    public void save(Player p) {
        CastAccount a=accounts.get(p.getUniqueId()); if(a==null)return;
        var data=p.getPersistentDataContainer();
        data.set(key("mana"),PersistentDataType.DOUBLE,a.manaExact());
        data.set(key("max_mana"),PersistentDataType.DOUBLE,a.maxMana());
        data.set(key("mana_regen"),PersistentDataType.DOUBLE,a.regenRate());
        for(Spell s:Spell.values()) data.set(key("cd_"+s.id()),PersistentDataType.LONG,a.end(s.id()));
    }

    public void quit(Player p) { save(p); accounts.remove(p.getUniqueId()); }
    public void regenerate(Player p) { if(account(p).regenerate()) save(p); }

    public boolean drinkDragonBreath(Player p, ItemStack item, EquipmentSlot hand) {
        long now=System.currentTimeMillis();
        var data=p.getPersistentDataContainer();
        long lastDrink=data.getOrDefault(key("last_dragon_drink"),PersistentDataType.LONG,0L);
        long cooldownMs=86_400_000L; // 24 hours

        if(now-lastDrink<cooldownMs) {
            long remaining=cooldownMs-(now-lastDrink);
            long hours=remaining/3_600_000L;
            long minutes=(remaining%3_600_000L)/60_000L;
            long seconds=(remaining%60_000L)/1000L;
            String timeStr=hours>0?String.format(Locale.ROOT,"%d ชม. %d นาที",hours,minutes)
                                 :String.format(Locale.ROOT,"%d นาที %d วินาที",minutes,seconds);
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.RED+"ดื่ม Dragon's Breath ได้วันละ 1 ครั้งเท่านั้น (รออีก "+timeStr+")"));
            p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,1.0f,1.0f);
            return false;
        }

        CastAccount account=account(p);
        double currentMax=account.maxMana();
        if(currentMax>=300.0) {
            p.spigot().sendMessage(ChatMessageType.ACTION_BAR,
                TextComponent.fromLegacyText(ChatColor.LIGHT_PURPLE+"คุณมี Max Mana ถึงขีดจำกัดสูงสุดแล้ว (300/300)"));
            p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,1.0f,1.0f);
            return false;
        }

        // Deduct 1 Dragon's Breath and give Glass Bottle
        if(p.getGameMode()!=GameMode.CREATIVE) {
            if(item.getAmount()>1) {
                item.setAmount(item.getAmount()-1);
                Map<Integer,ItemStack> leftover=p.getInventory().addItem(new ItemStack(Material.GLASS_BOTTLE));
                if(!leftover.isEmpty()) {
                    for(ItemStack drop:leftover.values())p.getWorld().dropItemNaturally(p.getLocation(),drop);
                }
            } else {
                if(hand==EquipmentSlot.HAND) {
                    p.getInventory().setItemInMainHand(new ItemStack(Material.GLASS_BOTTLE));
                } else {
                    p.getInventory().setItemInOffHand(new ItemStack(Material.GLASS_BOTTLE));
                }
            }
        }

        // Progression: <150 -> +5, <200 -> +2, <300 -> +0.5, cap 300
        double gain;
        if(currentMax<150.0) {
            gain=5.0;
        } else if(currentMax<200.0) {
            gain=2.0;
        } else {
            gain=0.5;
        }
        double newMax=Math.min(300.0,currentMax+gain);
        double newRegen=Math.min(15.0,account.regenRate()+0.2);
        int drinks=data.getOrDefault(key("dragon_drinks"),PersistentDataType.INTEGER,0)+1;

        account.setMaxMana(newMax);
        account.setRegenRate(newRegen);
        account.setMana(newMax); // Full replenish

        data.set(key("last_dragon_drink"),PersistentDataType.LONG,now);
        data.set(key("max_mana"),PersistentDataType.DOUBLE,newMax);
        data.set(key("mana_regen"),PersistentDataType.DOUBLE,newRegen);
        data.set(key("mana"),PersistentDataType.DOUBLE,newMax);
        data.set(key("dragon_drinks"),PersistentDataType.INTEGER,drinks);

        // Audio-visual feedback
        p.playSound(p.getLocation(),Sound.ENTITY_GENERIC_DRINK,1.0f,1.0f);
        p.playSound(p.getLocation(),Sound.ENTITY_ENDER_DRAGON_GROWL,0.7f,1.3f);
        p.playSound(p.getLocation(),Sound.ENTITY_PLAYER_LEVELUP,1.0f,1.2f);
        p.getWorld().spawnParticle(Particle.DRAGON_BREATH,p.getLocation().add(0,1,0),45,0.4,0.6,0.4,0.05);
        p.getWorld().spawnParticle(Particle.PORTAL,p.getLocation().add(0,1,0),30,0.5,0.5,0.5,0.1);

        String gainStr=(gain==(long)gain)?String.format(Locale.ROOT,"%d",(long)gain):String.format(Locale.ROOT,"%.1f",gain);
        String maxStr=(newMax==(long)newMax)?String.format(Locale.ROOT,"%d",(long)newMax):String.format(Locale.ROOT,"%.1f",newMax);
        String regenStr=String.format(Locale.ROOT,"%.1f",newRegen);

        p.sendTitle(ChatColor.LIGHT_PURPLE+""+ChatColor.BOLD+"DRAGON'S ESSENCE",
            ChatColor.AQUA+"Max Mana: "+maxStr+" (+"+gainStr+") "+ChatColor.WHITE+"| "+ChatColor.GREEN+"Regen: "+regenStr+"/s",
            10,60,20);
        p.sendMessage(ChatColor.LIGHT_PURPLE+"[Advance Magic] "+ChatColor.WHITE+"คุณดื่ม "+ChatColor.DARK_PURPLE+"Dragon's Breath "+
            ChatColor.WHITE+"ซึมซับพลังมังกรโบราณ! Max Mana: "+ChatColor.AQUA+maxStr+ChatColor.GREEN+" (+"+gainStr+")"+
            ChatColor.WHITE+" | Mana Regen: "+ChatColor.AQUA+regenStr+"/s"+
            ChatColor.GRAY+" (ดื่มสะสม "+drinks+" ครั้ง)");

        save(p);
        return true;
    }
}
