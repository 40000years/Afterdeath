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
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class VoidscapeCommand implements CommandExecutor, TabCompleter {

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
                loc.setY(141.0);
                player.teleport(loc);
                player.sendMessage(Component.text("🌌 วาร์ปเข้าสู่ The Void (Layer 1: Thunder Spires)", NamedTextColor.DARK_PURPLE));
            }
            return true;
        }

        // /voidscape spawn <phantom|vex|stalker>
        if (args[0].equalsIgnoreCase("spawn") && args.length >= 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("คำสั่งนี้ใช้ได้เฉพาะในเกม", NamedTextColor.RED));
                return true;
            }

            String mobType = args[1].toLowerCase();
            switch (mobType) {
                case "phantom" -> {
                    plugin.getMobManager().spawnGiantPhantomAt(player.getLocation().add(0, 5, 0), player);
                    player.sendMessage(Component.text("✅ เสก Giant Phantom (HP 400 / Size 6 / Scale 2.2) สำเร็จ!", NamedTextColor.GREEN));
                }
                case "vex" -> {
                    plugin.getMobManager().spawnGiantVexAt(player.getLocation().add(0, 1.5, 0), player);
                    player.sendMessage(Component.text("✅ เสก Giant Vex (HP 280 / Scale 2.8) สำเร็จ!", NamedTextColor.GREEN));
                }
                case "stalker" -> {
                    plugin.getMobManager().spawnShadowStalkerAt(player.getLocation(), player);
                    player.sendMessage(Component.text("✅ เสก Shadow Stalker (HP 200 / Scale 1.5) สำเร็จ!", NamedTextColor.GREEN));
                }
                default -> player.sendMessage(Component.text("ไม่พบมอนสเตอร์: phantom, vex, stalker", NamedTextColor.RED));
            }
            return true;
        }

        // /voidscape boss <spawn|kill>
        if (args[0].equalsIgnoreCase("boss") && args.length >= 2) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(Component.text("คำสั่งนี้ใช้ได้เฉพาะในเกม", NamedTextColor.RED));
                return true;
            }

            String action = args[1].toLowerCase();
            switch (action) {
                case "spawn" -> {
                    if (plugin.getBossManager().isBossAlive()) {
                        player.sendMessage(Component.text("❌ ราชันย์ก้นบึ้งทมิฬยังมีชีวิตอยู่แล้ว! กฎของมิติอนุญาตให้มีได้เพียง 1 ตัวเท่านั้น", NamedTextColor.RED));
                        return true;
                    }

                    World voidWorld = plugin.getVoidWorld();
                    Location spawnLoc = (player.getWorld().getName().equals(plugin.getVoidWorldName()))
                            ? player.getLocation()
                            : new Location(voidWorld, 8000.5, -51.0, 0.5);

                    var boss = plugin.getBossManager().spawnBoss(spawnLoc);
                    if (boss != null) {
                        player.sendMessage(Component.text("✅ เสก Abyssal Warden Boss (HP 5,000 / Scale 4.5x Titan / 1-Hit Kill) สำเร็จ!", NamedTextColor.GREEN));
                    } else {
                        player.sendMessage(Component.text("❌ ไม่สามารถเสกบอสได้ กรุณาตรวจสอบโลก The Void", NamedTextColor.RED));
                    }
                }
                case "kill" -> {
                    if (!plugin.getBossManager().isBossAlive()) {
                        player.sendMessage(Component.text("❌ ไม่มี Abyssal Warden Boss มีชีวิตอยู่ในขณะนี้", NamedTextColor.YELLOW));
                        return true;
                    }
                    plugin.getBossManager().killCurrentBoss();
                    player.sendMessage(Component.text("💀 สังหาร Abyssal Warden Boss เรียบร้อยแล้ว!", NamedTextColor.GREEN));
                }
                default -> player.sendMessage(Component.text("คำสั่งบอสที่ใช้ได้: /voidscape boss <spawn|kill>", NamedTextColor.RED));
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        if (args.length == 1) {
            for (String sub : List.of("tp", "spawn", "boss", "give", "reload")) {
                if (sub.startsWith(args[0].toLowerCase())) completions.add(sub);
            }
        } else if (args.length == 2) {
            if (args[0].equalsIgnoreCase("spawn")) {
                for (String mob : List.of("phantom", "vex", "stalker")) {
                    if (mob.startsWith(args[1].toLowerCase())) completions.add(mob);
                }
            } else if (args[0].equalsIgnoreCase("boss")) {
                for (String action : List.of("spawn", "kill")) {
                    if (action.startsWith(args[1].toLowerCase())) completions.add(action);
                }
            } else if (args[0].equalsIgnoreCase("give")) {
                for (String item : List.of("crystal", "fruit", "blade", "pocket", "armor")) {
                    if (item.startsWith(args[1].toLowerCase())) completions.add(item);
                }
            }
        }
        return completions;
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(Component.text("===== คำสั่ง Voidscape =====", NamedTextColor.DARK_PURPLE));
        sender.sendMessage(Component.text("/voidscape tp - วาร์ปเข้า/ออกจาก The Void", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/voidscape spawn <phantom|vex|stalker> - เสกมอนสเตอร์ยักษ์ทดสอบ", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/voidscape boss <spawn|kill> - จัดการ Abyssal Warden Boss (HP 5000)", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/voidscape give <crystal|fruit|blade|pocket|armor> - เสกไอเทมทดสอบ", NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("/voidscape reload - รีโหลดคอนฟิก", NamedTextColor.YELLOW));
    }
}
