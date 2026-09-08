package com.example.nightvision.command;

import com.example.nightvision.NightVisionPlugin;
import com.example.nightvision.manager.NightVisionManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class NightVisionCommand implements CommandExecutor, TabCompleter {

    private final NightVisionPlugin plugin;
    private final NightVisionManager manager;

    public NightVisionCommand(NightVisionPlugin plugin, NightVisionManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("nightvision.admin")) {
                sender.sendMessage(Component.text("คุณไม่มีสิทธิ์ใช้งานคำสั่ง reload", NamedTextColor.RED));
                return true;
            }
            plugin.reloadConfig();
            sender.sendMessage(Component.text("[NightVision] รีโหลดการตั้งค่าเรียบร้อยแล้ว!", NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("คำสั่งนี้ใช้ได้เฉพาะผู้เล่นเท่านั้น (สำหรับ console ให้ใช้ /" + label + " reload)", NamedTextColor.RED));
            return true;
        }

        if (!player.hasPermission("nightvision.use")) {
            manager.sendMessage(player, "messages.no-permission");
            return true;
        }

        if (args.length == 0) {
            // สลับสถานะ (Toggle)
            manager.toggle(player);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "on" -> manager.setEnabled(player, true, true);
            case "off" -> manager.setEnabled(player, false, true);
            case "toggle" -> manager.toggle(player);
            case "status" -> {
                boolean active = manager.isEnabled(player);
                manager.sendMessage(player, active ? "messages.status-on" : "messages.status-off");
            }
            default -> {
                // ตรวจสอบว่าเป็นคำสั่งสั่งผู้เล่นอื่นหรือไม่ /nv <player> [on|off]
                if (player.hasPermission("nightvision.admin")) {
                    Player target = Bukkit.getPlayer(args[0]);
                    if (target != null && target.isOnline()) {
                        boolean targetState = args.length > 1 && args[1].equalsIgnoreCase("on");
                        if (args.length > 1 && args[1].equalsIgnoreCase("off")) {
                            targetState = false;
                        } else if (args.length == 1) {
                            targetState = !manager.isEnabled(target);
                        }
                        manager.setEnabled(target, targetState, true);
                        player.sendMessage(Component.text("ปรับสถานะ NightVision ของ " + target.getName() + " เป็น " + (targetState ? "ON" : "OFF"), NamedTextColor.GREEN));
                        return true;
                    }
                }
                player.sendMessage(Component.text("การใช้งาน: /" + label + " [on|off|toggle|status]", NamedTextColor.YELLOW));
            }
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> list = new ArrayList<>();
        if (args.length == 1) {
            List<String> subCommands = new ArrayList<>(List.of("on", "off", "toggle", "status"));
            if (sender.hasPermission("nightvision.admin")) {
                subCommands.add("reload");
                for (Player online : Bukkit.getOnlinePlayers()) {
                    subCommands.add(online.getName());
                }
            }
            for (String sub : subCommands) {
                if (sub.toLowerCase().startsWith(args[0].toLowerCase())) {
                    list.add(sub);
                }
            }
        } else if (args.length == 2 && sender.hasPermission("nightvision.admin")) {
            for (String sub : List.of("on", "off")) {
                if (sub.toLowerCase().startsWith(args[1].toLowerCase())) {
                    list.add(sub);
                }
            }
        }
        return list;
    }
}
