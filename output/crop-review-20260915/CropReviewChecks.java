import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.*;
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
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;

/** Diagnostic reproduction only. Run in a disposable Paper 26.2 server; edits worlds and shuts down. */
public final class CropReviewChecks extends JavaPlugin implements Listener {
    static final class TestConnection extends Connection {
        TestConnection(){super(PacketFlow.SERVERBOUND);channel=new EmbeddedChannel();address=new InetSocketAddress("127.0.0.1",1);}
        @Override public void send(Packet<?> packet){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener){}
        @Override public void send(Packet<?> packet,ChannelFutureListener listener,boolean flush){}
        @Override public boolean isConnected(){return true;}
    }
    Player actor; ServerPlayer handle; boolean protect; int breakEvents;
    final List<String> results=new ArrayList<>();
    void result(String name,Object value){String line=name+": "+value;results.add(line);getLogger().info(line);}
    @EventHandler(priority=EventPriority.LOWEST) public void block(BlockBreakEvent e){if(protect){breakEvents++;e.setCancelled(true);}}
    @Override public void onEnable(){Bukkit.getScheduler().runTaskLater(this,()->{try{runChecks();}catch(Throwable t){result("HARNESS_ERROR",t);getLogger().log(java.util.logging.Level.SEVERE,"Probe failed",t);}finally{finish();}},20);}
    void runChecks() throws Exception {
        var garden=(VoidscapePlugin)Bukkit.getPluginManager().getPlugin("Evergarden");
        var magic=(AdvanceMagicPlugin)Bukkit.getPluginManager().getPlugin("advance-magic");
        result("both_plugins_enabled",garden.isEnabled()&&magic.isEnabled());
        World world=Bukkit.getWorlds().get(0);
        world.getChunkAt(0,0);world.getBlockAt(0,99,0).setType(Material.STONE);
        var server=MinecraftServer.getServer();var level=((CraftWorld)world).getHandle();
        GameProfile profile=new GameProfile(UUID.randomUUID(),".BedrockProbe");
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(0.5,100,0.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);actor=handle.getBukkitEntity();actor.setOp(true);actor.setGravity(false);actor.setGameMode(GameMode.SURVIVAL);


        var crops=garden.crops();var buffs=garden.cropBuffs();var factory=crops.factory();
        var type=com.example.voidscape.crop.CropType.MANA_DEW_BERRY;
        actor.setOp(false);actor.getInventory().clear();
        Bukkit.dispatchCommand(actor,"evergarden crops");
        var click=new InventoryClickEvent(actor.getOpenInventory(),InventoryType.SlotType.CONTAINER,45,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);
        result("BUG_non_admin_receives_seeds",Arrays.stream(actor.getInventory().getContents()).filter(factory::isSeed).count());
        actor.closeInventory();actor.setOp(true);
        int planted=0;
        for(var t:com.example.voidscape.crop.CropType.values()) {
            var soil=world.getBlockAt(planted%10,100,planted/10);soil.setType(Material.FARMLAND);soil.getRelative(org.bukkit.block.BlockFace.UP).setType(Material.AIR);
            var seed=factory.createSeed(t,2);actor.getInventory().setItemInMainHand(seed);
            var ev=new org.bukkit.event.player.PlayerInteractEvent(actor,org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK,seed,soil,org.bukkit.block.BlockFace.UP,EquipmentSlot.HAND);
            Bukkit.getPluginManager().callEvent(ev);
            var c=crops.getCropAt(soil.getLocation().add(0,1,0));
            result("plant_"+t.id,c!=null&&seed.getAmount()==1);
            planted++;
        }
        var first=crops.getCropAt(new Location(world,0,101,0));
        first.accelerate(type.tier.growthSeconds*3);crops.tick();
        result("mature_growth",first.isMature());
        long foodBefore=world.getEntitiesByClass(org.bukkit.entity.Item.class).stream().filter(i->factory.isFood(i.getItemStack())).count();
        crops.harvest(first,actor,false);crops.tick();
        result("BUG_harvest_overdue_immediately_mature",first.isMature());
        result("harvest_produces_food",world.getEntitiesByClass(org.bukkit.entity.Item.class).stream().filter(i->factory.isFood(i.getItemStack())).count()>foodBefore);
        Location original=first.getLocation().clone();
        buffs.onConsume(new org.bukkit.event.player.PlayerItemConsumeEvent(actor,factory.createFood(com.example.voidscape.crop.CropType.DEMETERS_MELON,1),EquipmentSlot.HAND));
        actor.teleport(original.clone().add(0,0,1));for(var c:crops.getPlantedCrops())c.setStage(2);first.setStage(0);buffs.tick();
        result("BUG_flora_aura_mutates_crop_location",!original.equals(first.getLocation()));
        var account=magic.mana().account(actor);account.setMaxMana(200);account.setMana(0);magic.mana().save(actor);
        buffs.onConsume(new org.bukkit.event.player.PlayerItemConsumeEvent(actor,factory.createFood(type,1),EquipmentSlot.HAND));
        result("BUG_mana_berry_actual_expected_50",account.manaExact());
        buffs.onConsume(new org.bukkit.event.player.PlayerItemConsumeEvent(actor,factory.createFood(com.example.voidscape.crop.CropType.FAIRY_MUSHROOM,1),EquipmentSlot.HAND));
        var field=buffs.getClass().getDeclaredField("doubleJumpUntil");field.setAccessible(true);
        ((Map<UUID,Long>)field.get(buffs)).put(actor.getUniqueId(),0L);buffs.tick();
        var flight=new org.bukkit.event.player.PlayerToggleFlightEvent(actor,true);buffs.onToggleFlight(flight);
        result("BUG_flight_still_allowed_after_expiry",actor.getAllowFlight()&&!flight.isCancelled());
        actor.setAllowFlight(false);
        crops.saveCrops();int before=crops.getPlantedCrops().size();crops.loadCrops();result("save_load_count",before==crops.getPlantedCrops().size());
        var mapField=crops.getClass().getDeclaredField("plantedCrops");mapField.setAccessible(true);
        var map=(Map<String,com.example.voidscape.crop.PlantedCrop>)mapField.get(crops);map.clear();
        for(int count:new int[]{100,1000,5000}) {
            map.clear();for(int i=0;i<count;i++)map.put("world,"+i+",101,0",new com.example.voidscape.crop.PlantedCrop(new Location(world,i,101,0),type,0,System.currentTimeMillis(),null,null));
            double[] times=new double[5];
            for(int i=0;i<5;i++){long start=System.nanoTime();crops.saveCrops();times[i]=(System.nanoTime()-start)/1e6;}
            Arrays.sort(times);result("save_"+count+"_median_ms",times[2]);result("save_"+count+"_max_ms",times[4]);
        }
        map.clear();crops.saveCrops();
    }

    void finish(){
        try{Files.write(Path.of("crop-review-result.txt"),results);}catch(Exception ignored){}
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
