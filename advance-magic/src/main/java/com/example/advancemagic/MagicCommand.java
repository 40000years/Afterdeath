package com.example.advancemagic;

import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class MagicCommand implements TabExecutor {
    private final AdvanceMagicPlugin plugin;
    public MagicCommand(AdvanceMagicPlugin plugin){this.plugin=plugin;}
    public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        if(args.length==0||args[0].equalsIgnoreCase("list")) {
            sender.sendMessage(ChatColor.LIGHT_PURPLE+"Advance Magic: right-click a wand to cast.");
            for(Spell s:Spell.values())sender.sendMessage(ChatColor.AQUA+s.id()+ChatColor.GRAY+" | "+s.mana+" mana | "+s.cooldown+"s | Core: Core of "+com.example.advancemagic.item.WandService.coreTitle(s));
            sender.sendMessage(ChatColor.GRAY+"Craft: 8 Netherite Ingots / Nether Stars around a matching Evergarden Vault Core (mix allowed).");
            sender.sendMessage(ChatColor.YELLOW+"เมนูเสก/คราฟ: "+ChatColor.AQUA+"/magic items "+ChatColor.GREEN+"(หยิบคทา/แกนทันที) "+ChatColor.GRAY+"หรือหยิบจาก Bedrock Creative menu ได้โดยตรง");
            return true;
        }
        if(args[0].equalsIgnoreCase("items")||args[0].equalsIgnoreCase("craft")) {
            if(sender instanceof Player player)plugin.itemMenu().open(player,args[0].equalsIgnoreCase("items"));
            else sender.sendMessage("Use this menu from in-game.");
            return true;
        }
        if(args[0].equalsIgnoreCase("mana")&&sender instanceof Player p){
            plugin.casts().actionbar(p,"");
            var a=plugin.mana().account(p);
            long currentFullTime=p.getWorld().getFullTime();
            long currentDay=currentFullTime/24000L;
            long lastDay=p.getPersistentDataContainer().getOrDefault(plugin.mana().key("last_dragon_day"),org.bukkit.persistence.PersistentDataType.LONG,-1L);
            String dbStatus;
            if(a.maxMana()>=300.0) dbStatus=ChatColor.LIGHT_PURPLE+"สูงสุดแล้ว (300/300)";
            else if(currentDay!=lastDay) dbStatus=ChatColor.GREEN+"พร้อมดื่มวันนี้ในเกม (+Max Mana & Regen)";
            else {
                long dayTime=currentFullTime%24000L;
                long ticksRemaining=24000L-dayTime;
                long totalSec=Math.max(1L,ticksRemaining/20L);
                long m=totalSec/60L, s=totalSec%60L;
                dbStatus=ChatColor.YELLOW+"รอวันใหม่ในเกมอีก "+(m>0?m+" นาที ":"")+s+" วินาที (หรือนอนข้ามคืน)";
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
        if(args[0].equalsIgnoreCase("givecore")||(args[0].equalsIgnoreCase("give")&&args.length>=4&&args[3].equalsIgnoreCase("core"))) {
            if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            Player p;
            Spell spell;
            if(args[0].equalsIgnoreCase("givecore")&&args.length==2&&sender instanceof Player sp) {
                p=sp;
                spell=Spell.parse(args[1]);
            } else if(args.length>=3) {
                p=resolvePlayer(sender,args[1]);
                spell=Spell.parse(args[2]);
            } else {
                sender.sendMessage(ChatColor.YELLOW+"Usage: /magic givecore [player] <spell>");
                return true;
            }
            if(p==null||spell==null){sender.sendMessage(ChatColor.RED+"Unknown online player or spell. Use /magic list.");return true;}
            giveOrDrop(p,plugin.wands().createCore(spell));
            sender.sendMessage(ChatColor.GREEN+"Gave Core of "+com.example.advancemagic.item.WandService.coreTitle(spell)+" to "+p.getName());
            return true;
        }
        if(args[0].equalsIgnoreCase("give")) {
            if(!sender.hasPermission("advance-magic.admin")){sender.sendMessage(ChatColor.RED+"No permission.");return true;}
            Player p;
            Spell spell;
            boolean isCore=false;
            if(args.length==2&&sender instanceof Player sp) {
                p=sp;
                spell=Spell.parse(args[1]);
            } else if(args.length==3&&sender instanceof Player sp&&(args[2].equalsIgnoreCase("core")||args[2].equalsIgnoreCase("wand"))) {
                p=sp;
                spell=Spell.parse(args[1]);
                isCore=args[2].equalsIgnoreCase("core");
            } else if(args.length>=3) {
                p=resolvePlayer(sender,args[1]);
                spell=Spell.parse(args[2]);
                if(args.length>=4) isCore=args[3].equalsIgnoreCase("core");
            } else {
                sender.sendMessage(ChatColor.YELLOW+"Usage: /magic give [player] <spell> [wand|core]");
                return true;
            }
            if(p==null||spell==null){sender.sendMessage(ChatColor.RED+"Unknown online player or spell. Use /magic list.");return true;}
            ItemStack item=isCore?plugin.wands().createCore(spell):plugin.wands().create(spell);
            giveOrDrop(p,item);
            sender.sendMessage(ChatColor.GREEN+"Gave "+(isCore?"Core of "+com.example.advancemagic.item.WandService.coreTitle(spell):spell.title+" Wand")+" to "+p.getName());
            return true;
        }
        sender.sendMessage("/magic [list|mana|craft|items|pack [resend]|give [player] <spell> [wand|core]|givecore [player] <spell>]");return true;
    }

    private Player resolvePlayer(CommandSender sender, String targetName) {
        if(targetName==null||targetName.isEmpty()) return sender instanceof Player p ? p : null;
        if(targetName.equalsIgnoreCase("@s")||targetName.equalsIgnoreCase("@p")) {
            if(sender instanceof Player p) return p;
            return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        }
        if(targetName.equalsIgnoreCase("@a")) {
            if(sender instanceof Player p) return p;
            return Bukkit.getOnlinePlayers().stream().findFirst().orElse(null);
        }
        Player p=Bukkit.getPlayerExact(targetName);
        if(p!=null) return p;
        for(Player pl : Bukkit.getOnlinePlayers()) {
            if(pl.getName().equalsIgnoreCase(targetName)) return pl;
        }
        String dotTarget=targetName.startsWith(".") ? targetName : "." + targetName;
        for(Player pl : Bukkit.getOnlinePlayers()) {
            if(pl.getName().equalsIgnoreCase(dotTarget)) return pl;
            if(pl.getName().startsWith(".") && pl.getName().substring(1).equalsIgnoreCase(targetName)) return pl;
        }
        return Bukkit.getPlayer(targetName);
    }

    private void giveOrDrop(Player p, ItemStack item) {
        var leftover=p.getInventory().addItem(item);
        if(!leftover.isEmpty()) {
            leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(), drop));
        }
        p.updateInventory();
    }

    public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> values=new ArrayList<>();
        if(args.length==1){values.addAll(List.of("list","mana","craft"));if(sender.hasPermission("advance-magic.admin"))values.addAll(List.of("give","givecore","pack","items"));}
        if(args.length==2&&sender.hasPermission("advance-magic.admin")&&args[0].equalsIgnoreCase("pack"))values.add("resend");
        if(sender.hasPermission("advance-magic.admin")&&args.length>=2&&(args[0].equalsIgnoreCase("give")||args[0].equalsIgnoreCase("givecore"))) {
            if(args.length==2) {
                values.addAll(List.of("@s","@p","@a"));
                for(Player p:Bukkit.getOnlinePlayers()) {
                    values.add(p.getName());
                    if(p.getName().startsWith(".")) values.add(p.getName().substring(1));
                }
                for(Spell s:Spell.values()) values.add(s.id());
            }
            if(args.length==3) {
                for(Spell s:Spell.values()) values.add(s.id());
                if(args[0].equalsIgnoreCase("give")) values.addAll(List.of("wand","core"));
            }
            if(args.length==4&&args[0].equalsIgnoreCase("give")) values.addAll(List.of("wand","core"));
        }
        String prefix=args.length==0?"":args[args.length-1].toLowerCase(Locale.ROOT);
        return values.stream().filter(v->v.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
