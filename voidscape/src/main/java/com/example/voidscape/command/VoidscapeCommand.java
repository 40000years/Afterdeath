package com.example.voidscape.command;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.VoidItemManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class VoidscapeCommand implements CommandExecutor {

    private final VoidscapePlugin plugin;
    private final VoidItemManager itemManager;

    public VoidscapeCommand(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.itemManager = plugin.getItemManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("voidscape.admin")) {
            sender.sendMessage(Component.text("คุณไม่มีสิทธิ์ใช้งานคำสั่งนี้", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        // /voidscape reload
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            sender.sendMessage(Component.text("✅ โหลด config.yml ของ Voidscape เรียบร้อย!", NamedTextColor.GREEN));
            return true;
        }

        // /voidscape tp
        if (args[0].equalsIgnoreCase("tp")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("คำสั่งนี้ใช้ได้เฉพาะในเกม", NamedTextColor.RED));
                return true;
            }

            World voidWorld = plugin.getVoidWorld();
            if (voidWorld == null) {
                player.sendMessage(Component.text("ไม่พบโลก The Void", NamedTextColor.RED));
                return true;
            }

            // ถ้าอยู่ใน The Void ให้ส่งกลับ Overworld
            if (player.getWorld().getName().equals(plugin.getVoidWorldName())) {
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS);
                player.removePotionEffect(org.bukkit.potion.PotionEffectType.DARKNESS);
                World overworld = Bukkit.getWorlds().get(0);
                player.teleport(overworld.getSpawnLocation());
                player.sendMessage(Component.text("🌀 วาร์ปกลับสู่ Overworld", NamedTextColor.AQUA));
            } else {
                Location loc = voidWorld.getSpawnLocation();
                loc.setY(261.0);
                player.teleport(loc);
                player.sendMessage(Component.text("🌌 วาร์ปเข้าสู่ The Void", NamedTextColor.DARK_PURPLE));
            }
            return true;
        }

        // /voidscape give <crystal|fruit|blade|pocket|armor>
        if (args[0].equalsIgnoreCase("give") && args.length >= 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("คำสั่งนี้ใช้ได้เฉพาะในเกม", NamedTextColor.RED));
                return true;
            }

            String itemType = args[1].toLowerCase();
            switch (itemType) {
                case "crystal" -> {
                    player.getInventory().addItem(itemManager.createVoidCrystal(8));
                    player.sendMessage(Component.text("✅ ได้รับ Voidic Crystal x8", NamedTextColor.GREEN));
                }
                case "fruit" -> {
                    player.getInventory().addItem(itemManager.createNullFruit(4));
                    player.sendMessage(Component.text("✅ ได้รับ Null Fruit x4", NamedTextColor.GREEN));
                }
                case "blade" -> {
                    player.getInventory().addItem(itemManager.createShadowBlade());
                    player.sendMessage(Component.text("✅ ได้รับ Shadow Blade", NamedTextColor.GREEN));
                }
                case "pocket" -> {
                    player.getInventory().addItem(itemManager.createPocketVoid());
                    player.sendMessage(Component.text("✅ ได้รับ Pocket Void", NamedTextColor.GREEN));
                }
                case "armor" -> {
                    player.getInventory().addItem(itemManager.createVoidArmor(Material.NETHERITE_CHESTPLATE, "เกราะอกแห่งความมืด (Voidic Chestplate)"));
                    player.getInventory().addItem(itemManager.createVoidArmor(Material.NETHERITE_BOOTS, "รองเท้าแห่งความมืด (Voidic Boots)"));
                    player.sendMessage(Component.text("✅ ได้รับ Voidic Armor Set", NamedTextColor.GREEN));
                }
                default -> player.sendMessage(Component.text("ไม่พบไอเทม: crystal, fruit, blade, pocket, armor", NamedTextColor.RED));
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("===== คำสั่ง Voidscape =====", NamedTextColor.DARK_PURPLE));
        sender.sendMessage(Component.text("/voidscape tp - วาร์ปเข้า/ออกจาก The Void", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/voidscape give <crystal|fruit|blade|pocket|armor> - เสกไอเทมทดสอบ", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/voidscape reload - รีโหลดคอนฟิก", NamedTextColor.YELLOW));
    }
}
