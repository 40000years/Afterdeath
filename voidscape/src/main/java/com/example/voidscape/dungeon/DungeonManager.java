package com.example.voidscape.dungeon;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.RelicService.Relic;
import com.example.voidscape.world.DungeonLayout;
import com.example.voidscape.world.DungeonLayout.Site;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.boss.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.*;
import org.bukkit.util.Vector;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class DungeonManager implements Listener {
    private enum Species { HOLLOW, STALKER, WEAVER, CAPTAIN }
    private static final int[][] MAIN_SEALS={{-25,97,-16},{25,104,16},{-25,111,16}};
    private static final int[][] MINOR_SEALS={{0,97,8}};
    private static final class Encounter {
        final Site site; final Map<UUID,Species> mobs=new HashMap<>();
        final Map<UUID,Integer> presence=new HashMap<>();
        int seals=0,wave=-1,phase=0; boolean bossStarted=false,finished=false;
        long lastPresent=System.currentTimeMillis(),lastSkill=0,warningAt=0,phaseUntil=0;
        Location warning; UUID caster; BossBar bar;
        Encounter(Site site){this.site=site;}
    }
    private final VoidscapePlugin plugin;
    private final Map<String,Encounter> active=new LinkedHashMap<>();
    private final Map<UUID,Encounter> owners=new HashMap<>();
    private final Map<UUID,Long> combatUntil=new HashMap<>();
    private final Map<UUID,String> seen=new HashMap<>();
    private final NamespacedKey mobKey,runKey;
    private final String runId=UUID.randomUUID().toString();
    private final YamlConfiguration ledger;
    private final File file;
    private boolean storageHealthy=true;
    public DungeonManager(VoidscapePlugin plugin)throws IOException {
        this.plugin=plugin;mobKey=plugin.key("dungeon_mob");runKey=plugin.key("runtime");
        file=new File(plugin.getDataFolder(),"dungeons.yml");
        ledger=new YamlConfiguration();
        if(file.exists())try{ledger.load(file);}catch(Exception e){throw new IOException("Cannot safely read reward ledger",e);}
        // Persistent tags are authoritative. Remove only our stale encounter entities, never named vanilla mobs.
        for(Entity e:plugin.world().getEntities())if(e.getPersistentDataContainer().has(mobKey))e.remove();
    }
    private String path(Site s){return "sites."+s.id();}
    private boolean save() {
        try {
            Path dest=file.toPath(),tmp=dest.resolveSibling("dungeons.yml.tmp");
            Files.writeString(tmp,ledger.saveToString());
            try{Files.move(tmp,dest,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}
            catch(AtomicMoveNotSupportedException e){Files.move(tmp,dest,StandardCopyOption.REPLACE_EXISTING);}
            return true;
        }catch(IOException e){storageHealthy=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Reward storage failed; new encounters/claims disabled",e);return false;}
    }
    public boolean inCombat(Player p){return combatUntil.getOrDefault(p.getUniqueId(),0L)>System.currentTimeMillis();}
    public int mobCount(){return owners.size();} public int activeCount(){return active.size();}
    private boolean playable(Player p){return p.isOnline()&&!p.isDead()&&(p.getGameMode()==GameMode.SURVIVAL||p.getGameMode()==GameMode.ADVENTURE);}
    private List<Player> players(Site site) {
        List<Player> result=new ArrayList<>();
        for(Player p:plugin.world().getPlayers())if(playable(p)&&p.getY()>60&&p.getY()<170&&site.contains(p.getX(),p.getZ(),12))result.add(p);
        return result;
    }
    private Location position(Site s,int x,int y,int z){return new Location(plugin.world(),s.x()+x+0.5,y,s.z()+z+0.5);}
    private int[][] seals(Site site){return site.kind()==DungeonLayout.Kind.DREADSHIP?MAIN_SEALS:MINOR_SEALS;}
    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e) {
        Player p=e.getPlayer();Block block=e.getClickedBlock();
        if(p.getWorld()!=plugin.world()||block==null||!e.getAction().isRightClick()||e.getHand()!=EquipmentSlot.HAND||!playable(p))return;
        Site site=plugin.layout().at(block.getX(),block.getZ(),0);if(site==null)return;
        if(block.getType()==Material.VAULT){e.setCancelled(true);claim(p);return;}
        if(block.getType()!=Material.LODESTONE)return;
        int index=-1;int[][] marks=seals(site);
        for(int i=0;i<marks.length;i++)if(block.getX()==site.x()+marks[i][0]&&block.getY()==marks[i][1]&&block.getZ()==site.z()+marks[i][2])index=i;
        if(index<0)return;e.setCancelled(true);
        if(!storageHealthy){plugin.message(p,"คลังรางวัลไม่พร้อม · แจ้งแอดมิน");return;}
        long next=ledger.getLong(path(site)+".next-open",0);
        if(next>System.currentTimeMillis()){plugin.message(p,"ผนึกกำลังฟื้นตัว · อีก "+Math.max(1,(next-System.currentTimeMillis())/60000)+" นาที");return;}
        Encounter encounter=active.get(site.id());
        if(encounter==null) {
            if(active.size()>=plugin.integer("performance.max-active-dungeons",4,1,12)){plugin.message(p,"มีการต่อสู้หลายแห่งอยู่ · ลองใหม่ภายหลัง");return;}
            encounter=new Encounter(site);active.put(site.id(),encounter);
        }
        if((encounter.seals&(1<<index))!=0){plugin.message(p,"ผนึกนี้ถูกปลดแล้ว");return;}
        if(!encounter.mobs.isEmpty()||encounter.bossStarted){plugin.message(p,"กำจัดผู้เฝ้าผนึกก่อน");return;}
        if(mobCount()+4>plugin.integer("performance.max-dungeon-mobs",48,4,128)){plugin.message(p,"พลังผนึกยังไม่พร้อม · ลองใหม่ภายหลัง");return;}
        encounter.wave=index;
        List<Player> team=players(site);
        int amount=Math.min(6,2+Math.min(team.size(),4));
        int[] marker=marks[index];
        for(int i=0;i<amount;i++) {
            Species kind=Species.values()[(i+index+(int)Math.floorMod(site.variant(),3))%3];
            Location spawn=position(site,marker[0]+(i%2==0?-3:3),marker[1],marker[2]+(i<2?-3:3));
            spawn(encounter,kind,spawn,team.size());
        }
        if(encounter.mobs.isEmpty()){plugin.message(p,"ไม่สามารถเรียกผู้เฝ้าได้ · ตรวจระบบป้องกันการเกิดมอนสเตอร์");return;}
        for(Player member:team){encounter.presence.putIfAbsent(member.getUniqueId(),0);plugin.message(member,"ผนึกตื่นแล้ว · กำจัดผู้เฝ้าแห่งความว่างเปล่า");}
    }
    private LivingEntity spawn(Encounter enc,Species species,Location where,int teamSize) {
        if(where.getWorld()!=plugin.world()||!where.getWorld().isChunkLoaded(where.getBlockX()>>4,where.getBlockZ()>>4))return null;
        if(owners.size()>=plugin.integer("performance.max-dungeon-mobs",48,4,128))return null;
        Class<? extends Mob> type=species==Species.WEAVER?Vex.class:species==Species.STALKER?Enderman.class:WitherSkeleton.class;
        Mob mob=plugin.world().spawn(where,type,m->{
            m.getPersistentDataContainer().set(mobKey,PersistentDataType.STRING,enc.site.id());
            m.getPersistentDataContainer().set(runKey,PersistentDataType.STRING,runId);
            double health=species==Species.CAPTAIN?plugin.integer("combat.boss-health-per-phase",1200,100,1800):species==Species.HOLLOW?120:species==Species.STALKER?90:65;
            health=Math.min(2000,health*(1+Math.max(0,Math.min(teamSize,4)-2)*0.25));
            m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);m.setHealth(m.getAttribute(Attribute.MAX_HEALTH).getValue());
            if(m.getAttribute(Attribute.ATTACK_DAMAGE)!=null)m.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(species==Species.CAPTAIN?plugin.integer("combat.boss-attack-damage",22,4,60):species==Species.HOLLOW?plugin.integer("combat.hollow-attack-damage",12,2,40):plugin.integer("combat.stalker-attack-damage",10,2,30));
            if(m.getAttribute(Attribute.SCALE)!=null)m.getAttribute(Attribute.SCALE).setBaseValue(species==Species.CAPTAIN?2.2:species==Species.WEAVER?1.8:1.15);
            if(m.getAttribute(Attribute.FOLLOW_RANGE)!=null)m.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(28);
            if(m.getAttribute(Attribute.KNOCKBACK_RESISTANCE)!=null)m.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(species==Species.CAPTAIN?0.9:0.35);
            if(m.getAttribute(Attribute.ARMOR)!=null)m.getAttribute(Attribute.ARMOR).setBaseValue(species==Species.CAPTAIN?16:8);
            m.setPersistent(false);m.setRemoveWhenFarAway(true);
            m.customName(Component.text(switch(species){case HOLLOW->"อัศวินเกราะกลวง";case STALKER->"นักล่าเสียงสะท้อน";case WEAVER->"ผู้เย็บรอยแยก";case CAPTAIN->enc.site.kind()==DungeonLayout.Kind.DREADSHIP?"กัปตันไร้ร่าง":"ผู้พิทักษ์มหาวิหาร";},NamedTextColor.LIGHT_PURPLE));
            m.setCustomNameVisible(true);
            if(m instanceof Vex vex)vex.setLimitedLifetime(false);
            if(m instanceof WitherSkeleton) {
                var equipment=m.getEquipment();
                ItemStack helmet=new ItemStack(Material.CARVED_PUMPKIN);ItemMeta meta=helmet.getItemMeta();
                meta.setItemModel(new NamespacedKey("voidscape",species==Species.CAPTAIN?"captain_mask":"hollow_mask"));helmet.setItemMeta(meta);
                equipment.setHelmet(helmet);equipment.setHelmetDropChance(0);
                equipment.setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));equipment.setChestplateDropChance(0);
                equipment.setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));equipment.setLeggingsDropChance(0);
                equipment.setBoots(new ItemStack(Material.NETHERITE_BOOTS));equipment.setBootsDropChance(0);
                equipment.setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));equipment.setItemInMainHandDropChance(0);
            }
        });
        if(!mob.isValid()||mob.isDead())return null;
        enc.mobs.put(mob.getUniqueId(),species);owners.put(mob.getUniqueId(),enc);
        return mob;
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void creature(CreatureSpawnEvent e) {
        if(e.getLocation().getWorld()==plugin.world()&&!runId.equals(e.getEntity().getPersistentDataContainer().get(runKey,PersistentDataType.STRING)))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void entityTeleport(EntityTeleportEvent e){if(owners.containsKey(e.getEntity().getUniqueId()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void changeBlock(EntityChangeBlockEvent e){if(e.getBlock().getWorld()==plugin.world())e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent e) {
        Encounter enc=owners.get(e.getEntity().getUniqueId());
        Entity source=e.getDamager();Player attacker=source instanceof Player p?p:source instanceof Projectile pr&&pr.getShooter() instanceof Player p?p:null;
        if(enc!=null) {
            if(attacker!=null&&(attacker.getWorld()!=plugin.world()||!enc.site.contains(attacker.getX(),attacker.getZ(),12))){e.setCancelled(true);return;}
            Species kind=enc.mobs.get(e.getEntity().getUniqueId());
            if(kind==Species.HOLLOW&&attacker!=null) {
                Vector toward=attacker.getLocation().toVector().subtract(e.getEntity().getLocation().toVector()).setY(0);
                if(toward.lengthSquared()>0.01&&toward.normalize().dot(e.getEntity().getLocation().getDirection().setY(0).normalize())>0.35)
                    e.setDamage(e.getDamage()*0.35);
            }
            if(attacker!=null)combatUntil.put(attacker.getUniqueId(),System.currentTimeMillis()+10000);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void mobDamage(EntityDamageEvent e) {
        Encounter enc=owners.get(e.getEntity().getUniqueId());if(enc==null)return;
        if(e.getCause()==EntityDamageEvent.DamageCause.VOID||e.getCause()==EntityDamageEvent.DamageCause.FALL){e.setCancelled(true);return;}
        if(enc.mobs.get(e.getEntity().getUniqueId())!=Species.CAPTAIN)return;
        if(System.currentTimeMillis()<enc.phaseUntil){e.setCancelled(true);return;}
        LivingEntity boss=(LivingEntity)e.getEntity();
        int phases=enc.site.kind()==DungeonLayout.Kind.DREADSHIP?3:1;
        if(e.getFinalDamage()>=boss.getHealth()&&enc.phase<phases-1) {
            e.setCancelled(true);enc.phase++;boss.setHealth(boss.getAttribute(Attribute.MAX_HEALTH).getValue());
            enc.phaseUntil=System.currentTimeMillis()+2500;enc.warningAt=0;
            for(Player p:players(enc.site))plugin.message(p,"ร่างแตกสลายและประกอบใหม่ · ระยะ "+(enc.phase+1)+"/"+phases);
        }
    }
    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(EntityDeathEvent e) {
        Encounter enc=owners.remove(e.getEntity().getUniqueId());if(enc==null)return;
        Species species=enc.mobs.remove(e.getEntity().getUniqueId());
        e.getDrops().clear();e.setDroppedExp(0);
        if(!runId.equals(e.getEntity().getPersistentDataContainer().get(runKey,PersistentDataType.STRING)))return;
        if(species==Species.CAPTAIN){finish(enc);return;}
        if(enc.mobs.isEmpty()&&!enc.bossStarted&&enc.wave>=0) {
            enc.seals|=1<<enc.wave;enc.wave=-1;
            for(Player p:players(enc.site))plugin.message(p,"ปลดผนึก "+Integer.bitCount(enc.seals)+"/"+seals(enc.site).length+
                (Integer.bitCount(enc.seals)==seals(enc.site).length?" · ไปยัง "+(enc.site.kind()==DungeonLayout.Kind.DREADSHIP?"ดาดฟ้าเรือ":"แท่นกลางวิหาร"):" · ค้นหาผนึกต่อไป"));
        }
    }
    private void finish(Encounter enc) {
        if(enc.finished)return;enc.finished=true;
        boolean major=enc.site.kind()==DungeonLayout.Kind.DREADSHIP;
        ledger.set(path(enc.site)+".next-open",System.currentTimeMillis()+plugin.integer(major?"structures.major.reset-hours":"structures.minor.reset-hours",major?12:3,1,168)*3600000L);
        int shards=plugin.integer(major?"rewards.major-shards":"rewards.minor-shards",major?8:2,1,64);
        for(var member:enc.presence.entrySet()) {
            // Presence throughout a fight counts, including support players; last hit is irrelevant.
            if(member.getValue()<plugin.integer("rewards.minimum-participation-seconds",20,1,300)*2)continue;
            String token=UUID.randomUUID().toString(),root="pending."+member.getKey()+"."+token;
            ledger.set(root+".shards",shards);
            if(major&&new Random().nextDouble()<plugin.getConfig().getDouble("rewards.relic-chance",0.30))
                ledger.set(root+".relic",Relic.values()[new Random().nextInt(5)].name());
            Player p=Bukkit.getPlayer(member.getKey());if(p!=null)plugin.message(p,"พิชิตสำเร็จ! รางวัลพร้อมรับ · /void claim หรือคลิกคลังรางวัล");
        }
        save();remove(enc);active.remove(enc.site.id());
    }
    public void claim(Player p) {
        if(!storageHealthy){plugin.message(p,"คลังรางวัลไม่พร้อม · แจ้งแอดมิน");return;}
        String root="pending."+p.getUniqueId();var section=ledger.getConfigurationSection(root);
        if(section==null||section.getKeys(false).isEmpty()){plugin.message(p,"ยังไม่มีรางวัลค้างรับ · ต้องมีส่วนร่วมในการพิชิตดัน");return;}
        NamespacedKey receiptKey=plugin.key("reward_receipts");
        String receipts=p.getPersistentDataContainer().getOrDefault(receiptKey,PersistentDataType.STRING,"");
        Set<String> applied=new LinkedHashSet<>(Arrays.asList(receipts.split(",")));applied.remove("");
        for(String token:new ArrayList<>(section.getKeys(false))) {
            if(applied.contains(token)){ledger.set(root+"."+token,null);continue;}
            List<ItemStack> reward=new ArrayList<>();reward.add(plugin.relics().create(Relic.VOID_SHARD,section.getInt(token+".shards",1)));
            String relic=section.getString(token+".relic");if(relic!=null)reward.add(plugin.relics().create(Relic.valueOf(relic),1));
            Inventory simulation=Bukkit.createInventory(null,36);
            ItemStack[] contents=p.getInventory().getStorageContents();
            for(int i=0;i<contents.length;i++)if(contents[i]!=null)contents[i]=contents[i].clone();
            simulation.setContents(contents);
            if(!simulation.addItem(reward.toArray(ItemStack[]::new)).isEmpty()){plugin.message(p,"กระเป๋าเต็ม · รางวัลยังอยู่ในคลัง รับใหม่ได้");break;}
            p.getInventory().setStorageContents(simulation.getContents());applied.add(token);
            p.getPersistentDataContainer().set(receiptKey,PersistentDataType.STRING,String.join(",",applied));
            // Items and receipt are in the same player save. A crash before ledger pruning cannot duplicate them.
            p.saveData();ledger.set(root+"."+token,null);
            plugin.message(p,"รับรางวัลจากการพิชิตแล้ว");
        }
        if(save()) {while(applied.size()>128)applied.remove(applied.iterator().next());p.getPersistentDataContainer().set(receiptKey,PersistentDataType.STRING,String.join(",",applied));}
    }
    public void tick() {
        long now=System.currentTimeMillis();combatUntil.values().removeIf(t->t<now);
        for(Player p:plugin.world().getPlayers()) {
            Site s=plugin.layout().at(p.getLocation().getBlockX(),p.getLocation().getBlockZ(),12);
            if(s!=null&&!s.id().equals(seen.put(p.getUniqueId(),s.id())))plugin.message(p,
                s.kind()==DungeonLayout.Kind.DREADSHIP?"ค้นพบคฤหาสน์เรืออับปาง · คลิกผนึก Lodestone ทั้ง 3 ชั้นเพื่อท้าทายกัปตัน":"ค้นพบมหาวิหารเงา · คลิกผนึกกลางวิหารเพื่อเริ่ม");
        }
        for(Encounter enc:new ArrayList<>(active.values())) {
            List<Player> team=players(enc.site);
            if(team.isEmpty()) {
                if(enc.bar!=null)enc.bar.removeAll();
                if(now-enc.lastPresent>plugin.integer("performance.idle-reset-seconds",60,15,600)*1000L){remove(enc);active.remove(enc.site.id());}
                else for(UUID id:enc.mobs.keySet())if(Bukkit.getEntity(id) instanceof Mob m){m.setTarget(null);m.setAI(false);}
                continue;
            }
            enc.lastPresent=now;
            if(!enc.mobs.isEmpty())for(Player p:team){enc.presence.merge(p.getUniqueId(),1,Integer::sum);combatUntil.put(p.getUniqueId(),now+10000);}
            // Missing/unloaded entities never count as defeated. Reset that wave so chunk unload cannot grant seals.
            if(enc.mobs.keySet().stream().anyMatch(id->Bukkit.getEntity(id)==null)) {
                remove(enc);active.remove(enc.site.id());continue;
            }
            if(Integer.bitCount(enc.seals)==seals(enc.site).length&&!enc.bossStarted) {
                boolean upstairs=enc.site.kind()!=DungeonLayout.Kind.DREADSHIP||team.stream().anyMatch(p->p.getY()>=134);
                if(upstairs) {
                    Location spawn=position(enc.site,0,enc.site.kind()==DungeonLayout.Kind.DREADSHIP?135:97,0);
                    LivingEntity boss=spawn(enc,Species.CAPTAIN,spawn,team.size());
                    if(boss!=null){enc.bossStarted=true;enc.bar=Bukkit.createBossBar("ผู้เฝ้าสมบัติมิติ",BarColor.PURPLE,BarStyle.SEGMENTED_10);}
                }
            }
            for(var entry:new ArrayList<>(enc.mobs.entrySet())) {
                if(!(Bukkit.getEntity(entry.getKey()) instanceof Mob mob))continue;
                mob.setAI(true);
                Player target=team.stream().min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
                if(target!=null&&entry.getValue()==Species.STALKER&&!target.isSprinting()&&target.getLocation().distanceSquared(mob.getLocation())>100)target=null;
                mob.setTarget(target);
                if(mob.getY()<75||!enc.site.contains(mob.getX(),mob.getZ(),8)) {
                    Location back=position(enc.site,0,enc.bossStarted&&enc.site.kind()==DungeonLayout.Kind.DREADSHIP?135:97,0);
                    // EntityTeleportEvent protects portals, so an internal recall uses a short-lived flag.
                    owners.remove(mob.getUniqueId());mob.teleport(back);owners.put(mob.getUniqueId(),enc);
                }
                if(entry.getValue()==Species.CAPTAIN&&target!=null)bossTick(enc,mob,target,team,now);
                if(entry.getValue()==Species.WEAVER&&now/1000%6==0) {
                    for(UUID id:enc.mobs.keySet())if(Bukkit.getEntity(id) instanceof LivingEntity ally&&ally!=mob&&ally.getLocation().distanceSquared(mob.getLocation())<144) {
                        ally.setHealth(Math.min(ally.getAttribute(Attribute.MAX_HEALTH).getValue(),ally.getHealth()+2));break;
                    }
                }
            }
        }
    }
    private void bossTick(Encounter enc,Mob boss,Player target,List<Player> team,long now) {
        if(enc.bar!=null) {
            enc.bar.setTitle("กัปตันไร้ร่าง · ระยะ "+(enc.phase+1)+" · "+(int)boss.getHealth()+" HP");
            enc.bar.setProgress(Math.max(0,Math.min(1,boss.getHealth()/boss.getAttribute(Attribute.MAX_HEALTH).getValue())));
            for(Player p:new ArrayList<>(enc.bar.getPlayers()))if(!team.contains(p))enc.bar.removePlayer(p);
            for(Player p:team)enc.bar.addPlayer(p);
        }
        if(enc.warningAt==0&&now-enc.lastSkill>6500-enc.phase*700&&now>enc.phaseUntil) {
            enc.warning=target.getLocation();enc.warningAt=now+2200;enc.caster=boss.getUniqueId();enc.lastSkill=now;
            for(Player p:team){plugin.message(p,"⚠ สมอพิพากษา · ออกจากวงแสง!");p.playSound(enc.warning,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,0.6f,0.6f);}
        }
        if(enc.warningAt>0) {
            double radius=4+enc.phase;
            for(Player p:team)for(int i=0;i<12;i++) {
                double angle=i*Math.PI/6;
                p.spawnParticle(Particle.SOUL_FIRE_FLAME,enc.warning.clone().add(Math.cos(angle)*radius,0.15,Math.sin(angle)*radius),1,0,0,0,0);
            }
            if(now>=enc.warningAt) {
                enc.warningAt=0;
                for(Player p:team)if(p.getWorld()==enc.warning.getWorld()&&p.getLocation().distanceSquared(enc.warning)<=radius*radius)
                    p.damage(plugin.integer("combat.boss-skill-damage",32,10,160),boss);
                for(Player p:team)p.playSound(enc.warning,Sound.ENTITY_WARDEN_SONIC_BOOM,0.7f,0.8f);
            }
        }
    }
    private boolean protectedBlock(Block b){return b.getWorld()==plugin.world()&&plugin.layout().at(b.getX(),b.getZ(),0)!=null&&b.getY()>=94&&b.getY()<=160;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){if(protectedBlock(e.getBlock())&&!(e.getPlayer().getGameMode()==GameMode.CREATIVE&&e.getPlayer().hasPermission("voidscape.admin")))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void placeBlock(BlockPlaceEvent e){if(protectedBlock(e.getBlock())&&!(e.getPlayer().getGameMode()==GameMode.CREATIVE&&e.getPlayer().hasPermission("voidscape.admin")))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(BlockExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void fluid(BlockFromToEvent e){if(protectedBlock(e.getToBlock()))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonExtendEvent e){if(e.getBlocks().stream().anyMatch(b->protectedBlock(b)||protectedBlock(b.getRelative(e.getDirection()))))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void piston(BlockPistonRetractEvent e){if(e.getBlocks().stream().anyMatch(this::protectedBlock))e.setCancelled(true);}
    @EventHandler public void quit(PlayerQuitEvent e){seen.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void changeWorld(PlayerChangedWorldEvent e){seen.remove(e.getPlayer().getUniqueId());}
    @EventHandler public void chunk(org.bukkit.event.world.ChunkLoadEvent e) {
        if(e.getWorld()!=plugin.world())return;
        for(Entity entity:e.getChunk().getEntities())if(entity.getPersistentDataContainer().has(mobKey)&&!owners.containsKey(entity.getUniqueId()))entity.remove();
    }
    private void remove(Encounter enc) {
        for(UUID id:enc.mobs.keySet()){owners.remove(id);Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}
        enc.mobs.clear();if(enc.bar!=null)enc.bar.removeAll();
    }
    public void close(){for(Encounter enc:active.values())remove(enc);active.clear();if(storageHealthy)save();}
}
