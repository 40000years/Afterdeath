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
public final class RecheckChecks extends JavaPlugin implements Listener {
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
    @EventHandler(priority=EventPriority.LOWEST) public void block(BlockBreakEvent e){if(protect && e.getBlock().getX()==2){breakEvents++;e.setCancelled(true);}}
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


        Bukkit.getPluginManager().registerEvents(this,this);
        actor.setOp(false);
        result("normal_cast_allowed",magic.casts().canCast(actor));
        result("normal_craft_allowed",magic.wands().canCraft(actor));
        Bukkit.dispatchCommand(actor,"evergarden crops");
        result("normal_crop_menu_opened",actor.getOpenInventory().getTopInventory().getHolder() instanceof com.example.voidscape.crop.CropShowcaseGui);
        actor.closeInventory();
        garden.cropGui().open(actor);
        var click=new InventoryClickEvent(actor.getOpenInventory(),InventoryType.SlotType.CONTAINER,45,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);
        result("normal_crop_menu_no_free_seeds",actor.getInventory().isEmpty());actor.closeInventory();
        actor.getInventory().setItem(0,magic.wands().createCore(Spell.FROST_NOVA));actor.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,8));
        result("normal_menu_craft_success",magic.itemMenu().craft(actor,Spell.FROST_NOVA)==null);
        actor.getInventory().clear();
        // Neighbouring block protection denies x=2, but allows the origin at x=1.
        var listener=new UniqueAbilityListener(garden);
        var pick=new ItemStack(Material.NETHERITE_PICKAXE);var meta=pick.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","ue_"+UniqueEnchant.VEIN_SMELTER.id()),PersistentDataType.BYTE,(byte)1);pick.setItemMeta(meta);
        actor.getInventory().setItemInMainHand(pick);
        var origin=world.getBlockAt(1,100,0);var neighbour=world.getBlockAt(2,100,0);
        origin.setType(Material.DIAMOND_ORE);neighbour.setType(Material.DIAMOND_ORE);protect=true;breakEvents=0;
        listener.onBlockBreak(new BlockBreakEvent(origin,actor));
        result("BUG_vein_smelt_removes_protected_neighbour",neighbour.getType()==Material.AIR);
        result("vein_smelt_protection_events",breakEvents);
        var hoe=new ItemStack(Material.NETHERITE_HOE);meta=hoe.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","ue_"+UniqueEnchant.DEMETER_SCYTHE.id()),PersistentDataType.BYTE,(byte)1);hoe.setItemMeta(meta);actor.getInventory().setItemInMainHand(hoe);
        for(var b:List.of(origin,neighbour)){b.getRelative(0,-1,0).setType(Material.FARMLAND);b.setType(Material.WHEAT);var age=(org.bukkit.block.data.Ageable)b.getBlockData();age.setAge(age.getMaximumAge());b.setBlockData(age);}
        breakEvents=0;listener.onBlockBreak(new BlockBreakEvent(origin,actor));
        result("BUG_scythe_harvests_protected_neighbour",((org.bukkit.block.data.Ageable)neighbour.getBlockData()).getAge()==0);
        result("scythe_protection_events",breakEvents);protect=false;
        // One tree break consumes every charge through recursive synthetic events.
        var field=garden.cropBuffs().getClass().getDeclaredField("treeFellerCharges");field.setAccessible(true);
        var charges=(Map<UUID,Integer>)field.get(garden.cropBuffs());charges.put(actor.getUniqueId(),5);
        origin.setType(Material.OAK_LOG);neighbour.setType(Material.OAK_LOG);actor.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
        garden.cropBuffs().onOreBreak(new BlockBreakEvent(origin,actor));
        result("tree_charges_after_one_break_expected_4",charges.get(actor.getUniqueId()));
        // Two worlds with different day counters allow alternating daily drinks.
        world.setFullTime(24000L*10);garden.world().setFullTime(24000L*20);
        actor.getInventory().setItemInMainHand(new ItemStack(Material.DRAGON_BREATH,10));
        boolean d1=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        actor.teleport(garden.world().getSpawnLocation());
        boolean d2=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        actor.teleport(new Location(world,0.5,100,0.5));
        boolean d3=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        result("BUG_daily_drink_A_B_A_all_succeed",d1&&d2&&d3);
        // Failed casts still trigger the food buff's explosion before the mana check.
        var buffs=garden.cropBuffs();var f=buffs.getClass().getDeclaredField("omniReboundUntil");f.setAccessible(true);
        ((Map<UUID,Long>)f.get(buffs)).put(actor.getUniqueId(),System.currentTimeMillis()+60000);
        var monster=world.spawn(actor.getLocation().add(0,0,2),org.bukkit.entity.Zombie.class);monster.setAI(false);
        magic.mana().account(actor).setMana(0);double hp=monster.getHealth();
        boolean cast=magic.casts().cast(actor,Spell.FROST_NOVA,magic.wands().create(Spell.FROST_NOVA));
        result("BUG_failed_cast_damages_monster",!cast&&monster.getHealth()<hp);monster.remove();
    }

    void finish(){
        try{Files.write(Path.of("recheck-result.txt"),results);}catch(Exception ignored){}
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
