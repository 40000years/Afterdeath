import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.*;
import org.bukkit.inventory.*;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffectType;
import java.net.InetSocketAddress;
import java.util.*;

/** Test-only Paper 26.2 actor. This code and NMS dependencies are never shipped in the plugin. */
public final class IntegrationChecks extends JavaPlugin {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    AdvanceMagicPlugin plugin;
    Player player;
    ServerPlayer handle;
    Vindicator target;
    int passed;
    void check(boolean value,String label){if(!value)throw new AssertionError(label);passed++;getLogger().info("PASS "+label);}
    void run(Runnable test){try{test.run();}catch(Throwable e){getLogger().log(java.util.logging.Level.SEVERE,"INTEGRATION FAILED after "+passed,e);finish();}}
    void later(int ticks,Runnable test){Bukkit.getScheduler().runTaskLater(this,()->run(test),ticks);}
    @Override public void onEnable(){later(20,this::begin);}
    void begin() {
        plugin=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        check(plugin!=null&&plugin.isEnabled(),"plugin boots on Paper 26.2");
        World w=Bukkit.getWorlds().get(0);
        w.setTime(18000);
        for(int x=-2;x<=2;x++)for(int z=-2;z<=2;z++){w.getChunkAt(x,z).load();w.getChunkAt(x,z).setForceLoaded(true);}
        for(int x=-20;x<=20;x++)for(int z=-20;z<=20;z++)w.getBlockAt(x,99,z).setType(Material.STONE);
        var server=MinecraftServer.getServer();var world=((CraftWorld)w).getHandle();
        GameProfile profile=new GameProfile(UUID.randomUUID(),"MagicTestActor");
        handle=new ServerPlayer(server,world,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(0.5,100,0.5);
        server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        world.addNewPlayer(handle);player=handle.getBukkitEntity();player.setGameMode(GameMode.SURVIVAL);
        player.setGravity(false);player.setOp(true);
        player.teleport(new Location(w,0.5,100,0.5,0,0));
        check(player.isOnline()&&player.isValid(),"real server-backed test actor is online");
        for(Spell s:Spell.values()) {
            ItemStack wand=plugin.wands().create(s);
            check(plugin.wands().spell(wand)==s&&wand.getItemMeta().getItemModel().toString().equals("advance_magic:"+s.id()),"wand PDC and model "+s.id());
            Recipe recipe=Bukkit.getRecipe(new NamespacedKey(plugin,s.id()));
            check(recipe instanceof ShapedRecipe&&((ShapedRecipe)recipe).getShape().length==3&&Arrays.stream(((ShapedRecipe)recipe).getShape()).allMatch(row->row.length()==3),"recipe shape "+s.id());
            var choices=((ShapedRecipe)recipe).getChoiceMap();
            String[] shape=((ShapedRecipe)recipe).getShape();boolean ingredients=true;
            for(int y=0;y<3;y++)for(int x=0;x<3;x++)ingredients &= choices.get(shape[y].charAt(x)).test(new ItemStack(x==1&&y==1?s.core:Material.NETHER_STAR));
            check(ingredients,"recipe ingredients "+s.id());
        }
        check(plugin.wands().spell(new ItemStack(Material.CARROT_ON_A_STICK))==null,"vanilla item cannot cast");
        var account=plugin.mana().account(player);
        check(account.reserve(Spell.LIGHTNING_STRIKE.id(),60,8,System.currentTimeMillis()),"mana reservation");
        plugin.mana().quit(player);
        check(plugin.mana().account(player).mana()==40&&plugin.mana().account(player).remaining(Spell.LIGHTNING_STRIKE.id(),System.currentTimeMillis())>0,"PDC reload preserves mana and cooldown");
        target=w.spawn(new Location(w,0.5,100,6.5),Vindicator.class);target.setAI(false);target.setSilent(true);target.setGravity(false);
        target.getEquipment().clear();target.getAttribute(Attribute.MAX_HEALTH).setBaseValue(200);target.setHealth(200);
        target.getAttribute(Attribute.KNOCKBACK_RESISTANCE).setBaseValue(1);
        check(plugin.spells().cast(player,Spell.FROST_NOVA),"Frost Nova casts");
        check(target.getPotionEffect(PotionEffectType.SLOWNESS).getAmplifier()==3,"Frost Nova Slowness IV");
        later(3,()->{
            check(target.getFreezeTicks()>0,"visual freezing applied by ticker");
            check(plugin.spells().cast(player,Spell.NATURES_BLOOM),"Nature's Bloom casts");
            check(player.hasPotionEffect(PotionEffectType.REGENERATION)&&player.getPotionEffect(PotionEffectType.ABSORPTION).getAmplifier()==1,"caster receives Regen II and Absorption II");
            check(plugin.spells().cast(player,Spell.IRON_ARMOR)&&plugin.statuses().armored(player),"Iron Armor active");
            check(player.getPotionEffect(PotionEffectType.RESISTANCE).getAmplifier()==2,"Resistance III");
            check(plugin.spells().cast(player,Spell.INVISIBILITY_SHROUD)&&player.hasPotionEffect(PotionEffectType.INVISIBILITY),"Shroud activates");
            plugin.statuses().reveal(player);check(!player.hasPotionEffect(PotionEffectType.INVISIBILITY),"Shroud reveal removes owned invisibility");
            reset();check(plugin.spells().cast(player,Spell.EARTH_WALL),"Earth Wall casts");
            check(w.getEntitiesByClass(FallingBlock.class).size()==15,"15 temporary wall visuals");
            check(w.getBlockAt(0,100,3).getType()==Material.AIR,"wall never replaces terrain");
            Arrow arrow=w.spawnArrow(new Location(w,0.5,101,1),new org.bukkit.util.Vector(0,0,1),4,0);
            later(3,()->{
                check(!arrow.isValid(),"wall intercepts moving arrow");
                reset();
                check(plugin.spells().cast(player,Spell.LIGHTNING_STRIKE),"Lightning targets entity");
                check(target.getHealth()<200,"Lightning damage passes through real server damage pipeline");
                reset();check(plugin.spells().cast(player,Spell.DRAGONS_BREATH),"Dragon cloud launches");
                check(w.getEntitiesByClass(AreaEffectCloud.class).size()==1,"native AreaEffectCloud exists");
                later(30,this::projectiles);
            });
        });
    }
    void reset() {
        plugin.effects().closeOwner(player.getUniqueId());plugin.statuses().clear(player);
        player.teleport(new Location(player.getWorld(),0.5,100,0.5,0,0));
        player.setVelocity(new org.bukkit.util.Vector());
        for(var effect:player.getActivePotionEffects())player.removePotionEffect(effect.getType());
        for(var effect:target.getActivePotionEffects())target.removePotionEffect(effect.getType());
        target.setNoDamageTicks(0);target.setHealth(200);target.setFireTicks(0);target.teleport(new Location(player.getWorld(),0.5,100,6.5));target.setVelocity(new org.bukkit.util.Vector());
    }
    void projectiles() {
        check(target.getHealth()<200,"Dragon cloud deals continuous magic damage");
        reset();check(plugin.spells().cast(player,Spell.POISON_SPORES),"Poison Spores launches");
        later(12,()->{
            check(target.hasPotionEffect(PotionEffectType.POISON),"spore impact applies poison");
            reset();check(plugin.spells().cast(player,Spell.SHULKER_LEVITATION),"homing ShulkerBullet launches");
            later(40,()->{
                check(target.hasPotionEffect(PotionEffectType.LEVITATION)&&target.getPotionEffect(PotionEffectType.LEVITATION).getAmplifier()==1,"shulker impact applies Levitation II");
                reset();check(plugin.spells().cast(player,Spell.WITHER_RAY),"Wither Ray casts");
                later(25,()->{
                    check(target.hasPotionEffect(PotionEffectType.WITHER),"wither skull applies Wither II");
                    reset();check(plugin.spells().cast(player,Spell.VOID_PULL),"gravity orb launches");
                    later(50,()->{
                        check(target.hasPotionEffect(PotionEffectType.SLOWNESS),"gravity orb roots target");
                        reset();check(plugin.spells().cast(player,Spell.TIME_DILATION),"Time Dilation casts");
                        Arrow arrow=player.getWorld().spawnArrow(new Location(player.getWorld(),1.5,102,1.5),new org.bukkit.util.Vector(0,0,1),1,0);
                        double speed=arrow.getVelocity().length();
                        later(2,()->{
                            check(arrow.getVelocity().length()<speed*0.4,"time dome slows projectiles");
                            check(target.hasPotionEffect(PotionEffectType.MINING_FATIGUE),"time dome applies mining fatigue");arrow.remove();
                            reset();player.setHealth(4);check(plugin.spells().cast(player,Spell.SOUL_DRAIN),"Soul Drain channel starts");
                            for(int delay:new int[]{21,41,61})later(delay,()->getLogger().info("DRAIN health="+target.getHealth()+" caster="+player.getHealth()+" effects="+plugin.effects().size()+" clear="+plugin.context().clear(player.getEyeLocation(),target.getEyeLocation())));
                            later(62,this::finishSpells);
                        });
                    });
                });
            });
        });
    }
    void finishSpells() {
        check(target.getHealth()<=176,"Soul Drain deals three eight-health pulses");
        check(player.getHealth()>4,"Soul Drain heals caster from actual health drained");
        reset();World w=player.getWorld();
        for(int x=-1;x<=1;x++)for(int y=100;y<=102;y++)w.getBlockAt(x,y,3).setType(Material.STONE);
        check(plugin.spells().cast(player,Spell.SHADOW_STEP)&&player.getLocation().getZ()>4,"Shadow Step phases a one-block wall");
        check(plugin.context().safeBody(player.getLocation()),"blink destination has clear body space");
        for(int x=-1;x<=1;x++)for(int y=100;y<=102;y++)w.getBlockAt(x,y,3).setType(Material.AIR);
        reset();player.teleport(new Location(w,0.5,100,0.5,0,30));
        check(plugin.spells().cast(player,Spell.METEOR_STRIKE),"Meteor Strike marks ground");
        check(w.getEntitiesByClass(LargeFireball.class).isEmpty(),"meteor respects 1.5-second warning");
        later(32,()->{
            check(!w.getEntitiesByClass(LargeFireball.class).isEmpty(),"meteor drops native fireball after warning");
            later(25,()->{
                boolean fire=false;for(int x=-6;x<=6;x++)for(int z=-2;z<=10;z++)if(w.getBlockAt(x,100,z).getType()==Material.FIRE)fire=true;
                check(fire,"meteor ignites terrain");
                reset();check(plugin.effects().size()==0,"all temporary effects cleaned up");
                check(w.getEntitiesByClass(FallingBlock.class).isEmpty()&&w.getEntitiesByClass(AreaEffectCloud.class).isEmpty(),"temporary entities removed");
                getLogger().info("INTEGRATION COMPLETE: "+passed+" assertions passed");finish();
            });
        });
    }
    void finish(){
        Bukkit.getScheduler().cancelTasks(this);
        if(plugin!=null&&player!=null){plugin.effects().closeOwner(player.getUniqueId());plugin.statuses().clear(player);}
        if(target!=null)target.remove();
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
