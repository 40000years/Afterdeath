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
public final class BedrockChecks extends JavaPlugin implements Listener {
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

        for(Spell spell:Spell.values()) {
            actor.getInventory().clear();
            Bukkit.dispatchCommand(actor,"magic give "+spell.id());
            result("give_self_"+spell.id(),magic.wands().spell(actor.getInventory().getItem(0))==spell);
            for(int mode=0;mode<3;mode++) {
                ItemStack[] grid=new ItemStack[9];
                for(int i=0;i<9;i++)grid[i]=new ItemStack(mode==1||(mode==2&&i%2==0)?Material.NETHER_STAR:Material.NETHERITE_INGOT);
                grid[4]=garden.relics().createMagicCore(spell.id());
                var crafted=Bukkit.craftItemResult(grid,world,actor);
                result("craft_"+spell.id()+"_"+mode,magic.wands().spell(crafted.getResult())==spell && Arrays.stream(crafted.getResultingMatrix()).allMatch(i->i==null||i.getType().isAir()));
            }
        }
        actor.getInventory().clear();Bukkit.dispatchCommand(actor,"evergarden give key");
        result("evergarden_give_key",garden.relics().isVoidKey(actor.getInventory().getItem(0)));
        actor.getInventory().clear();Bukkit.dispatchCommand(actor,"magic givecore frost_nova");
        result("givecore_self",magic.wands().coreSpell(actor.getInventory().getItem(0))==Spell.FROST_NOVA);
        actor.getInventory().clear();
        actor.getInventory().setItem(0,garden.relics().createMagicCore("frost_nova"));
        actor.getInventory().setItem(1,new ItemStack(Material.NETHERITE_INGOT,3));
        actor.getInventory().setItem(2,new ItemStack(Material.NETHER_STAR,6));
        result("menu_mixed_craft_success",magic.itemMenu().craft(actor,Spell.FROST_NOVA)==null);
        result("menu_mixed_exact_consumption",actor.getInventory().contains(Material.NETHER_STAR,1)&&!actor.getInventory().contains(Material.NETHERITE_INGOT)&&magic.wands().spell(actor.getInventory().getItem(0))==Spell.FROST_NOVA);
        actor.getInventory().clear();actor.getInventory().setItem(0,magic.wands().createCore(Spell.FROST_NOVA));
        actor.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,7));
        var before=Arrays.stream(actor.getInventory().getStorageContents()).map(x->x==null?null:x.clone()).toArray(ItemStack[]::new);
        result("menu_insufficient_no_consumption",magic.itemMenu().craft(actor,Spell.FROST_NOVA)!=null&&Arrays.equals(before,actor.getInventory().getStorageContents()));
        actor.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,8));
        var deny=actor.addAttachment(this,"advance-magic.craft",false);
        result("menu_permission_denied",magic.itemMenu().craft(actor,Spell.FROST_NOVA)!=null&&actor.getInventory().contains(Material.NETHER_STAR,8));actor.removeAttachment(deny);
        actor.getInventory().clear();for(int n=0;n<36;n++)actor.getInventory().setItem(n,new ItemStack(Material.STONE,64));
        ItemStack stacked=magic.wands().createCore(Spell.FROST_NOVA);stacked.setAmount(2);actor.getInventory().setItem(0,stacked);
        actor.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,9));
        before=Arrays.stream(actor.getInventory().getStorageContents()).map(x->x==null?null:x.clone()).toArray(ItemStack[]::new);
        result("menu_full_inventory_no_consumption",magic.itemMenu().craft(actor,Spell.FROST_NOVA)!=null&&Arrays.equals(before,actor.getInventory().getStorageContents()));
        actor.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,8));
        result("menu_full_inventory_freed_slot_used",magic.itemMenu().craft(actor,Spell.FROST_NOVA)==null&&magic.wands().spell(actor.getInventory().getItem(1))==Spell.FROST_NOVA);
        actor.getInventory().clear();magic.itemMenu().open(actor,true);
        var view=actor.getOpenInventory();
        var click=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);
        result("admin_menu_click_server_grant",click.isCancelled()&&magic.wands().spell(actor.getInventory().getItem(0))==Spell.LIGHTNING_STRIKE);
        actor.getInventory().clear();deny=actor.addAttachment(this,"advance-magic.admin",false);
        click=new InventoryClickEvent(view,InventoryType.SlotType.CONTAINER,0,ClickType.LEFT,InventoryAction.PICKUP_ALL);Bukkit.getPluginManager().callEvent(click);
        result("admin_menu_rechecks_permission",actor.getInventory().isEmpty());actor.removeAttachment(deny);actor.closeInventory();
        var exact=(ShapedRecipe)Bukkit.getRecipe(new NamespacedKey(magic,"frost_nova"));
        result("wand_recipe_displays_tagged_core",magic.wands().coreSpell(exact.getChoiceMap().get(exact.getShape()[1].charAt(1)).getItemStack())==Spell.FROST_NOVA && !exact.getChoiceMap().get(exact.getShape()[1].charAt(1)).test(magic.wands().createCore(Spell.LIGHTNING_STRIKE)));
        var renamed=garden.relics().createMagicCore("frost_nova");var name=renamed.getItemMeta();name.setDisplayName("Renamed core");renamed.setItemMeta(name);
        ItemStack[] legacy=new ItemStack[9];Arrays.fill(legacy,new ItemStack(Material.NETHER_STAR));legacy[4]=renamed;
        result("renamed_core_still_crafts",magic.wands().spell(Bukkit.craftItem(legacy,world,actor))==Spell.FROST_NOVA);
        ItemStack[] shards=new ItemStack[9];for(int i:new int[]{0,1,3,4})shards[i]=garden.relics().createKeyShard(1);
        result("evergarden_key_tagged",garden.relics().isVoidKey(Bukkit.craftItem(shards,world,actor)));
        for(int i:new int[]{0,1,3,4})shards[i]=new ItemStack(Material.PRISMARINE_SHARD);
        result("evergarden_plain_shards_rejected",!garden.relics().isVoidKey(Bukkit.craftItem(shards,world,actor)));
        ItemStack[] dust=new ItemStack[9];
        for(int slot:new int[]{1,3,5,7})dust[slot]=garden.relics().createAstralDust(1);
        dust[4]=new ItemStack(Material.AMETHYST_SHARD);
        result("evergarden_repair_stone_crafts",garden.relics().isRepairStone(Bukkit.craftItem(dust,world,actor)));
        for(int slot:new int[]{1,3,5,7})dust[slot]=new ItemStack(Material.SUGAR);
        result("evergarden_plain_sugar_rejected",Bukkit.craftItem(dust,world,actor).getType().isAir());
        ItemStack[] repair=new ItemStack[9];repair[0]=garden.relics().createRepairStone(1);repair[1]=new ItemStack(Material.DIAMOND_PICKAXE);
        var dmg=(org.bukkit.inventory.meta.Damageable)repair[1].getItemMeta();dmg.setDamage(800);repair[1].setItemMeta(dmg);
        result("evergarden_repair_has_registered_recipe",Bukkit.getCraftingRecipe(repair,world)!=null);
        result("evergarden_repair_actual_output",Bukkit.craftItem(repair,world,actor));
        ItemStack[] conduit=new ItemStack[9];Arrays.fill(conduit,new ItemStack(Material.NAUTILUS_SHELL));conduit[4]=new ItemStack(Material.HEART_OF_THE_SEA);
        result("vanilla_conduit_restored",Bukkit.craftItem(conduit,world,actor).getType()==Material.CONDUIT);
    }
    void finish(){
        try{Files.write(Path.of("bedrock-result.txt"),results);}catch(Exception ignored){}
        if(handle!=null){MinecraftServer.getServer().getPlayerList().getPlayers().remove(handle);MinecraftServer.getServer().getPlayerList().getPlayersByUUID().remove(handle.getUUID());handle.discard();}
        Bukkit.getScheduler().runTaskLater(this,Bukkit::shutdown,5);
    }
}
