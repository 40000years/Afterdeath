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
        VOID_KEY(Material.TRIAL_KEY,"กุญแจมิติ Void","ใช้สำหรับเปิด Void Vault ในวิหารโบราณ"),
        RIFT_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อแยกพิภพ","ขุด 3×3 บล็อกพร้อมกัน · ย่อตัวเพื่อขุดทีละก้อน"),
        SMELTER_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อหลอมเพลิงมิติ","หลอมบล็อกที่ขุดอัตโนมัติ (ทราย->กระจก, แร่->แท่งโลหะ)"),
        STORM_BOW(Material.BOW,"ธนูพิพากษาสายฟ้า","ยิงธนูผ่าสายฟ้าต่อเนื่องใส่ศัตรู"),
        NOVA_BOW(Material.BOW,"ธนูสะเก็ดดาว","ชาร์จเต็ม: ระเบิดพลังงาน · ไม่ทำลายบล็อก"),
        RIFT_BLADE(Material.NETHERITE_SWORD,"ดาบกรีดมิติ","คลิกขวา: วาร์ปไปข้างหน้า · ต้องมีทางโล่ง"),
        ETERNAL_AEGIS(Material.SHIELD,"โล่แห่งความอมตะ","คลิกขวา: อมตะ 3 วินาที · โจมตีไม่ได้ขณะใช้งาน");
        public final Material material; public final String title,lore;
        Relic(Material m,String t,String l){material=m;title=t;lore=l;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
    }
    private final VoidscapePlugin plugin;
    private final NamespacedKey type,shot,shotOwner,shieldUntil,voidKeyTag;
    private final Set<UUID> mining=new HashSet<>();
    private final Map<UUID,Long> arrows=new HashMap<>();
    public record MagicCore(String id, String title, String wandTitle) {}
    public static final List<MagicCore> MAGIC_CORES = List.of(
        new MagicCore("lightning_strike", "Core of Lightning", "Lightning Strike"),
        new MagicCore("frost_nova", "Core of Frost", "Frost Nova"),
        new MagicCore("shadow_step", "Core of Shadows", "Shadow Step"),
        new MagicCore("natures_bloom", "Core of Nature", "Nature's Bloom"),
        new MagicCore("earth_wall", "Core of Earth", "Earth Wall"),
        new MagicCore("dragons_breath", "Core of Dragon", "Dragon's Breath"),
        new MagicCore("void_pull", "Core of the Void", "Void Pull"),
        new MagicCore("invisibility_shroud", "Core of Invisibility", "Invisibility Shroud"),
        new MagicCore("poison_spores", "Core of Poison", "Poison Spores"),
        new MagicCore("wither_ray", "Core of Wither", "Wither Ray"),
        new MagicCore("shulker_levitation", "Core of Levitation", "Shulker Levitation"),
        new MagicCore("meteor_strike", "Core of Meteor", "Meteor Strike"),
        new MagicCore("iron_armor", "Core of Iron", "Iron Armor"),
        new MagicCore("time_dilation", "Core of Time", "Time Dilation"),
        new MagicCore("soul_drain", "Core of Souls", "Soul Drain")
    );
    private static final Material[] ARMOR_TRIMS = {
        Material.SILENCE_ARMOR_TRIM_SMITHING_TEMPLATE, Material.SPIRE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.WARD_ARMOR_TRIM_SMITHING_TEMPLATE, Material.VEX_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.RIB_ARMOR_TRIM_SMITHING_TEMPLATE, Material.SENTRY_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.TIDE_ARMOR_TRIM_SMITHING_TEMPLATE, Material.WAYFINDER_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.RAISER_ARMOR_TRIM_SMITHING_TEMPLATE, Material.SHAPER_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.HOST_ARMOR_TRIM_SMITHING_TEMPLATE, Material.FLOW_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.BOLT_ARMOR_TRIM_SMITHING_TEMPLATE, Material.EYE_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.DUNE_ARMOR_TRIM_SMITHING_TEMPLATE, Material.COAST_ARMOR_TRIM_SMITHING_TEMPLATE,
        Material.WILD_ARMOR_TRIM_SMITHING_TEMPLATE, Material.SNOUT_ARMOR_TRIM_SMITHING_TEMPLATE
    };
    public RelicService(VoidscapePlugin plugin) {
        this.plugin=plugin; type=plugin.key("relic_v2");shot=plugin.key("shot");shotOwner=plugin.key("shot_owner");shieldUntil=plugin.key("shield_until");
        voidKeyTag=plugin.key("void_key");
    }
    public ItemStack createMagicCore(MagicCore core) {
        ItemStack item=new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD+"✦ "+core.title());
        meta.setLore(List.of(
            ChatColor.GRAY+"Ancient Magic Core (แกนเวทมนตร์โบราณ)",
            ChatColor.DARK_GRAY+"Used to craft: "+ChatColor.LIGHT_PURPLE+core.wandTitle()+" Wand",
            ChatColor.YELLOW+"Recipe: 8 Netherite Ingots / Nether Stars + this Core",
            ChatColor.DARK_PURPLE+"Obtained from Void Vault in Voidscape"
        ));
        var modelData=meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("advance_magic:core_"+core.id()));
        meta.setCustomModelDataComponent(modelData);
        meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","core"),PersistentDataType.STRING,core.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING,core.id());
        item.setItemMeta(meta);
        return item;
    }
    public ItemStack createMagicCore(String id) {
        for(MagicCore c : MAGIC_CORES) {
            if(c.id().equalsIgnoreCase(id)||c.id().replace("_","").equalsIgnoreCase(id.replace("_",""))) return createMagicCore(c);
        }
        return createMagicCore(MAGIC_CORES.get(0));
    }
    public ItemStack create(Relic relic,int count) {
        ItemStack item=new ItemStack(relic.material,Math.min(relic.material.getMaxStackSize(),Math.max(1,count)));
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(relic.title,relic==Relic.VOID_KEY?NamedTextColor.LIGHT_PURPLE:NamedTextColor.AQUA));
        meta.lore(List.of(
            Component.text(relic.lore,NamedTextColor.GRAY),
            Component.text(relic==Relic.VOID_KEY?"VOIDSCAPE · TRIAL KEY":"VOIDSCAPE · RELIC",NamedTextColor.DARK_PURPLE)
        ));
        meta.setItemModel(new NamespacedKey("voidscape",relic.id()));
        meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,relic.name());
        if(relic==Relic.VOID_KEY) {
            meta.getPersistentDataContainer().set(voidKeyTag,PersistentDataType.BYTE,(byte)1);
        } else {
            meta.addEnchant(Enchantment.UNBREAKING,3,true);
            if(relic==Relic.RIFT_PICKAXE||relic==Relic.SMELTER_PICKAXE) {meta.addEnchant(Enchantment.EFFICIENCY,5,true);meta.addEnchant(Enchantment.FORTUNE,3,true);}
            if(relic==Relic.RIFT_BLADE) meta.addEnchant(Enchantment.SHARPNESS,8,true);
            if(relic==Relic.NOVA_BOW||relic==Relic.STORM_BOW) meta.addEnchant(Enchantment.POWER,6,true);
        }
        item.setItemMeta(meta); return item;
    }
    public boolean isVoidKey(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(voidKeyTag,PersistentDataType.BYTE)
            || type(item)==Relic.VOID_KEY;
    }
    public ItemStack createVoidKey() {
        return create(Relic.VOID_KEY,1);
    }
    public ItemStack rollVaultReward() {
        Random r=new Random();
        double roll=r.nextDouble();
        // 30% Diamond Block
        if(roll<0.30) {
            return new ItemStack(Material.DIAMOND_BLOCK,1);
        }
        // 30% Netherite Ingot
        if(roll<0.60) {
            return new ItemStack(Material.NETHERITE_INGOT,1);
        }
        // 20% Random Armor Trim
        if(roll<0.80) {
            Material trimMat=ARMOR_TRIMS[r.nextInt(ARMOR_TRIMS.length)];
            return new ItemStack(trimMat,1);
        }
        // 10% Special Tool
        if(roll<0.90) {
            Relic[] tools={Relic.RIFT_PICKAXE,Relic.SMELTER_PICKAXE,Relic.STORM_BOW};
            return create(tools[r.nextInt(tools.length)],1);
        }
        // 10% สุ่มแกนเวทมนตร์ Core of ... (สุ่ม 1 ใน 15 แบบ)
        MagicCore core = MAGIC_CORES.get(r.nextInt(MAGIC_CORES.size()));
        return createMagicCore(core);
    }
    public ItemStack createGuideBook() {
        ItemStack book=new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta=(org.bukkit.inventory.meta.BookMeta)book.getItemMeta();
        meta.setTitle("บันทึกมิติ Voidscape");
        meta.setAuthor("ผู้พิทักษ์มิติ");
        meta.pages(List.of(
            Component.text("§1§lมิติความว่างเปล่า\n§0(Voidscape Realm)\n§8ส่วนขยาย Advance Magic\n\n§0ยินดีต้อนรับสู่ The Void!\nสวนลอยฟ้าในความว่างเปล่า ป่าดอกไม้และคริสตัลซ่อนร่องรอยวิหารโบราณ\nพร้อมวิหารโบราณ 3 ธาตุ กระจายตัวไม่จำกัดทั่วโลก"),
            Component.text("§1§lสำรวจสวนลอยฟ้า\n§0เดินตามทางแสงไปวิหาร หรือใช้ §5Elytra§0 สำรวจต่อ สร้างบ้านบนทุ่งนอกเขตวิหารได้\n\n§0วิหารโบราณทั้ง 3 ธาตุมีอยู่ §c§lไม่จำกัดทั่วทั้งมิติ§r§0 (เกิดซ้ำเรื่อยๆ ทุกๆ ~280 บล็อก)\n\n§0วิหารใกล้จุดเกิดที่สุด:\n§51. วิหารความมืด§0 (มุ่งหน้าทิศเหนือ Z = -250)\n§92. วิหารดวงดาว§0 (ทิศ ต.อ.เฉียงใต้ X = 220, Z = 130)\n§63. วิหารกาลเวลา§0 (ทิศ ต.ต.เฉียงใต้ X = -220, Z = 130)\n\n§8พิมพ์ /void locate เพื่อดูพิกัดวิหารใกล้ตัวคุณ"),
            Component.text("§1§lกฎการท้าทาย\n§0- คลิกที่แท่น §5Lodestone§0 กลางวิหารเพื่อเรียกผู้พิทักษ์\n\n§0⚠ §c§lคำเตือน:§r§0 ห้ามนำเรือหรือรถรางมาขังมอนสเตอร์เด็ดขาด! พลังวิหารจะขับไล่ยานพาหนะทันที"),
            Component.text("§1§lรางวัล & Void Vault\n§0- เมื่อชนะการต่อสู้ §dVoid Key§0 จะเด้งเข้าตัวผู้เล่นทันที\n- นำไปเปิด §5Void Vault§0\n- §cเปิดได้คนละ 1 ครั้งต่อกล่อง!§0\n\n§0§lโอกาสดรอป (30/30/20/10/10):§r\n§b• 30%§0 Diamond Block\n§8• 30%§0 Netherite Ingot\n§e• 20%§0 Armor Trim สุ่ม\n§d• 10%§0 อุปกรณ์พิเศษ\n§5• 10%§0 สุ่มแกน Core of ... (1 ใน 15 แบบ)"),
            Component.text("§1§lแกนเวทย์ & อุปกรณ์\n§0• §6แกน Core of ... (10%)§0: สุ่ม 1 ใน 15 แบบ นำไปล้อมด้วย Netherite Ingot หรือ Nether Star รวม 8 ชิ้น ที่โต๊ะคราฟต์เพื่อสร้างคทาเวทมนตร์ Advance Magic!\n\n§0• §bที่ขุด 3x3§0: ขุดพื้นที่ 3x3 บล็อกพร้อมกัน\n• §6ที่ขุดหลอมอัตโนมัติ§0: ขุดทรายได้กระจก ขุดแร่ได้แท่งโลหะ\n• §dธนูสายฟ้า§0: ยิงธนูผ่าสายฟ้าต่อเนื่อง")
        ));
        book.setItemMeta(meta);
        return book;
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
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void smeltMine(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.getGameMode()!=GameMode.SURVIVAL)return;
        ItemStack held=p.getInventory().getItemInMainHand();
        if(type(held)!=Relic.SMELTER_PICKAXE)return;
        Block block=e.getBlock();
        ItemStack smelted=smeltResult(block.getType());
        if(smelted==null)return;
        e.setDropItems(false);
        e.setExpToDrop(Math.max(e.getExpToDrop(),1));
        Location loc=block.getLocation().add(0.5,0.5,0.5);
        block.getWorld().dropItemNaturally(loc,smelted);
        block.getWorld().spawnParticle(Particle.FLAME,loc,6,0.2,0.2,0.2,0.02);
        block.getWorld().spawnParticle(Particle.SMOKE,loc,3,0.1,0.1,0.1,0.01);
        p.playSound(loc,Sound.BLOCK_FURNACE_FIRE_CRACKLE,0.6f,1.2f);
    }
    private ItemStack smeltResult(Material m) {
        return switch(m) {
            case SAND, RED_SAND -> new ItemStack(Material.GLASS,1);
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON_BLOCK -> new ItemStack(Material.IRON_INGOT,m==Material.RAW_IRON_BLOCK?9:1);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD_BLOCK -> new ItemStack(Material.GOLD_INGOT,m==Material.RAW_GOLD_BLOCK?9:1);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER_BLOCK -> new ItemStack(Material.COPPER_INGOT,m==Material.RAW_COPPER_BLOCK?9:1);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP,1);
            case COBBLESTONE -> new ItemStack(Material.STONE,1);
            case COBBLED_DEEPSLATE -> new ItemStack(Material.DEEPSLATE,1);
            case STONE -> new ItemStack(Material.SMOOTH_STONE,1);
            case CLAY -> new ItemStack(Material.TERRACOTTA,1);
            case NETHERRACK -> new ItemStack(Material.NETHER_BRICK,1);
            case WET_SPONGE -> new ItemStack(Material.SPONGE,1);
            case CACTUS -> new ItemStack(Material.GREEN_DYE,1);
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG, MANGROVE_LOG, CHERRY_LOG -> new ItemStack(Material.CHARCOAL,1);
            default -> null;
        };
    }
    public void tick() {
        long now=System.currentTimeMillis();
        arrows.entrySet().removeIf(e->{Entity a=Bukkit.getEntity(e.getKey());if(a==null)return true;if(now>=e.getValue()){a.remove();return true;}return false;});
    }
    public void close(){for(UUID id:arrows.keySet()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}arrows.clear();}
}
