package com.example.antifreecam.command;

import com.example.antifreecam.AntiFreecamPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class AntiFreecamCommand implements CommandExecutor {

    private final AntiFreecamPlugin plugin;

    public AntiFreecamCommand(AntiFreecamPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("antifreecam.admin")) {
                sender.sendMessage(Component.text("คุณไม่มีสิทธิ์ใช้งานคำสั่งนี้", NamedTextColor.RED));
                return true;
            }

            plugin.reloadConfig();
            sender.sendMessage(Component.text("[AntiFreecam] รีโหลดการตั้งค่าระบบพรางคอนเทนเนอร์เรียบร้อยแล้ว!", NamedTextColor.GREEN));
            return true;
        }

        sender.sendMessage(Component.text("=== AntiFreecam (Silent Container Masking) ===", NamedTextColor.DARK_AQUA));
        sender.sendMessage(Component.text("คำสั่ง: /" + label + " reload เพื่อรีโหลดการตั้งค่า", NamedTextColor.GRAY));
        return true;
    }
}
