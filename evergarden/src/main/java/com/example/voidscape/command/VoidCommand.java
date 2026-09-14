package com.example.voidscape.command;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.item.RelicService.Relic;
import com.example.voidscape.world.DungeonLayout;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import java.util.*;

public final class VoidCommand implements CommandExecutor,TabCompleter {
    private final VoidscapePlugin plugin;
    private int generated,total,cursor,radius;
    private boolean generating,inFlight;
    private boolean isAdmin(CommandSender s){return s.hasPermission("evergarden.admin")||s.hasPermission("voidscape.admin");}
    private boolean canEnter(CommandSender s){return s.hasPermission("evergarden.enter")||s.hasPermission("voidscape.enter");}
    public VoidCommand(VoidscapePlugin plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        Player p=sender instanceof Player player?player:null;
        if(Set.of("give","pregen","reload","status","pack","test","dev","kit").contains(sub)&&!isAdmin(sender)){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
        switch(sub) {
            case "test", "dev", "kit" -> {
                if(p==null){plugin.message(sender,"คำสั่งนี้ใช้ได้เฉพาะผู้เล่นในเกมเท่านั้น");return true;}
                plugin.testGui().open(p);
            }
            case "pack" -> {
                if(args.length>1&&args[1].equalsIgnoreCase("resend")&&p!=null)plugin.packs().offer(p);
                plugin.packs().describe(sender);
            }
            case "guide" -> {
                if(p!=null) {
                    p.openBook(plugin.relics().createGuideBook());
                    p.playSound(p.getLocation(),Sound.ITEM_BOOK_PAGE_TURN,0.8f,1.0f);
                }
            }
            case "enter" -> {if(p!=null&&canEnter(p)){if(p.getWorld()==plugin.world())plugin.travel().leave(p,false);else plugin.travel().enter(p);}}
            case "tp" -> {
                if(p==null)return true;
                if(args.length>1&&isAdmin(sender)) {
                    String dest=args[1].toLowerCase(Locale.ROOT);
                    if(dest.equals("spawn")) {
                        p.teleport(new Location(plugin.world(),0.5,97.0,0.5));
                        plugin.message(p,"วาร์ปมายังจุดเกิดเกาะกลางมิติ (Y=97)");
                        return true;
                    }
                    var kind=dest.contains("astral")?DungeonLayout.Kind.SANCTUM_ASTRAL:
                             dest.contains("time")?DungeonLayout.Kind.SANCTUM_TIME:
                             DungeonLayout.Kind.SANCTUM_DARK;
                    int x=p.getWorld()==plugin.world()?p.getLocation().getBlockX():0,z=p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                    var s=plugin.layout().locate(x,z,kind,12);
                    if(s==null){plugin.message(sender,"ไม่พบสิ่งก่อสร้างในระยะค้นหา");return true;}
                    p.teleport(new Location(plugin.world(),s.x()+0.5,97,s.z()+8.5));
                    plugin.message(p,"วาร์ปไปยัง "+s.kind().displayName+" พิกัด X="+s.x()+" Y=97 Z="+(s.z()+8));
                    return true;
                }
                if(canEnter(p)){if(p.getWorld()==plugin.world())plugin.travel().leave(p,false);else plugin.travel().enter(p);}
            }
            case "leave" -> {if(p!=null&&p.getWorld()==plugin.world())plugin.travel().leave(p,false);}
            case "give" -> {
                if(args.length<2){plugin.message(sender,"/evergarden give <ชื่อไอเทม/เอนแชนต์/แกน> [จำนวน/ผู้เล่น] [ผู้เล่น]");return true;}
                int count = 1;
                Player target = p;
                if(args.length == 3) {
                    try {
                        count = Math.max(1, Integer.parseInt(args[2]));
                    } catch(NumberFormatException ignored) {
                        target = Bukkit.getPlayerExact(args[2]);
                    }
                } else if(args.length >= 4) {
                    try {
                        count = Math.max(1, Integer.parseInt(args[2]));
                        target = Bukkit.getPlayerExact(args[3]);
                    } catch(NumberFormatException ignored) {
                        target = Bukkit.getPlayerExact(args[2]);
                    }
                }
                if(target == null) {
                    plugin.message(sender, "ระบุผู้เล่นออนไลน์ด้วย หรือรันคำสั่งในฐานะผู้เล่น");
                    return true;
                }
                if(target.getInventory().firstEmpty() < 0) {
                    plugin.message(sender, "กระเป๋าผู้รับเต็ม");
                    return true;
                }
                ItemStack item = resolveItem(args[1], count);
                if(item == null) {
                    plugin.message(sender, "ไม่พบไอเทม: "+args[1]+" (ลองพิมพ์ชื่อตรงๆ เช่น ricochet, sharpness, storm_bow, eternity, lightning_strike)");
                    return true;
                }
                target.getInventory().addItem(item);
                String itemName = item.hasItemMeta() && item.getItemMeta().hasDisplayName() ? item.getItemMeta().getDisplayName() : item.getType().name();
                plugin.message(sender, "มอบ "+itemName+" x"+item.getAmount()+" ให้ "+target.getName()+" แล้ว");
                return true;
            }
            case "pregen" -> pregen(sender,args);
            case "reload" -> {plugin.reloadConfig();plugin.message(sender,"โหลดการตั้งค่าแล้ว · ตำแหน่งวิหารคงเดิมตาม world-layout.yml");}
            case "status" -> plugin.message(sender,"Evergarden 3.0 · "+plugin.world().getName()+" · การต่อสู้ "+plugin.dungeons().activeCount()+" · มอน "+plugin.dungeons().mobCount()+" · pregen "+generated+"/"+total);
            default -> {
                plugin.message(sender,"Evergarden 3.0 (Advance Magic Expansion) · /evergarden guide · /evergarden leave");
                plugin.message(sender,"สร้างประตู Crying Obsidian แล้วจุดด้วย Fire Charge หรือ Eye of Ender เพื่อเดินทาง");
                if(isAdmin(sender))plugin.message(sender,"แอดมิน: test (เมนูทดสอบ) · tp [dark|astral|time|spawn] · give · status · pregen · reload");
            }
        }
        return true;
    }
    private void pregen(CommandSender sender,String[] args) {
        if(args.length>1&&args[1].equalsIgnoreCase("stop")){generating=false;plugin.message(sender,"หยุดสร้างล่วงหน้าแล้ว");return;}
        if(generating){plugin.message(sender,"กำลังสร้าง "+generated+"/"+total);return;}
        try{radius=Math.max(1,Math.min(96,Integer.parseInt(args[1])));}catch(RuntimeException e){plugin.message(sender,"/evergarden pregen <รัศมี chunks 1–96> หรือ stop");return;}
        total=(radius*2+1)*(radius*2+1);generated=0;cursor=0;generating=true;
        plugin.message(sender,"สร้างล่วงหน้า "+total+" chunks ทีละ chunk · /evergarden status");
        new org.bukkit.scheduler.BukkitRunnable(){public void run(){
            if(!generating){cancel();return;}if(inFlight)return;
            if(cursor>=total){generating=false;plugin.message(sender,"สร้างเสร็จ "+generated+" chunks");cancel();return;}
            int n=cursor++,side=radius*2+1,x=n/side-radius,z=n%side-radius;inFlight=true;
            plugin.world().getChunkAtAsync(x,z,true).whenComplete((chunk,error)->Bukkit.getScheduler().runTask(plugin,()->{
                inFlight=false;if(error!=null){generating=false;plugin.getLogger().warning("Pregeneration stopped: "+error.getMessage());return;}
                generated++;plugin.world().unloadChunkRequest(x,z);
            }));
        }}.runTaskTimer(plugin,1,2);
    }
    private ItemStack resolveItem(String raw, int count) {
        if(raw == null || raw.isBlank()) return null;
        String clean = raw.toLowerCase(Locale.ROOT).trim().replace("-", "_");

        // 1. Scroll of Eternity (Unbreakable)
        if(clean.equals("scroll_eternity") || clean.equals("eternity") || clean.equals("unbreakable") || clean.equals("scroll_of_eternity")) {
            ItemStack is = plugin.relics().createScrollEternity();
            if(count > 1) is.setAmount(Math.min(count, 64));
            return is;
        }

        // 2. Magic Cores with prefix (core_ or wand_)
        if(clean.startsWith("core_") || clean.startsWith("core") || clean.startsWith("wand_")) {
            String coreId = clean;
            if(coreId.startsWith("core_")) coreId = coreId.substring(5);
            else if(coreId.startsWith("wand_")) coreId = coreId.substring(5);
            else if(coreId.startsWith("core")) coreId = coreId.substring(4);
            if(coreId.startsWith("_")) coreId = coreId.substring(1);
            if(!coreId.isEmpty()) {
                ItemStack is = plugin.relics().createMagicCore(coreId);
                if(count > 1) is.setAmount(Math.min(count, 64));
                return is;
            }
        }

        // 3. Limit Break with prefix (lb_ or limit_break_)
        if(clean.startsWith("lb_") || clean.startsWith("limit_break_")) {
            String lbName = clean.replace("limit_break_", "").replace("lb_", "").replace("_", "");
            for(var lb : LimitBreakType.values()) {
                if(lb.name().replace("_", "").equalsIgnoreCase(lbName)) {
                    ItemStack is = plugin.relics().createScrollLimitBreak(lb);
                    if(count > 1) is.setAmount(Math.min(count, 64));
                    return is;
                }
            }
        }

        // 4. Unique Enchant with prefix (ue_ or unique_)
        if(clean.startsWith("ue_") || clean.startsWith("unique_")) {
            String ueName = clean.replace("unique_", "").replace("ue_", "").replace("_", "");
            for(var ue : UniqueEnchant.values()) {
                if(ue.name().replace("_", "").equalsIgnoreCase(ueName) || ue.id().replace("_", "").equalsIgnoreCase(ueName)) {
                    ItemStack is = plugin.relics().createScrollUnique(ue);
                    if(count > 1) is.setAmount(Math.min(count, 64));
                    return is;
                }
            }
        }

        // 5. Unique Enchants DIRECT name (e.g. "ricochet", "colossus_slayer", "absolute_zero", etc.)
        for(var ue : UniqueEnchant.values()) {
            if(ue.name().equalsIgnoreCase(clean) || ue.id().equalsIgnoreCase(clean) || ue.name().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                ItemStack is = plugin.relics().createScrollUnique(ue);
                if(count > 1) is.setAmount(Math.min(count, 64));
                return is;
            }
        }

        // 6. Limit Breaks DIRECT name (e.g. "sharpness", "protection", "power", "efficiency", "fortune", "looting")
        for(var lb : LimitBreakType.values()) {
            if(lb.name().equalsIgnoreCase(clean) || lb.name().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                ItemStack is = plugin.relics().createScrollLimitBreak(lb);
                if(count > 1) is.setAmount(Math.min(count, 64));
                return is;
            }
        }

        // 7. Common aliases & shortcuts
        switch(clean) {
            case "key", "void_key", "voidkey" -> { return plugin.relics().createVoidKey(); }
            case "shard", "key_shard", "keyshard" -> { return plugin.relics().createKeyShard(Math.max(1, count)); }
            case "dust", "astral_dust", "astraldust" -> { return plugin.relics().createAstralDust(Math.max(1, count)); }
            case "repair", "repair_stone", "repairstone" -> { return plugin.relics().createRepairStone(Math.max(1, count)); }
            case "elixir", "void_elixir", "voidelixir" -> { return plugin.relics().createVoidElixir(Math.max(1, count)); }
            case "pickaxe", "rift_pickaxe", "riftpickaxe" -> { return plugin.relics().create(Relic.RIFT_PICKAXE, 1); }
            case "smelter", "smelter_pickaxe", "smelterpickaxe" -> { return plugin.relics().create(Relic.SMELTER_PICKAXE, 1); }
            case "blade", "sword", "rift_blade", "riftblade", "rift_sword" -> { return plugin.relics().create(Relic.RIFT_BLADE, 1); }
            case "aegis", "shield", "eternal_aegis", "eternalaegis" -> { return plugin.relics().create(Relic.ETERNAL_AEGIS, 1); }
            case "storm", "storm_bow", "stormbow" -> { return plugin.relics().create(Relic.STORM_BOW, 1); }
            case "nova", "nova_bow", "novabow" -> { return plugin.relics().create(Relic.NOVA_BOW, 1); }
            case "mythic_core", "shulker_core", "levitation_core" -> { return plugin.relics().createMagicCore("shulker_levitation"); }
        }

        // 8. Relic enum match
        for(Relic r : Relic.values()) {
            if(r.name().equalsIgnoreCase(clean) || r.id().equalsIgnoreCase(clean) || r.name().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                if(r == Relic.SCROLL_ETERNITY) {
                    ItemStack is = plugin.relics().createScrollEternity();
                    if(count > 1) is.setAmount(Math.min(count, 64));
                    return is;
                }
                if(r == Relic.SCROLL_LIMIT_BREAK) return plugin.relics().createScrollLimitBreak(LimitBreakType.SHARPNESS);
                if(r == Relic.SCROLL_UNIQUE) return plugin.relics().createScrollUnique(UniqueEnchant.COLOSSUS_SLAYER);
                if(r == Relic.KEY_SHARD) return plugin.relics().createKeyShard(Math.max(1, count));
                if(r == Relic.ASTRAL_DUST) return plugin.relics().createAstralDust(Math.max(1, count));
                if(r == Relic.REPAIR_STONE) return plugin.relics().createRepairStone(Math.max(1, count));
                if(r == Relic.VOID_ELIXIR) return plugin.relics().createVoidElixir(Math.max(1, count));
                return plugin.relics().create(r, 1);
            }
        }

        // 9. Magic Cores DIRECT match (e.g. "lightning_strike", "frost_nova")
        for(var core : RelicService.MAGIC_CORES) {
            if(core.id().equalsIgnoreCase(clean) || core.id().replace("_", "").equalsIgnoreCase(clean.replace("_", ""))) {
                ItemStack is = plugin.relics().createMagicCore(core);
                if(count > 1) is.setAmount(Math.min(count, 64));
                return is;
            }
        }

        return null;
    }

    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> c=new ArrayList<>();
        if(args.length==1){c.addAll(List.of("help","guide","enter","leave"));if(isAdmin(sender))c.addAll(List.of("test","tp","give","status","reload","pregen","pack"));}
        if(args.length==2&&args[0].equalsIgnoreCase("tp")&&isAdmin(sender))c.addAll(List.of("dark","astral","time","spawn"));
        if(args.length==2&&args[0].equalsIgnoreCase("give")&&isAdmin(sender)) {
            // Relics & Equipment
            for(Relic r:Relic.values()) c.add(r.id());
            // Unique Enchants (both direct and prefixed)
            for(var ue : UniqueEnchant.values()) {
                c.add(ue.id());
                c.add("ue_"+ue.id());
            }
            // Limit Breaks (both direct and prefixed)
            for(var lb : LimitBreakType.values()) {
                c.add(lb.name().toLowerCase(Locale.ROOT));
                c.add("lb_"+lb.name().toLowerCase(Locale.ROOT));
            }
            // Magic Cores (both direct and prefixed)
            for(var core : RelicService.MAGIC_CORES) {
                c.add(core.id());
                c.add("core_"+core.id());
            }
            // Shortcuts
            c.addAll(List.of("eternity","key","shard","dust","repair","elixir","storm","nova","blade","aegis"));
        }
        if(args.length==3&&args[0].equalsIgnoreCase("give")&&isAdmin(sender)) {
            for(Player pl : Bukkit.getOnlinePlayers()) c.add(pl.getName());
            c.addAll(List.of("1","2","4","8","16","32","64"));
        }
        if(args.length==4&&args[0].equalsIgnoreCase("give")&&isAdmin(sender)) {
            for(Player pl : Bukkit.getOnlinePlayers()) c.add(pl.getName());
        }
        return c.stream().filter(s->s.startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
    }
}
