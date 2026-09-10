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

    @Override public void onLoad() {
        extractResourcePacks();
    }

    public void extractResourcePacks() {
        try {
            Path output = getDataFolder().toPath().resolve("resource-packs");
            Files.createDirectories(output);
            String[] files = {"voidscape-java.zip", "voidscape-bedrock.mcpack", "geyser-mappings.json", "pack-hashes.json"};
            for (String file : files) {
                try (InputStream in = getResource("resource-packs/" + file)) {
                    if (in != null) {
                        Path target = output.resolve(file);
                        byte[] bytes = in.readAllBytes();
                        if (!Files.exists(target) || !Arrays.equals(Files.readAllBytes(target), bytes)) {
                            Files.write(target, bytes);
                        }
                    }
                }
            }
            Path pluginsDir = getDataFolder().toPath().toAbsolutePath().getParent();
            if (pluginsDir != null) {
                Path geyser = pluginsDir.resolve("Geyser-Spigot");
                if (!Files.isDirectory(geyser)) {
                    geyser = pluginsDir.resolve("Geyser");
                }
                if (Files.isDirectory(geyser)) {
                    Path bedrockPack = output.resolve("voidscape-bedrock.mcpack");
                    Path mappings = output.resolve("geyser-mappings.json");
                    if (Files.exists(bedrockPack)) {
                        Path destPack = geyser.resolve("packs/voidscape-bedrock.mcpack");
                        Files.createDirectories(destPack.getParent());
                        Files.copy(bedrockPack, destPack, StandardCopyOption.REPLACE_EXISTING);
                    }
                    if (Files.exists(mappings)) {
                        Path destMapping = geyser.resolve("custom_mappings/voidscape.json");
                        Files.createDirectories(destMapping.getParent());
                        Files.copy(mappings, destMapping, StandardCopyOption.REPLACE_EXISTING);
                    }
                    getLogger().info("Voidscape Bedrock pack & mappings auto-installed to Geyser.");
                }
            }
        } catch (Exception e) {
            getLogger().warning("Resource pack extraction: " + e.getMessage());
        }
    }

    @Override public void onEnable() {
        try {
            extractResourcePacks();
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
                saved.set("spacing",integer("structures.spacing-chunks",48,24,256));
                saved.set("chance",getConfig().getDouble("structures.chance",0.70));
                saved.save(file);
            }
            long seed=saved.getLong("seed");
            layout=new DungeonLayout(seed,saved.getInt("spacing",48),saved.getDouble("chance",0.70));
            String worldName=saved.getString("world","the_void_v2");
            if(worldName.equals("the_void"))throw new IllegalStateException("Use a new world name for v2; never replace the legacy world generator.");
            voidWorld=new WorldCreator(worldName).seed(seed).environment(World.Environment.NORMAL).generator(new VoidGenerator(seed,layout)).createWorld();
            if(voidWorld==null)throw new IllegalStateException("Cannot load Void world");
            voidWorld.setSpawnLocation(0,97,0);voidWorld.setTime(18000);
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
            VoidCommand command=new VoidCommand(this);
            getCommand("voidscape").setExecutor(command);getCommand("voidscape").setTabCompleter(command);
            getServer().getScheduler().runTaskTimer(this,()->{dungeons.tick();travel.tick();relics.tick();},20,10);
            getLogger().info("Voidscape 2.0 (Advance Magic Expansion) enabled in "+worldName);
        } catch(Exception e) {
            getLogger().log(java.util.logging.Level.SEVERE,"Voidscape failed to start safely",e);
            getServer().getPluginManager().disablePlugin(this);
        }
    }
    @Override public void onDisable(){if(dungeons!=null)dungeons.close();if(relics!=null)relics.close();}
    public NamespacedKey key(String value){return new NamespacedKey(this,value);}
    public int integer(String path,int value,int min,int max){return Math.max(min,Math.min(max,getConfig().getInt(path,value)));}
    public void message(CommandSender sender,String text){sender.sendMessage(Component.text("✦ "+text,NamedTextColor.AQUA));}
    public World world(){return voidWorld;} public DungeonLayout layout(){return layout;}
    public RelicService relics(){return relics;} public DungeonManager dungeons(){return dungeons;} public TravelListener travel(){return travel;}
}
