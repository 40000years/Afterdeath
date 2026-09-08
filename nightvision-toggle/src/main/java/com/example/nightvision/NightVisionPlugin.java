package com.example.nightvision;

import com.example.nightvision.command.NightVisionCommand;
import com.example.nightvision.listener.NightVisionListener;
import com.example.nightvision.manager.NightVisionManager;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class NightVisionPlugin extends JavaPlugin {

    private static NightVisionPlugin instance;
    private NightVisionManager manager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.manager = new NightVisionManager(this);

        // ลงทะเบียน Event Listener
        getServer().getPluginManager().registerEvents(new NightVisionListener(this, manager), this);

        // ลงทะเบียน Command
        PluginCommand cmd = getCommand("nv");
        if (cmd != null) {
            NightVisionCommand executor = new NightVisionCommand(this, manager);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        }

        // โหลดข้อมูลสำหรับผู้เล่นทุกคนที่ออนไลน์อยู่ขณะนี้ (กรณี reload plugin)
        for (Player player : Bukkit.getOnlinePlayers()) {
            manager.loadPlayer(player);
        }

        getLogger().info("NightVisionToggle v" + getDescription().getVersion() + " เปิดใช้งานเรียบร้อยแล้ว!");
    }

    @Override
    public void onDisable() {
        getLogger().info("NightVisionToggle ปิดการทำงานเรียบร้อย");
        instance = null;
    }

    public static NightVisionPlugin getInstance() {
        return instance;
    }

    public NightVisionManager getManager() {
        return manager;
    }
}
