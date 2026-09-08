package com.example.voidscape.item;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.*;
import org.bukkit.util.Vector;
import java.util.*;

public final class RelicService implements Listener {
    public enum Relic {
        RIFT_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อแยกพิภพ","ขุด 3×3 · ย่อตัวเพื่อขุดทีละก้อน"),
        NOVA_BOW(Material.BOW,"ธนูสะเก็ดดาว","ชาร์จเต็ม: ระเบิดพลังงาน · ไม่ทำลายบล็อก"),
        STORM_BOW(Material.BOW,"ธนูพิพากษา","ชาร์จเต็ม: สายฟ้าต่อเนื่องสูงสุด 3 เป้าหมาย"),
        RIFT_BLADE(Material.NETHERITE_SWORD,"ดาบกรีดมิติ","คลิกขวา: วาร์ปไปข้างหน้า · ต้องมีทางโล่ง"),
        ETERNAL_AEGIS(Material.SHIELD,"โล่แห่งความอมตะ","คลิกขวา: อมตะ 3 วินาที · โจมตีไม่ได้ขณะใช้งาน"),
        VOID_SHARD(Material.ECHO_SHARD,"ผลึกโบราณ","สะสม 24 ชิ้น · /void forge <ชื่อไอเทม>");
        public final Material material; public final String title,lore;
        Relic(Material m,String t,String l){material=m;title=t;lore=l;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
    }
    private final VoidscapePlugin plugin;
    private final NamespacedKey type,shot,shotOwner,shieldUntil;
    private final Set<UUID> mining=new HashSet<>();
    private final Map<UUID,Long> arrows=new HashMap<>();
    public RelicService(VoidscapePlugin plugin) {
        this.plugin=plugin; type=plugin.key("relic_v2");shot=plugin.key("shot");shotOwner=plugin.key("shot_owner");shieldUntil=plugin.key("shield_until");
    }
    public ItemStack create(Relic relic,int count) {
        ItemStack item=new ItemStack(relic.material,Math.min(relic.material.getMaxStackSize(),Math.max(1,count)));
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(relic.title,NamedTextColor.AQUA));
        meta.lore(List.of(Component.text(relic.lore,NamedTextColor.GRAY),Component.text("VOIDSCAPE · RELIC",NamedTextColor.DARK_PURPLE)));
        meta.setItemModel(new NamespacedKey("voidscape",relic.id()));
        meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,relic.name());
        if(relic!=Relic.VOID_SHARD) {
            meta.addEnchant(Enchantment.UNBREAKING,3,true);
            if(relic==Relic.RIFT_PICKAXE) {meta.addEnchant(Enchantment.EFFICIENCY,5,true);meta.addEnchant(Enchantment.FORTUNE,3,true);}
            if(relic==Relic.RIFT_BLADE) meta.addEnchant(Enchantment.SHARPNESS,8,true);
            if(relic==Relic.NOVA_BOW||relic==Relic.STORM_BOW) meta.addEnchant(Enchantment.POWER,6,true);
        }
        item.setItemMeta(meta); return item;
    }
    public Relic type(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(type,PersistentDataType.STRING);
        try { Relic r=Relic.valueOf(id==null?"":id);return item.getType()==r.material?r:null; } catch(IllegalArgumentException e){return null;}
    }
    public boolean immune(Player p) {
        return p.getPersistentDataContainer().getOrDefault(shieldUntil,PersistentDataType.LONG,0L)>System.currentTimeMillis();
    }
    private boolean ready(Player p,Relic relic) {
        long end=p.getPersistentDataContainer().getOrDefault(plugin.key("cd_"+relic.id()),PersistentDataType.LONG,0L);
        long left=end-System.currentTimeMillis();
        if(left<=0)return true;
        p.sendActionBar(Component.text("คูลดาวน์ " + ((left+999)/1000) + " วินาที",NamedTextColor.GRAY));return false;
    }
    private void cooldown(Player p,Relic r,int seconds) {
        p.getPersistentDataContainer().set(plugin.key("cd_"+r.id()),PersistentDataType.LONG,System.currentTimeMillis()+seconds*1000L);
    }
    @EventHandler(priority=EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if(!e.getAction().isRightClick())return;
        // Air clicks can be pre-cancelled by vanilla; honour the item-use decision separately.
        if(e.useItemInHand()==Event.Result.DENY)return;
        Relic r=type(e.getItem()); Player p=e.getPlayer();
        if(r!=Relic.RIFT_BLADE&&r!=Relic.ETERNAL_AEGIS)return;
        e.setCancelled(true);
        if(p.getGameMode()==GameMode.SPECTATOR||!ready(p,r))return;
        if(r==Relic.ETERNAL_AEGIS) {
            int seconds=plugin.integer("relics.shield.duration-seconds",3,1,5);
            p.getPersistentDataContainer().set(shieldUntil,PersistentDataType.LONG,System.currentTimeMillis()+seconds*1000L);
            cooldown(p,r,plugin.integer("relics.shield.cooldown-seconds",75,10,600));
            p.playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,0.6f,0.7f);
            p.sendActionBar(Component.text("โล่แห่งความอมตะ · "+seconds+" วินาที",NamedTextColor.GOLD));
        } else {
            if(immune(p))return;
            Location start=p.getLocation(),target=null;
            Vector direction=start.getDirection();
            double distance=plugin.integer("relics.blink.distance",8,2,12);
            for(double d=0.5;d<=distance;d+=0.5) {
                Location next=start.clone().add(direction.clone().multiply(d));
                if(!p.getWorld().getWorldBorder().isInside(next))break;
                if(!p.getWorld().isChunkLoaded(next.getBlockX()>>4,next.getBlockZ()>>4))break;
                BoundingBox body=BoundingBox.of(next.clone().add(-0.31,0,-0.31),next.clone().add(0.31,1.85,0.31));
                boolean collision=false;
                for(int x=(int)Math.floor(body.getMinX());x<=Math.floor(body.getMaxX());x++)
                    for(int y=(int)Math.floor(body.getMinY());y<=Math.floor(body.getMaxY());y++)
                        for(int z=(int)Math.floor(body.getMinZ());z<=Math.floor(body.getMaxZ());z++)
                            if(!p.getWorld().getBlockAt(x,y,z).isPassable())collision=true;
                if(collision)break;target=next;
            }
            if(target!=null&&start.distanceSquared(target)>=1&&p.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN)) {
                cooldown(p,r,plugin.integer("relics.blink.cooldown-seconds",8,2,120));
                p.setFallDistance(0);p.playSound(target,Sound.ENTITY_ENDERMAN_TELEPORT,0.7f,0.7f);
                p.spawnParticle(Particle.REVERSE_PORTAL,target.clone().add(0,1,0),12,0.3,0.5,0.3,0.03);
            }
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void shieldDamage(EntityDamageEvent e) {if(e.getEntity() instanceof Player p&&immune(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void shieldAttack(EntityDamageByEntityEvent e) {
        Player p=e.getDamager() instanceof Player direct?direct:e.getDamager() instanceof Projectile pr&&pr.getShooter() instanceof Player shooter?shooter:null;
        if(p!=null&&immune(p))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void shoot(EntityShootBowEvent e) {
        if(!(e.getEntity() instanceof Player p))return;
        if(immune(p)){e.setCancelled(true);return;}
        Relic r=type(e.getBow());
        if((r!=Relic.NOVA_BOW&&r!=Relic.STORM_BOW)||e.getForce()<0.95)return;
        if(!ready(p,r)||arrows.size()>=plugin.integer("performance.max-special-projectiles",64,1,256)) {e.setCancelled(true);return;}
        Entity arrow=e.getProjectile();
        arrow.getPersistentDataContainer().set(shot,PersistentDataType.STRING,r.name());
        arrow.getPersistentDataContainer().set(shotOwner,PersistentDataType.STRING,p.getUniqueId().toString());
        arrows.put(arrow.getUniqueId(),System.currentTimeMillis()+10000);
        cooldown(p,r,plugin.integer("relics.bow.cooldown-seconds",5,1,60));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(ProjectileHitEvent e) {
        Projectile arrow=e.getEntity();String id=arrow.getPersistentDataContainer().get(shot,PersistentDataType.STRING);
        if(id==null)return;
        arrow.getPersistentDataContainer().remove(shot);arrows.remove(arrow.getUniqueId());
        if(!(arrow.getShooter() instanceof Player p)||!p.isOnline()||p.getWorld()!=arrow.getWorld()||immune(p)){arrow.remove();return;}
        Location at=arrow.getLocation();arrow.remove();
        List<LivingEntity> targets=new ArrayList<>(at.getNearbyLivingEntities(5.0));
        targets.removeIf(t->t.equals(p)||t instanceof ArmorStand||t.isDead()||t instanceof Player&&!plugin.getConfig().getBoolean("relics.allow-pvp",false));
        targets.sort(Comparator.comparingDouble(t->t.getLocation().distanceSquared(at)));
        if(id.equals(Relic.NOVA_BOW.name())) {
            p.getWorld().spawnParticle(Particle.EXPLOSION,at,1);
            p.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.3f);
            for(LivingEntity t:targets.subList(0,Math.min(8,targets.size())))
                if(clear(at,t.getEyeLocation()))t.damage(Math.max(2,16-at.distance(t.getLocation())*3),p);
        } else {
            int n=0;
            for(LivingEntity t:targets) {
                if(!clear(at,t.getEyeLocation()))continue;
                t.getWorld().strikeLightningEffect(t.getLocation());t.damage(14-n*3,p);
                if(++n==3)break;
            }
        }
    }
    private boolean clear(Location a,Location b) {
        Vector v=b.toVector().subtract(a.toVector());double d=v.length();
        return d<0.3||a.getWorld().rayTraceBlocks(a,v,d-0.2,FluidCollisionMode.NEVER,true)==null;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mine(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.isSneaking()||p.getGameMode()!=GameMode.SURVIVAL||mining.contains(p.getUniqueId())||type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)return;
        Block origin=e.getBlock();if(!Tag.MINEABLE_PICKAXE.isTagged(origin.getType()))return;
        RayTraceResult ray=p.rayTraceBlocks(6);
        org.bukkit.block.BlockFace face=ray==null?org.bukkit.block.BlockFace.UP:ray.getHitBlockFace();
        if(face==null)return;
        UUID id=p.getUniqueId();ItemStack held=p.getInventory().getItemInMainHand();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline()||p.getWorld()!=origin.getWorld()||type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)return;
            if(!p.getInventory().getItemInMainHand().equals(held))return;
            mining.add(id);
            try {
                for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++) {
                    if(a==0&&b==0)continue;
                    Block block=face.getModY()!=0?origin.getRelative(a,0,b):face.getModX()!=0?origin.getRelative(0,a,b):origin.getRelative(a,b,0);
                    if(!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)||p.getLocation().distanceSquared(block.getLocation())>64)continue;
                    if(!Tag.MINEABLE_PICKAXE.isTagged(block.getType())||block.getType().getHardness()<0||block.getState() instanceof org.bukkit.inventory.InventoryHolder)continue;
                    if(type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)break;
                    p.breakBlock(block); // Native events, claims, drops, XP, Fortune and durability for EACH block.
                }
            } finally {mining.remove(id);}
        });
    }
    public boolean forge(Player p,Relic relic) {
        if(relic==Relic.VOID_SHARD)return false;
        int cost=plugin.integer("rewards.forge-cost",24,1,256),count=0;
        for(ItemStack item:p.getInventory().getStorageContents())if(type(item)==Relic.VOID_SHARD)count+=item.getAmount();
        if(count<cost){plugin.message(p,"ต้องมีผลึกโบราณ "+cost+" ชิ้น");return false;}
        if(p.getInventory().firstEmpty()<0){plugin.message(p,"เว้นช่องว่างในกระเป๋า 1 ช่องก่อน");return false;}
        for(ItemStack item:p.getInventory().getStorageContents())if(type(item)==Relic.VOID_SHARD&&cost>0){int n=Math.min(cost,item.getAmount());item.subtract(n);cost-=n;}
        p.getInventory().addItem(create(relic,1));p.saveData();plugin.message(p,"หลอม "+relic.title+" สำเร็จ");return true;
    }
    public void tick() {
        long now=System.currentTimeMillis();
        arrows.entrySet().removeIf(e->{Entity a=Bukkit.getEntity(e.getKey());if(a==null)return true;if(now>=e.getValue()){a.remove();return true;}return false;});
    }
    public void close(){for(UUID id:arrows.keySet()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}arrows.clear();}
}
