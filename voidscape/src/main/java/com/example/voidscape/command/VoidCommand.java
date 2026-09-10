package com.example.voidscape.command;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.RelicService.Relic;
import com.example.voidscape.world.DungeonLayout;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public final class VoidCommand implements CommandExecutor,TabCompleter {
    private final VoidscapePlugin plugin;
    private int generated,total,cursor,radius;
    private boolean generating,inFlight;
    public VoidCommand(VoidscapePlugin plugin){this.plugin=plugin;}
    @Override public boolean onCommand(CommandSender sender,Command command,String label,String[] args) {
        String sub=args.length==0?"help":args[0].toLowerCase(Locale.ROOT);
        Player p=sender instanceof Player player?player:null;
        if(Set.of("give","pregen","reload","status").contains(sub)&&!sender.hasPermission("voidscape.admin")){plugin.message(sender,"ไม่มีสิทธิ์แอดมิน");return true;}
        switch(sub) {
            case "guide" -> {
                if(p!=null) {
                    p.openBook(plugin.relics().createGuideBook());
                    p.playSound(p.getLocation(),Sound.ITEM_BOOK_PAGE_TURN,0.8f,1.0f);
                }
            }
            case "enter" -> {if(p!=null&&p.hasPermission("voidscape.enter")){if(p.getWorld()==plugin.world())plugin.travel().leave(p,false);else plugin.travel().enter(p);}}
            case "tp" -> {
                if(p==null)return true;
                if(args.length>1&&sender.hasPermission("voidscape.admin")) {
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
                if(p.hasPermission("voidscape.enter")){if(p.getWorld()==plugin.world())plugin.travel().leave(p,false);else plugin.travel().enter(p);}
            }
            case "leave" -> {if(p!=null&&p.getWorld()==plugin.world())plugin.travel().leave(p,false);}
            case "give" -> {
                try {
                    Relic r=Relic.valueOf(args[1].toUpperCase(Locale.ROOT));Player target=args.length>2?Bukkit.getPlayerExact(args[2]):p;
                    if(target==null){plugin.message(sender,"ระบุผู้เล่นออนไลน์ด้วย");return true;}
                    if(target.getInventory().firstEmpty()<0){plugin.message(sender,"กระเป๋าผู้รับเต็ม");return true;}
                    target.getInventory().addItem(plugin.relics().create(r,1));plugin.message(sender,"มอบ "+r.id()+" แล้ว");
                }catch(RuntimeException e){plugin.message(sender,"/void give <ชื่อไอเทม> [ผู้เล่น]");}
            }
            case "locate" -> {
                int x=p!=null&&p.getWorld()==plugin.world()?p.getLocation().getBlockX():0;
                int z=p!=null&&p.getWorld()==plugin.world()?p.getLocation().getBlockZ():0;
                if(args.length>1) {
                    var kind=args[1].toLowerCase(Locale.ROOT).contains("astral")?DungeonLayout.Kind.SANCTUM_ASTRAL:
                             args[1].toLowerCase(Locale.ROOT).contains("time")?DungeonLayout.Kind.SANCTUM_TIME:
                             DungeonLayout.Kind.SANCTUM_DARK;
                    var s=plugin.layout().locate(x,z,kind,12);
                    if(s==null){plugin.message(sender,"ไม่พบในขอบเขตค้นหา");}
                    else {
                        int dist=(int)Math.hypot(s.x()-x,s.z()-z);
                        plugin.message(sender,s.kind().displayName+" X="+s.x()+" Z="+s.z()+" · ทางเข้า Y=97 (ห่าง "+dist+" บล็อก)");
                    }
                } else {
                    plugin.message(sender,"✦ ตำแหน่งวิหารทั้ง 3 ธาตุใกล้ที่สุด (มีไม่จำกัดทั่วทั้งมิติ):");
                    for(DungeonLayout.Kind k : DungeonLayout.Kind.values()) {
                        var s=plugin.layout().locate(x,z,k,12);
                        if(s!=null) {
                            int dist=(int)Math.hypot(s.x()-x,s.z()-z);
                            plugin.message(sender,"• "+k.displayName+" · X="+s.x()+" Z="+s.z()+" · Y=97 (ห่าง "+dist+" บล็อก)");
                        }
                    }
                }
            }
            case "pregen" -> pregen(sender,args);
            case "reload" -> {plugin.reloadConfig();plugin.message(sender,"โหลดการตั้งค่าแล้ว · ตำแหน่งวิหารคงเดิมตาม world-layout.yml");}
            case "status" -> plugin.message(sender,"Voidscape 3.0 · "+plugin.world().getName()+" · การต่อสู้ "+plugin.dungeons().activeCount()+" · มอน "+plugin.dungeons().mobCount()+" · pregen "+generated+"/"+total);
            default -> {
                plugin.message(sender,"Voidscape 3.0 (Advance Magic Expansion) · /void guide · /void locate · /void leave");
                plugin.message(sender,"สร้างประตู Crying Obsidian แล้วจุดด้วย Fire Charge หรือ Eye of Ender เพื่อเดินทาง");
                if(sender.hasPermission("voidscape.admin"))plugin.message(sender,"แอดมิน: tp [dark|astral|time|spawn] · locate · give · status · pregen · reload");
            }
        }
        return true;
    }
    private void pregen(CommandSender sender,String[] args) {
        if(args.length>1&&args[1].equalsIgnoreCase("stop")){generating=false;plugin.message(sender,"หยุดสร้างล่วงหน้าแล้ว");return;}
        if(generating){plugin.message(sender,"กำลังสร้าง "+generated+"/"+total);return;}
        try{radius=Math.max(1,Math.min(96,Integer.parseInt(args[1])));}catch(RuntimeException e){plugin.message(sender,"/void pregen <รัศมี chunks 1–96> หรือ stop");return;}
        total=(radius*2+1)*(radius*2+1);generated=0;cursor=0;generating=true;
        plugin.message(sender,"สร้างล่วงหน้า "+total+" chunks ทีละ chunk · /void status");
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
    @Override public List<String> onTabComplete(CommandSender sender,Command command,String alias,String[] args) {
        List<String> c=new ArrayList<>();
        if(args.length==1){c.addAll(List.of("help","guide","enter","leave","locate"));if(sender.hasPermission("voidscape.admin"))c.addAll(List.of("tp","give","status","reload","pregen"));}
        if(args.length==2&&args[0].equalsIgnoreCase("tp")&&sender.hasPermission("voidscape.admin"))c.addAll(List.of("dark","astral","time","spawn"));
        if(args.length==2&&args[0].equalsIgnoreCase("give")&&sender.hasPermission("voidscape.admin"))for(Relic r:Relic.values())c.add(r.id());
        if(args.length==2&&args[0].equalsIgnoreCase("locate"))c.addAll(List.of("dark","astral","time"));
        return c.stream().filter(s->s.startsWith(args[args.length-1].toLowerCase(Locale.ROOT))).toList();
    }
}
