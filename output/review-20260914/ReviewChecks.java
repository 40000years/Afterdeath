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
public final class ReviewChecks extends JavaPlugin implements Listener {
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
        GameProfile profile=new GameProfile(UUID.randomUUID(),"ReviewActor");
        handle=new ServerPlayer(server,level,profile,ClientInformation.createDefault());
        handle.connection=new ServerGamePacketListenerImpl(server,new TestConnection(),handle,CommonListenerCookie.createInitial(profile,false));
        handle.setPos(0.5,100,0.5);server.getPlayerList().getPlayers().add(handle);server.getPlayerList().getPlayersByUUID().put(profile.id(),handle);
        level.addNewPlayer(handle);actor=handle.getBukkitEntity();actor.setOp(true);actor.setGravity(false);actor.setGameMode(GameMode.SURVIVAL);

        ItemStack core=magic.wands().createCore(Spell.FROST_NOVA);var meta=core.getItemMeta();
        meta.getPersistentDataContainer().remove(new NamespacedKey(magic,"core"));
        meta.getPersistentDataContainer().set(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING,"shadow_step");core.setItemMeta(meta);
        result("conflicting_legacy_and_evergarden_core_should_reject",magic.wands().coreSpell(core));

        world.setFullTime(24000L);garden.world().setFullTime(48000L);
        actor.getInventory().setItemInMainHand(new ItemStack(Material.DRAGON_BREATH,3));
        boolean first=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        actor.teleport(new Location(garden.world(),0.5,97,0.5));
        boolean second=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        actor.teleport(new Location(world,0.5,100,0.5));
        boolean third=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        result("three_drinks_same_tick_world_day_1_2_1",first+","+second+","+third+"; maxMana="+magic.mana().account(actor).maxMana());

        ItemStack sword=new ItemStack(Material.DIAMOND_SWORD);
        actor.setItemOnCursor(garden.relics().createScrollEternity());
        var click=new InventoryClickEvent(actor.getOpenInventory(),InventoryType.SlotType.CONTAINER,9,ClickType.LEFT,InventoryAction.SWAP_WITH_CURSOR);
        click.setCurrentItem(sword);click.setCancelled(true);
        new EnchantApplyListener(garden,garden.relics()).onInventoryClick(click);
        result("cancelled_inventory_click_still_enchants",click.getCurrentItem().getItemMeta().isUnbreakable());

        var abilities=new UniqueAbilityListener(garden);
        ItemStack bound=new ItemStack(Material.DIAMOND_SWORD);meta=bound.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","ue_soulbound"),PersistentDataType.BYTE,(byte)1);bound.setItemMeta(meta);
        var death=new PlayerDeathEvent(actor,org.bukkit.damage.DamageSource.builder(org.bukkit.damage.DamageType.GENERIC).build(),new ArrayList<>(List.of(bound)),0,(String)null);
        abilities.onPlayerDeath(death);
        var stash=UniqueAbilityListener.class.getDeclaredField("soulboundStash");stash.setAccessible(true);
        result("soulbound_only_in_volatile_map","drops="+death.getDrops().size()+"; itemsToKeep="+death.getItemsToKeep().size()+"; stash="+((Map<?,?>)stash.get(abilities)).size()+"; freshListenerStash="+((Map<?,?>)stash.get(new UniqueAbilityListener(garden))).size());

        Bukkit.getPluginManager().registerEvents(this,this);protect=true;breakEvents=0;
        var ore=world.getBlockAt(3,99,3);ore.setType(Material.IRON_ORE);
        actor.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_PICKAXE));
        var vein=UniqueAbilityListener.class.getDeclaredMethod("mineVeinSmelt",Player.class,org.bukkit.block.Block.class);vein.setAccessible(true);vein.invoke(abilities,actor,ore);
        result("protected_vein_helper_bypasses_block_break","block="+ore.getType()+"; protectionEvents="+breakEvents);

        var crop=world.getBlockAt(4,100,4);world.getBlockAt(4,99,4).setType(Material.FARMLAND);crop.setType(Material.WHEAT);
        var age=(org.bukkit.block.data.Ageable)crop.getBlockData();age.setAge(age.getMaximumAge());crop.setBlockData(age);
        var harvest=UniqueAbilityListener.class.getDeclaredMethod("harvestCropsArea",Player.class,org.bukkit.block.Block.class);harvest.setAccessible(true);harvest.invoke(abilities,actor,crop);
        result("protected_harvest_helper_bypasses_block_break","age="+((org.bukkit.block.data.Ageable)crop.getBlockData()).getAge()+"; protectionEvents="+breakEvents);
        protect=false;

        ItemStack legs=new ItemStack(Material.NETHERITE_LEGGINGS);meta=legs.getItemMeta();meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","ue_titan_stance"),PersistentDataType.BYTE,(byte)1);legs.setItemMeta(meta);actor.getInventory().setLeggings(legs);actor.getInventory().setChestplate(null);
        var blast=new org.bukkit.event.entity.EntityDamageEvent(actor,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,10.0);
        abilities.onFatalDamage(blast);result("titan_without_chestplate_expected_6",blast.getDamage());
        actor.getInventory().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
        var blast2=new org.bukkit.event.entity.EntityDamageEvent(actor,org.bukkit.event.entity.EntityDamageEvent.DamageCause.ENTITY_EXPLOSION,10.0);
        abilities.onFatalDamage(blast2);result("titan_plain_chestplate_expected_6",blast2.getDamage());
    }
    void finish(){
        try{Files.write(Path.of("review-result.txt"),results);}catch(Exception ignored){}
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
