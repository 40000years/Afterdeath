package com.example.advancemagic;

import com.example.advancemagic.effect.*;
import com.example.advancemagic.item.WandService;
import com.example.advancemagic.mana.ManaService;
import com.example.advancemagic.spell.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public final class AdvanceMagicPlugin extends JavaPlugin implements Listener {
    private ManaService mana;
    private WandService wands;
    private EffectEngine effects;
    private StatusService statuses;
    private MagicContext context;
    private AreaSpells areas;
    private ProjectileSpells projectiles;
    private SpellRegistry spells;
    private CastListener casts;
    @Override public void onEnable() {
        saveDefaultConfig();mana=new ManaService(this);wands=new WandService(this);
        effects=new EffectEngine(this,getConfig().getInt("max-active-effects",128));
        context=new MagicContext(this);statuses=new StatusService(this);
        areas=new AreaSpells(context);projectiles=new ProjectileSpells(context);
        spells=new SpellRegistry(context,areas,projectiles);casts=new CastListener(this);
        for(Listener listener:List.of(this,wands,statuses,areas,projectiles,casts))getServer().getPluginManager().registerEvents(listener,this);
        wands.register();MagicCommand command=new MagicCommand(this);
        Objects.requireNonNull(getCommand("magic")).setExecutor(command);getCommand("magic").setTabCompleter(command);
        Bukkit.getScheduler().runTaskTimer(this,()->{effects.tick();statuses.tick();areas.tick();},1,1);
        Bukkit.getScheduler().runTaskTimer(this,()->Bukkit.getOnlinePlayers().forEach(mana::regenerate),20,20);
        for(Player p:Bukkit.getOnlinePlayers()){mana.account(p);wands.discover(p);}
        getLogger().info("15 spells and recipes registered. No client mod or packet dependency required.");
    }
    @Override public void onDisable() {
        Bukkit.getScheduler().cancelTasks(this);
        if(effects!=null)effects.close();if(statuses!=null)statuses.close();
        if(areas!=null)areas.close();if(projectiles!=null)projectiles.close();
        if(wands!=null)wands.close();if(mana!=null)Bukkit.getOnlinePlayers().forEach(mana::quit);
    }
    @EventHandler public void join(PlayerJoinEvent e) {
        Player p=e.getPlayer();mana.account(p);wands.discover(p);statuses.joined(p);
        Bukkit.getScheduler().runTaskLater(this,()->{if(p.isOnline()){statuses.joined(p);offerPack(p);}},30);
    }
    @EventHandler public void quit(PlayerQuitEvent e){cleanup(e.getPlayer());mana.quit(e.getPlayer());casts.quit(e.getPlayer());}
    @EventHandler public void death(PlayerDeathEvent e){cleanup(e.getEntity());mana.save(e.getEntity());}
    @EventHandler public void world(PlayerChangedWorldEvent e){cleanup(e.getPlayer());}
    private void cleanup(Player p){effects.closeOwner(p.getUniqueId());statuses.clear(p);}
    private boolean bedrock(Player p) {
        try {
            Class<?> api=Class.forName("org.geysermc.geyser.api.GeyserApi");Object instance=api.getMethod("api").invoke(null);
            return (boolean)api.getMethod("isBedrockPlayer",UUID.class).invoke(instance,p.getUniqueId());
        }catch(ReflectiveOperationException|LinkageError ignored){return false;}
    }
    private void offerPack(Player p) {
        String url=getConfig().getString("resource-pack.url","");
        if(url.isBlank()||bedrock(p))return;
        String hash=getConfig().getString("resource-pack.sha1","");
        if(!url.startsWith("https://")||!hash.matches("[a-fA-F0-9]{40}")){getLogger().warning("Resource pack requires an HTTPS URL and 40-character SHA-1.");return;}
        p.addResourcePack(UUID.fromString("3e8e5b71-0600-4a42-a678-483a7cce5fb0"),url,HexFormat.of().parseHex(hash),"Advance Magic wand textures",getConfig().getBoolean("resource-pack.required",false));
    }
    public ManaService mana(){return mana;}
    public WandService wands(){return wands;}
    public EffectEngine effects(){return effects;}
    public StatusService statuses(){return statuses;}
    public MagicContext context(){return context;}
    public AreaSpells areas(){return areas;}
    public ProjectileSpells projectiles(){return projectiles;}
    public SpellRegistry spells(){return spells;}
    public CastListener casts(){return casts;}
}
