package com.example.antifreecam;

import com.example.antifreecam.command.AntiFreecamCommand;
import com.example.antifreecam.listener.FreecamInteractListener;
import com.example.antifreecam.masker.ContainerMasker;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class AntiFreecamPlugin extends JavaPlugin {

    private static AntiFreecamPlugin instance;
    private ContainerMasker masker;
    private BukkitTask scanTask;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        this.masker = new ContainerMasker(this);

        // ลงทะเบียน Event Listener
        getServer().getPluginManager().registerEvents(new FreecamInteractListener(this, masker), this);

        // ลงทะเบียน Command
        PluginCommand cmd = getCommand("antifreecam");
        if (cmd != null) {
            cmd.setExecutor(new AntiFreecamCommand(this));
        }

        // เริ่ม Task สแกนพรางคอนเทนเนอร์และสิ่งมีค่าใต้ดินรอบตัวผู้เล่นแบบเงียบๆ
        startScanTask();

        getLogger().info("AntiFreecam v" + getDescription().getVersion() + " เปิดใช้งานเรียบร้อย (ระบบพรางคอนเทนเนอร์แบบเงียบ)");
    }

    @Override
    public void onDisable() {
        if (scanTask != null) {
            scanTask.cancel();
            scanTask = null;
        }

        // คืนค่าบล็อกจริงทั้งหมดให้ผู้เล่นเมื่อปิดปลั๊กอิน
        if (masker != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                masker.restorePlayer(player);
            }
        }

        getLogger().info("AntiFreecam ปิดการทำงานเรียบร้อย");
        instance = null;
    }

    public void startScanTask() {
        if (scanTask != null) {
            scanTask.cancel();
        }

        long interval = getConfig().getLong("masking.update-interval-ticks", 12L);
        scanTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                masker.updatePlayer(player);
            }
        }, 10L, interval);
    }

    @Override
    public void reloadConfig() {
        super.reloadConfig();
        startScanTask();
    }

    public static AntiFreecamPlugin getInstance() {
        return instance;
    }

    public ContainerMasker getMasker() {
        return masker;
    }
}
