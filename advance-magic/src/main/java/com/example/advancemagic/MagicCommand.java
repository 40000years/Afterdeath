package com.example.advancemagic;

import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class MagicCommand implements TabExecutor {
    private final AdvanceMagicPlugin plugin;
    public MagicCommand(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length==0||args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE+"Advance Magic: right-click a wand to cast.");
            for(Spell s:Spell.values())sender.sendMessage(ChatColor.AQUA+s.id()+ChatColor.GRAY+" | "+s.mana+" mana | "+s.cooldown+"s | Core: "+s.core);
            sender.sendMessage(ChatColor.GRAY+"Craft: 8 Nether Stars surrounding the core. Allies use scoreboard teams.");return true;
        }
        if(args[0].equalsIgnoreCase("mana")&&sender instanceof Player p){
            plugin.casts().actionbar(p,"");
            var a=plugin.mana().account(p);
            long lastDrink=p.getPersistentDataContainer().getOrDefault(plugin.mana().key("last_dragon_drink"),org.bukkit.persistence.PersistentDataType.LONG,0L);
            long cooldownMs=86_400_000L;
            long diff=System.currentTimeMillis()-lastDrink;
            String dbStatus;
            if(a.maxMana()>=300.0) dbStatus=ChatColor.LIGHT_PURPLE+"สูงสุดแล้ว (300/300)";
            else if(diff>=cooldownMs) dbStatus=ChatColor.GREEN+"พร้อมดื่มวันนี้ (+Max Mana & Regen)";
            else {
                long rem=cooldownMs-diff;
                long h=rem/3_600_000L, m=(rem%3_600_000L)/60_000L;
                dbStatus=ChatColor.YELLOW+"รออีก "+(h>0?h+" ชม. ":"")+m+" นาที";
            }
            p.sendMessage(ChatColor.LIGHT_PURPLE+"[Advance Magic] "+ChatColor.AQUA+"Mana: "+String.format(Locale.ROOT,"%.1f/%.1f",a.manaExact(),a.maxMana())+
                ChatColor.WHITE+" | Regen: "+ChatColor.GREEN+String.format(Locale.ROOT,"%.1f/s",a.regenRate())+
                ChatColor.WHITE+" | Dragon's Breath: "+dbStatus);
            return true;
        }
        if(args[0].equalsIgnoreCase("pack")) {
            if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            if(args.length>1&&args[1].equalsIgnoreCase("resend")&&sender instanceof Player p)plugin.packs().offer(p);
            plugin.packs().describe(sender);return true;
        }
        if(args[0].equalsIgnoreCase("give")) {
            if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            if(args.length!=3){sender.sendMessage("/magic give <player> <spell>");return true;}
            Player p=Bukkit.getPlayerExact(args[1]);Spell spell=Spell.parse(args[2]);
            if(p==null||spell==null){sender.sendMessage(ChatColor.RED+"Unknown online player or spell. Use /magic list.");return true;}
            if(p.getInventory().firstEmpty()<0){sender.sendMessage(ChatColor.RED+"Player inventory is full.");return true;}
            p.getInventory().addItem(plugin.wands().create(spell));sender.sendMessage("Gave "+spell.title+" Wand to "+p.getName());return true;
        }
        sender.sendMessage("/magic [list|mana|pack [resend]|give <player> <spell>]");return true;
    }
    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> values=new ArrayList<>();
        if(args.length==1){values.addAll(List.of("list","mana"));if(sender.hasPermission("advance-magic.admin"))values.addAll(List.of("give","pack"));}
        if(args.length==2&&sender.hasPermission("advance-magic.admin")&&args[0].equalsIgnoreCase("pack"))values.add("resend");
        if(sender.hasPermission("advance-magic.admin")&&args.length>=2&&args[0].equalsIgnoreCase("give")) {
            if(args.length==2)for(Player p:Bukkit.getOnlinePlayers())values.add(p.getName());
            if(args.length==3)for(Spell s:Spell.values())values.add(s.id());
        }
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
