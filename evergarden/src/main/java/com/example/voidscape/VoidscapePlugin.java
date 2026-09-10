package com.example.voidscape;

import com.example.voidscape.world.*;
import com.example.voidscape.item.RelicService;
import com.example.voidscape.dungeon.DungeonManager;
import com.example.voidscape.listener.TravelListener;
import com.example.voidscape.command.VoidCommand;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public final class VoidscapePlugin extends JavaPlugin {
    private World voidWorld;
    private DungeonLayout layout;
    private RelicService relics;
    private DungeonManager dungeons;
    private TravelListener travel;

    private com.example.voidscape.pack.ResourcePackService packs;
    private boolean loadFailed;
    @Override public void onLoad() {
        try {
            // A branding change must keep the exact layout, reward ledger and configuration.
            Path current=getDataFolder().toPath();
            Path legacy=current.resolveSibling("Voidscape");
            if(Files.isDirectory(legacy)) {
                Files.createDirectories(current);
                for(String file:List.of("config.yml","world-layout.yml","dungeons.yml")) {
                    Path source=legacy.resolve(file),target=current.resolve(file);
                    if(Files.exists(source)&&!Files.exists(target))Files.copy(source,target);
                }
            }
            saveDefaultConfig();getConfig().options().copyDefaults(true);saveConfig();
            packs=new com.example.voidscape.pack.ResourcePackService(this);packs.extract();
        } catch(Exception error) {loadFailed=true;getLogger().log(java.util.logging.Level.SEVERE,"Cannot safely initialize Evergarden",error);}
    }

    @Override public void onEnable() {
        try {
            if(loadFailed)throw new IllegalStateException("Evergarden initialization failed");
            saveDefaultConfig();
            if(getConfig().getInt("config-version",0)<2) {
                Path old=getDataFolder().toPath().resolve("config.yml");
                Path backup=getDataFolder().toPath().resolve("config-v1-backup.yml");
                if(!Files.exists(backup))Files.copy(old,backup);
                saveResource("config.yml",true);reloadConfig();
                getLogger().info("Archived v1 config; v2 uses a separate world. Original world is preserved.");
            }
            // Placement parameters are locked per world; changing density must never move existing dungeons.
            File file=new File(getDataFolder(),"world-layout.yml");
            YamlConfiguration saved=YamlConfiguration.loadConfiguration(file);
            if(!file.exists()) {
                saved.set("world",getConfig().getString("dimension.world-name","the_void_v2"));
                saved.set("seed",getConfig().getLong("dimension.seed",72819345L));
                saved.set("spacing",integer("structures.spacing-chunks",18,14,64));
                saved.set("chance",getConfig().getDouble("structures.chance",1.0));
                saved.save(file);
            }
            long seed=saved.getLong("seed");
            layout=new DungeonLayout(seed,saved.getInt("spacing",18),saved.getDouble("chance",1.0));
            String worldName=saved.getString("world","the_void_v2");
            if(worldName.equals("the_void"))throw new IllegalStateException("Use a new world name for v2; never replace the legacy world generator.");
            voidWorld=new WorldCreator(worldName).seed(seed).environment(World.Environment.NORMAL).generator(new VoidGenerator(seed,layout)).createWorld();
            if(voidWorld==null)throw new IllegalStateException("Cannot load Void world");
            voidWorld.setSpawnLocation(0,97,0);voidWorld.setTime(integer("dimension.time",13000,0,23999));
            voidWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE,false);
            voidWorld.setGameRule(GameRule.DO_WEATHER_CYCLE,false);
            voidWorld.setGameRule(GameRule.DO_MOB_SPAWNING,false);
            voidWorld.setGameRule(GameRule.DO_PATROL_SPAWNING,false);
            voidWorld.setGameRule(GameRule.DO_TRADER_SPAWNING,false);
            voidWorld.setStorm(false);voidWorld.setThundering(false);
            voidWorld.getWorldBorder().setCenter(0,0);
            voidWorld.getWorldBorder().setSize(integer("dimension.border-size",24000,4096,60000));
            relics=new RelicService(this);dungeons=new DungeonManager(this);travel=new TravelListener(this);
            var pm=getServer().getPluginManager();pm.registerEvents(relics,this);pm.registerEvents(dungeons,this);pm.registerEvents(travel,this);
            packs.start();pm.registerEvents(packs,this);
            getServer().getOnlinePlayers().forEach(p->{relics.migrate(p.getInventory());relics.migrate(p.getEnderChest());packs.offer(p);});
            getServer().getWorlds().forEach(w->w.getEntities().forEach(relics::migrateEntity));
            VoidCommand command=new VoidCommand(this);
            getCommand("evergarden").setExecutor(command);getCommand("evergarden").setTabCompleter(command);
            getServer().getScheduler().runTaskTimer(this,()->{dungeons.tick();travel.tick();relics.tick();},20,10);
            getLogger().info("Evergarden 3.0 enabled in "+worldName);
        } catch(Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE,"Evergarden failed to start safely",e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable(){if(packs!=null)packs.close();if(dungeons!=null)dungeons.close();if(relics!=null)relics.close();}
    public NamespacedKey key(String value){return new NamespacedKey("voidscape",value);}
    public int integer(String path,int value,int min,int max){return Math.max(min,Math.min(max,getConfig().getInt(path,value)));}
    public void message(CommandSender sender,String text){sender.sendMessage(Component.text("✦ "+text,NamedTextColor.AQUA));}
    public com.example.voidscape.pack.ResourcePackService packs(){return packs;}
    public World world(){return voidWorld;} public DungeonLayout layout(){return layout;}
    public RelicService relics(){return relics;} public DungeonManager dungeons(){return dungeons;} public TravelListener travel(){return travel;}
}
