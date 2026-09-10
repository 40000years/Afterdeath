package com.example.voidscape.dungeon;

import com.example.voidscape.VoidscapePlugin;
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
import org.bukkit.util.Vector;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class DungeonManager implements Listener {
    private enum Species { MINION, CASTER, STALKER, BOSS }
    private static final class Encounter {
        final Site site; final Map<UUID,Species> mobs=new HashMap<>();
        final Map<UUID,Integer> presence=new HashMap<>();
        int wave=0; boolean bossStarted=false,finished=false;
        long lastPresent=System.currentTimeMillis(),lastSkill=0,warningAt=0;
        Location warning; BossBar bar;
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
        }catch(IOException e){storageHealthy=false;plugin.getLogger().log(java.util.logging.Level.SEVERE,"Reward storage failed",e);return false;}
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

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void interact(PlayerInteractEvent e) {
        Player p=e.getPlayer();Block block=e.getClickedBlock();
        if(p.getWorld()!=plugin.world()||block==null||!e.getAction().isRightClick()||e.getHand()!=EquipmentSlot.HAND||!playable(p))return;
        Site site=plugin.layout().at(block.getX(),block.getZ(),0);if(site==null)return;

        // Vault interaction
        if(block.getType()==Material.VAULT) {
            e.setCancelled(true);
            openVault(p,site,block);
            return;
        }

        // Altar Lodestone interaction at (site.x, 97, site.z + 8)
        if(block.getType()!=Material.LODESTONE||block.getX()!=site.x()||block.getY()!=97||block.getZ()!=site.z()+8)return;
        e.setCancelled(true);

        if(!storageHealthy){plugin.message(p,"ระบบบันทึกไม่พร้อม · แจ้งแอดมิน");return;}
        long next=ledger.getLong(path(site)+".next-open",0);
        if(next>System.currentTimeMillis()){plugin.message(p,"วิหารกำลังฟื้นตัว · อีก "+Math.max(1,(next-System.currentTimeMillis())/60000)+" นาที");return;}

        Encounter encounter=active.get(site.id());
        if(encounter!=null) {
            plugin.message(p,"การต่อสู้ในวิหารนี้กำลังดำเนินอยู่!");
            return;
        }
        if(active.size()>=plugin.integer("performance.max-active-dungeons",6,1,16)){plugin.message(p,"มีการต่อสู้หลายแห่งในมิติ · ลองใหม่ภายหลัง");return;}
        if(mobCount()+6>plugin.integer("performance.max-dungeon-mobs",64,4,128)){plugin.message(p,"พลังงานมิติยังไม่คงที่ · ลองใหม่ภายหลัง");return;}

        encounter=new Encounter(site);
        active.put(site.id(),encounter);
        startWave(encounter,1);
    }

    private void openVault(Player p,Site site,Block block) {
        String openedPath=path(site)+".opened."+p.getUniqueId();
        if(ledger.getBoolean(openedPath,false)) {
            plugin.message(p,"คุณเคยเปิดกล่องสมบัตินี้ไปแล้ว (เปิดได้คนละ 1 ครั้งต่อวิหาร)");
            p.playSound(p.getLocation(),Sound.BLOCK_CHEST_LOCKED,0.7f,1.0f);
            return;
        }

        ItemStack hand=p.getInventory().getItemInMainHand();
        if(!plugin.relics().isVoidKey(hand)) {
            if(hand.getType()==Material.TRIAL_KEY||hand.getType()==Material.OMINOUS_TRIAL_KEY) {
                plugin.message(p,"กุญแจ Trial จากโลกปกติไม่สามารถเปิด Void Vault ได้! ต้องใช้ Void Key จากวิหาร");
            } else {
                plugin.message(p,"ต้องใช้ Void Key ในการเปิดกล่องสมบัตินี้ (เปิดได้คนละ 1 ครั้ง)");
            }
            p.playSound(p.getLocation(),Sound.BLOCK_CHEST_LOCKED,0.7f,1.0f);
            return;
        }

        // Consume Void Key
        hand.subtract(1);
        ledger.set(openedPath,true);
        save();

        // Roll reward from 100% loot table
        ItemStack reward=plugin.relics().rollVaultReward();
        var leftover=p.getInventory().addItem(reward);
        if(!leftover.isEmpty()) {
            p.getWorld().dropItemNaturally(block.getLocation().add(0.5,1.2,0.5),reward);
        }

        // Vault fanfare
        p.playSound(block.getLocation(),Sound.BLOCK_VAULT_OPEN_SHUTTER,1.0f,1.0f);
        p.playSound(block.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.8f,1.2f);
        p.getWorld().spawnParticle(Particle.TRIAL_SPAWNER_DETECTION,block.getLocation().add(0.5,1.0,0.5),30,0.4,0.4,0.4,0.05);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,block.getLocation().add(0.5,1.0,0.5),25,0.3,0.5,0.3,0.1);

        String rewardName=reward.getItemMeta()!=null&&reward.getItemMeta().hasDisplayName()?
            net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(reward.getItemMeta().displayName()):
            reward.getType().name();
        plugin.message(p,"✦ ปลดล็อก Void Vault สำเร็จ! คุณได้รับ "+rewardName);
    }

    private void startWave(Encounter enc,int waveNum) {
        enc.wave=waveNum;
        List<Player> team=players(enc.site);
        for(Player member:team) {
            enc.presence.putIfAbsent(member.getUniqueId(),0);
            plugin.message(member,"✦ ระลอกที่ "+waveNum+"/2: ผู้พิทักษ์แห่ง"+enc.site.kind().displayName+"ปรากฏตัว!");
            member.playSound(member.getLocation(),Sound.EVENT_RAID_HORN,0.7f,1.1f);
        }
        int count=Math.min(6,2+team.size());
        for(int i=0;i<count;i++) {
            Species sp=i%2==0?Species.MINION:Species.CASTER;
            Location loc=position(enc.site,(i%3-1)*6,97,8+(i>2?4:-4));
            spawn(enc,sp,loc,team.size());
        }
    }

    private void spawnBoss(Encounter enc) {
        enc.bossStarted=true;
        List<Player> team=players(enc.site);
        Location loc=position(enc.site,0,97,8);
        LivingEntity boss=spawn(enc,Species.BOSS,loc,team.size());
        if(boss!=null) {
            String title=switch(enc.site.kind()) {
                case SANCTUM_DARK -> "จอมมารแห่งความมืด (Shadow Overlord)";
                case SANCTUM_ASTRAL -> "อัครเทวทูตดวงดาว (Astral Archon)";
                case SANCTUM_TIME -> "ผู้พิทักษ์กาลเวลา (Chronos Vanguard)";
            };
            enc.bar=Bukkit.createBossBar(title,BarColor.PURPLE,BarStyle.SEGMENTED_10);
            for(Player p:team) {
                enc.bar.addPlayer(p);
                plugin.message(p,"⚠ บอสแห่งวิหารปรากฏตัว: "+title+"!");
                p.playSound(p.getLocation(),Sound.ENTITY_WITHER_SPAWN,0.7f,1.0f);
            }
        }
    }

    private LivingEntity spawn(Encounter enc,Species species,Location where,int teamSize) {
        if(where.getWorld()!=plugin.world()||!where.getWorld().isChunkLoaded(where.getBlockX()>>4,where.getBlockZ()>>4))return null;
        if(owners.size()>=plugin.integer("performance.max-dungeon-mobs",64,4,128))return null;

        Class<? extends Mob> type=switch(species) {
            case BOSS -> switch(enc.site.kind()) {
                case SANCTUM_DARK -> WitherSkeleton.class;
                case SANCTUM_ASTRAL -> Stray.class;
                case SANCTUM_TIME -> PiglinBrute.class;
            };
            case CASTER -> Vex.class;
            case STALKER -> Enderman.class;
            case MINION -> switch(enc.site.kind()) {
                case SANCTUM_DARK -> WitherSkeleton.class;
                case SANCTUM_ASTRAL -> Stray.class;
                case SANCTUM_TIME -> PiglinBrute.class;
            };
        };

        Mob mob=plugin.world().spawn(where,type,m->{
            m.getPersistentDataContainer().set(mobKey,PersistentDataType.STRING,enc.site.id());
            m.getPersistentDataContainer().set(runKey,PersistentDataType.STRING,runId);
            double health=species==Species.BOSS?950:species==Species.MINION?120:70;
            health=Math.min(2000,health*(1+Math.max(0,Math.min(teamSize,4)-1)*0.25));
            m.getAttribute(Attribute.MAX_HEALTH).setBaseValue(health);
            m.setHealth(m.getAttribute(Attribute.MAX_HEALTH).getValue());
            if(m.getAttribute(Attribute.ATTACK_DAMAGE)!=null)
                m.getAttribute(Attribute.ATTACK_DAMAGE).setBaseValue(species==Species.BOSS?24:14);
            if(m.getAttribute(Attribute.SCALE)!=null)
                m.getAttribute(Attribute.SCALE).setBaseValue(species==Species.BOSS?2.0:1.1);
            if(m.getAttribute(Attribute.KNOCKBACK_RESISTANCE)!=null)
                m.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(species==Species.BOSS?0.9:0.4);
            if(m.getAttribute(Attribute.ARMOR)!=null)
                m.getAttribute(Attribute.ARMOR).setBaseValue(species==Species.BOSS?16:8);
            m.setPersistent(false);
            m.setRemoveWhenFarAway(true);
            m.setCustomNameVisible(true);

            String name=species==Species.BOSS?
                (enc.site.kind()==DungeonLayout.Kind.SANCTUM_DARK?"จอมมารแห่งความมืด":
                 enc.site.kind()==DungeonLayout.Kind.SANCTUM_ASTRAL?"อัครเทวทูตดวงดาว":"ผู้พิทักษ์กาลเวลา"):
                (species==Species.CASTER?"ภูตพลังเวท":species==Species.MINION?"อัศวินแห่งวิหาร":"นักล่ามิติ");
            m.customName(Component.text(name,species==Species.BOSS?NamedTextColor.GOLD:NamedTextColor.LIGHT_PURPLE));

            if(m instanceof Vex vex) vex.setLimitedLifetime(false);
            if(m.getEquipment()!=null) {
                if(species==Species.BOSS) {
                    m.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                    m.getEquipment().setChestplateDropChance(0);
                    m.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_SWORD));
                    m.getEquipment().setItemInMainHandDropChance(0);
                } else if(m instanceof WitherSkeleton||m instanceof PiglinBrute) {
                    m.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_SWORD));
                    m.getEquipment().setItemInMainHandDropChance(0);
                }
            }
        });

        if(!mob.isValid()||mob.isDead())return null;
        enc.mobs.put(mob.getUniqueId(),species);
        owners.put(mob.getUniqueId(),enc);
        return mob;
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void creature(CreatureSpawnEvent e) {
        if(e.getLocation().getWorld()==plugin.world()&&!runId.equals(e.getEntity().getPersistentDataContainer().get(runKey,PersistentDataType.STRING)))e.setCancelled(true);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent e) {
        Encounter enc=owners.get(e.getEntity().getUniqueId());
        Entity source=e.getDamager();
        Player attacker=source instanceof Player p?p:source instanceof Projectile pr&&pr.getShooter() instanceof Player p?p:null;
        if(enc!=null&&attacker!=null) {
            if(attacker.getWorld()!=plugin.world()||!enc.site.contains(attacker.getX(),attacker.getZ(),12)) {
                e.setCancelled(true);return;
            }
            combatUntil.put(attacker.getUniqueId(),System.currentTimeMillis()+10000);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void death(EntityDeathEvent e) {
        Encounter enc=owners.remove(e.getEntity().getUniqueId());if(enc==null)return;
        Species species=enc.mobs.remove(e.getEntity().getUniqueId());
        e.getDrops().clear();e.setDroppedExp(0);
        if(!runId.equals(e.getEntity().getPersistentDataContainer().get(runKey,PersistentDataType.STRING)))return;

        if(species==Species.BOSS) {
            finish(enc);
            return;
        }

        if(enc.mobs.isEmpty()&&!enc.bossStarted) {
            if(enc.wave<2) {
                startWave(enc,enc.wave+1);
            } else {
                spawnBoss(enc);
            }
        }
    }

    private void finish(Encounter enc) {
        if(enc.finished)return;
        enc.finished=true;
        ledger.set(path(enc.site)+".next-open",System.currentTimeMillis()+plugin.integer("structures.minor.reset-hours",2,1,72)*3600000L);
        save();

        for(var member:enc.presence.entrySet()) {
            Player p=Bukkit.getPlayer(member.getKey());
            if(p!=null&&playable(p)&&enc.site.contains(p.getX(),p.getZ(),18)) {
                ItemStack key=plugin.relics().createVoidKey();
                var leftover=p.getInventory().addItem(key);
                if(!leftover.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(),key);
                plugin.message(p,"✦ พิชิตวิหารสำเร็จ! ได้รับ Void Key เข้ากระเป๋าแล้ว · นำไปไข Void Vault");
                p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.8f,1.0f);
            }
        }
        remove(enc);
        active.remove(enc.site.id());
    }

    public void tick() {
        long now=System.currentTimeMillis();combatUntil.values().removeIf(t->t<now);
        for(Player p:plugin.world().getPlayers()) {
            Site s=plugin.layout().at(p.getLocation().getBlockX(),p.getLocation().getBlockZ(),12);
            if(s!=null&&!s.id().equals(seen.put(p.getUniqueId(),s.id()))) {
                plugin.message(p,"ค้นพบ "+s.kind().displayName+" · คลิกแท่น Lodestone กลางวิหารเพื่อเริ่มการท้าทาย");
            }
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
            for(Player p:team){enc.presence.merge(p.getUniqueId(),1,Integer::sum);combatUntil.put(p.getUniqueId(),now+10000);}

            if(enc.bar!=null) {
                for(Player p:new ArrayList<>(enc.bar.getPlayers()))if(!team.contains(p))enc.bar.removePlayer(p);
                for(Player p:team)enc.bar.addPlayer(p);
            }

            for(var entry:new ArrayList<>(enc.mobs.entrySet())) {
                if(!(Bukkit.getEntity(entry.getKey()) instanceof Mob mob))continue;

                // Anti-Boat Cheese: Force eject and delete any vehicle immediately
                if(mob.isInsideVehicle()) {
                    Entity vehicle=mob.getVehicle();
                    mob.leaveVehicle();
                    if(vehicle instanceof Boat||vehicle instanceof Minecart) vehicle.remove();
                }

                mob.setAI(true);
                Player target=team.stream().min(Comparator.comparingDouble(p->p.getLocation().distanceSquared(mob.getLocation()))).orElse(null);
                mob.setTarget(target);

                // Anti-out-of-bounds recall
                if(mob.getY()<75||!enc.site.contains(mob.getX(),mob.getZ(),8)) {
                    Location back=position(enc.site,0,97,8);
                    owners.remove(mob.getUniqueId());mob.teleport(back);owners.put(mob.getUniqueId(),enc);
                }

                // Boss skills
                if(entry.getValue()==Species.BOSS&&target!=null) {
                    if(enc.bar!=null) {
                        enc.bar.setProgress(Math.max(0,Math.min(1,mob.getHealth()/mob.getAttribute(Attribute.MAX_HEALTH).getValue())));
                    }
                    if(enc.warningAt==0&&now-enc.lastSkill>6000) {
                        enc.warning=target.getLocation();enc.warningAt=now+2200;enc.lastSkill=now;
                        for(Player p:team){plugin.message(p,"⚠ คำสาปมิติ · รีบออกจากวงเวท!");p.playSound(enc.warning,Sound.BLOCK_RESPAWN_ANCHOR_CHARGE,0.6f,0.6f);}
                    }
                    if(enc.warningAt>0) {
                        for(Player p:team)for(int i=0;i<8;i++) {
                            double angle=i*Math.PI/4;
                            p.spawnParticle(Particle.SOUL_FIRE_FLAME,enc.warning.clone().add(Math.cos(angle)*3.5,0.15,Math.sin(angle)*3.5),1,0,0,0,0);
                        }
                        if(now>=enc.warningAt) {
                            enc.warningAt=0;
                            for(Player p:team)if(p.getWorld()==enc.warning.getWorld()&&p.getLocation().distanceSquared(enc.warning)<=16) {
                                p.damage(plugin.integer("combat.boss-skill-damage",35,10,160),mob);
                            }
                            for(Player p:team)p.playSound(enc.warning,Sound.ENTITY_WARDEN_SONIC_BOOM,0.7f,0.8f);
                        }
                    }
                }
            }
        }
    }

    private boolean protectedBlock(Block b){return b.getWorld()==plugin.world()&&plugin.layout().at(b.getX(),b.getZ(),0)!=null&&b.getY()>=94&&b.getY()<=140;}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void breakBlock(BlockBreakEvent e){if(protectedBlock(e.getBlock())&&!(e.getPlayer().getGameMode()==GameMode.CREATIVE&&e.getPlayer().hasPermission("voidscape.admin")))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void placeBlock(BlockPlaceEvent e){if(protectedBlock(e.getBlock())&&!(e.getPlayer().getGameMode()==GameMode.CREATIVE&&e.getPlayer().hasPermission("voidscape.admin")))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(EntityExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void explode(BlockExplodeEvent e){e.blockList().removeIf(this::protectedBlock);}
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
