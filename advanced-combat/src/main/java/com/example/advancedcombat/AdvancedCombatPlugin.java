package com.example.advancedcombat;

import com.example.advancedcombat.hook.FloodgateHook;
import com.example.advancedcombat.listener.AxeShieldStunListener;
import com.example.advancedcombat.listener.BedrockMaceListener;
import com.example.advancedcombat.listener.BedrockSweepListener;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public class AdvancedCombatPlugin extends JavaPlugin implements CommandExecutor {

    private static AdvancedCombatPlugin instance;
    private boolean floodgateAvailable = false;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (getServer().getPluginManager().isPluginEnabled("floodgate")) {
            floodgateAvailable = true;
            getLogger().info("ตรวจพบ Floodgate — เปิดใช้งานระบบปรับสมดุล Combat สำหรับ Bedrock");
        } else {
            getLogger().info("ไม่พบ Floodgate — ระบบจะทำงานตามค่า config");
        }

        // ลงทะเบียน Event Listeners
        getServer().getPluginManager().registerEvents(new BedrockMaceListener(this), this);
        getServer().getPluginManager().registerEvents(new BedrockSweepListener(this), this);
        getServer().getPluginManager().registerEvents(new AxeShieldStunListener(this), this);

        // ลงทะเบียน Command
        if (getCommand("advancedcombat") != null) {
            getCommand("advancedcombat").setExecutor(this);
        }

        getLogger().info("AdvancedCombat v" + getDescription().getVersion() + " เปิดใช้งานเรียบร้อย (Vanilla Java 26.2 Parity)");
    }

    @Override
    public void onDisable() {
        getLogger().info("AdvancedCombat ปิดการทำงานเรียบร้อย");
        instance = null;
    }

    public static AdvancedCombatPlugin getInstance() {
        return instance;
    }

    public boolean isBedrockPlayer(UUID uuid) {
        if (!floodgateAvailable || uuid == null) {
            return false;
        }
        return FloodgateHook.isBedrockPlayer(uuid);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("advancedcombat.admin")) {
                sender.sendMessage(Component.text("คุณไม่มีสิทธิ์ใช้งานคำสั่งนี้", NamedTextColor.RED));
                return true;
            }
            reloadConfig();
            sender.sendMessage(Component.text("[AdvancedCombat] รีโหลดการตั้งค่าเรียบร้อยแล้ว!", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("=== AdvancedCombat v" + getDescription().getVersion() + " ===", NamedTextColor.GOLD));
        sender.sendMessage(Component.text("คำสั่ง: /" + label + " reload เพื่อรีโหลดการตั้งค่า", NamedTextColor.YELLOW));
        return true;
    }
}
