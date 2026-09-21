package com.example.voidscape.team;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;

/** คำสั่ง /team ทั้งหมด */
public final class TeamCommand implements CommandExecutor, TabCompleter {

    private final VoidscapePlugin plugin;
    private final TeamService teams;

    public TeamCommand(VoidscapePlugin plugin, TeamService teams) {
        this.plugin = plugin;
        this.teams = teams;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("คำสั่งนี้ใช้ได้เฉพาะในเกม");
            return true;
        }
        if (!player.hasPermission("evergarden.team")) {
            plugin.message(player, "คุณไม่มีสิทธิ์ใช้ระบบทีม");
            return true;
        }
        if (args.length == 0) {
            help(player);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> create(player, args);
            case "invite" -> invite(player, args);
            case "accept" -> accept(player);
            case "deny" -> deny(player);
            case "leave" -> leave(player);
            case "kick" -> kick(player, args);
            case "promote" -> promote(player, args);
            case "disband" -> disband(player);
            case "info" -> info(player, args);
            case "pvp" -> pvp(player, args);
            case "admin" -> admin(player, args);
            default -> help(player);
        }
        return true;
    }

    // ==========================================================
    // คำสั่งย่อย
    // ==========================================================

    private void create(Player player, String[] args) {
        if (args.length < 2) {
            plugin.message(player, "ใช้: /team create <ชื่อทีม>");
            return;
        }
        String error = teams.create(player, args[1]);
        if (error != null) {
            plugin.message(player, error);
            return;
        }
        plugin.message(player, "สร้างทีม " + args[1] + " แล้ว PvP ในทีมปิดอยู่ ชวนเพื่อนด้วย /team invite <ชื่อผู้เล่น>");
    }

    private void invite(Player player, String[] args) {
        if (args.length < 2) {
            plugin.message(player, "ใช้: /team invite <ชื่อผู้เล่น>");
            return;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            plugin.message(player, "ไม่พบผู้เล่นชื่อนี้ที่ออนไลน์อยู่");
            return;
        }
        String error = teams.invite(player, target);
        if (error != null) {
            plugin.message(player, error);
            return;
        }
        Team team = teams.teamOf(player);
        plugin.message(player, "ส่งคำเชิญถึง " + target.getName() + " แล้ว");
        target.sendMessage(Component.text("✦ " + player.getName() + " ชวนคุณเข้าทีม "
                + (team != null ? team.name() : "") + " พิมพ์ /team accept เพื่อเข้าร่วม หรือ /team deny เพื่อปฏิเสธ",
                NamedTextColor.AQUA));
    }

    private void accept(Player player) {
        Team team = teams.pendingInvite(player.getUniqueId());
        String error = teams.accept(player);
        if (error != null) {
            plugin.message(player, error);
            return;
        }
        Team joined = team != null ? team : teams.teamOf(player);
        if (joined != null) {
            teams.announce(joined, player.getName() + " เข้าร่วมทีมแล้ว");
            plugin.message(player, "เข้าทีม " + joined.name() + " แล้ว PvP ในทีมตอนนี้"
                    + (joined.pvp() ? "เปิดอยู่" : "ปิดอยู่"));
        }
    }

    private void deny(Player player) {
        String error = teams.deny(player);
        plugin.message(player, error != null ? error : "ปฏิเสธคำเชิญแล้ว");
    }

    private void leave(Player player) {
        Team team = teams.teamOf(player);
        String error = teams.leave(player);
        if (error != null) {
            plugin.message(player, error);
            return;
        }
        plugin.message(player, "ออกจากทีมแล้ว");
        if (team != null) teams.announce(team, player.getName() + " ออกจากทีม");
    }

    private void kick(Player player, String[] args) {
        if (args.length < 2) {
            plugin.message(player, "ใช้: /team kick <ชื่อผู้เล่น>");
            return;
        }
        Team team = teams.teamOf(player);
        UUID target = findMember(team, args[1]);
        if (target == null) {
            plugin.message(player, "ไม่พบผู้เล่นชื่อนี้ในทีม");
            return;
        }
        String error = teams.kick(player, target, args[1]);
        if (error != null) {
            plugin.message(player, error);
            return;
        }
        plugin.message(player, "เตะ " + args[1] + " ออกจากทีมแล้ว");
        Player online = Bukkit.getPlayer(target);
        if (online != null) plugin.message(online, "คุณถูกเตะออกจากทีมแล้ว");
        if (team != null) teams.announce(team, args[1] + " ถูกเตะออกจากทีม");
    }

    private void promote(Player player, String[] args) {
        if (args.length < 2) {
            plugin.message(player, "ใช้: /team promote <ชื่อผู้เล่น>");
            return;
        }
        Team team = teams.teamOf(player);
        UUID target = findMember(team, args[1]);
        if (target == null) {
            plugin.message(player, "ไม่พบผู้เล่นชื่อนี้ในทีม");
            return;
        }
        String error = teams.promote(player, target, args[1]);
        if (error != null) plugin.message(player, error);
    }

    private void disband(Player player) {
        Team team = teams.teamOf(player);
        String error = teams.disband(player);
        if (error != null) {
            plugin.message(player, error);
            return;
        }
        plugin.message(player, "ยุบทีม" + (team != null ? " " + team.name() : "") + " แล้ว");
    }

    private void info(Player player, String[] args) {
        Team team = args.length >= 2 ? teams.teamByName(args[1]) : teams.teamOf(player);
        if (team == null) {
            plugin.message(player, args.length >= 2 ? "ไม่พบทีมชื่อนี้" : "คุณยังไม่มีทีม พิมพ์ /team create <ชื่อ> เพื่อสร้าง");
            return;
        }
        plugin.message(player, "ทีม " + team.name() + " (" + team.size() + "/" + teams.maxMembers() + " คน)");
        plugin.message(player, "หัวหน้า: " + teams.nameOf(team.leader()));
        plugin.message(player, "PvP ในทีม: " + (team.pvp() ? "เปิด (ตีกันเองได้)" : "ปิด (ตีกันเองไม่ได้)"));
        StringBuilder line = new StringBuilder();
        for (UUID member : team.members()) {
            if (!line.isEmpty()) line.append(", ");
            boolean online = Bukkit.getPlayer(member) != null;
            line.append(teams.nameOf(member)).append(online ? " (ออนไลน์)" : "");
        }
        plugin.message(player, "สมาชิก: " + line);
    }

    private void pvp(Player player, String[] args) {
        Team team = teams.teamOf(player);
        if (team == null) {
            plugin.message(player, "คุณยังไม่มีทีม");
            return;
        }
        if (args.length < 2) {
            plugin.message(player, "PvP ในทีมตอนนี้" + (team.pvp() ? "เปิดอยู่ ตีกันเองได้" : "ปิดอยู่ ตีกันเองไม่ได้")
                    + " เปลี่ยนด้วย /team pvp on หรือ /team pvp off");
            return;
        }
        String value = args[1].toLowerCase(Locale.ROOT);
        boolean enable;
        if (value.equals("on") || value.equals("true") || value.equals("เปิด")) enable = true;
        else if (value.equals("off") || value.equals("false") || value.equals("ปิด")) enable = false;
        else {
            plugin.message(player, "ใช้: /team pvp <on|off>");
            return;
        }
        String error = teams.setPvp(player, enable);
        if (error != null) plugin.message(player, error);
    }

    private void admin(Player player, String[] args) {
        if (!player.hasPermission("evergarden.team.admin")) {
            plugin.message(player, "คุณไม่มีสิทธิ์ใช้คำสั่งนี้");
            return;
        }
        if (args.length < 3 || !args[1].equalsIgnoreCase("disband")) {
            plugin.message(player, "ใช้: /team admin disband <ชื่อทีม>");
            return;
        }
        Team team = teams.teamByName(args[2]);
        if (team == null) {
            plugin.message(player, "ไม่พบทีมชื่อนี้");
            return;
        }
        teams.forceDisband(team);
        plugin.message(player, "ยุบทีม " + args[2] + " แล้ว");
    }

    private void help(Player player) {
        plugin.message(player, "คำสั่งทีม");
        player.sendMessage(Component.text(
                "/team create <ชื่อ> - สร้างทีม\n"
                + "/team invite <ผู้เล่น> - ชวน (หัวหน้าเท่านั้น)\n"
                + "/team accept | /team deny - ตอบรับหรือปฏิเสธคำเชิญ\n"
                + "/team leave - ออกจากทีม\n"
                + "/team kick <ผู้เล่น> - เตะ (หัวหน้าเท่านั้น)\n"
                + "/team promote <ผู้เล่น> - โอนหัวหน้า\n"
                + "/team disband - ยุบทีม (หัวหน้าเท่านั้น)\n"
                + "/team info [ชื่อทีม] - ดูข้อมูลทีม\n"
                + "/team pvp <on|off> - เปิดปิดการตีกันเองในทีม",
                NamedTextColor.GRAY));
    }

    /** หาสมาชิกในทีมจากชื่อ รองรับคนที่ออฟไลน์อยู่ */
    private UUID findMember(Team team, String name) {
        if (team == null) return null;
        for (UUID member : team.members()) {
            if (teams.nameOf(member).equalsIgnoreCase(name)) return member;
        }
        OfflinePlayer offline = Bukkit.getOfflinePlayerIfCached(name);
        return offline != null && team.has(offline.getUniqueId()) ? offline.getUniqueId() : null;
    }

    // ==========================================================
    // Tab complete
    // ==========================================================

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            options.addAll(List.of("create", "invite", "accept", "deny", "leave", "kick", "promote", "disband", "info", "pvp"));
            if (player.hasPermission("evergarden.team.admin")) options.add("admin");
        } else if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            Team team = teams.teamOf(player);
            switch (sub) {
                case "pvp" -> options.addAll(List.of("on", "off"));
                case "invite" -> Bukkit.getOnlinePlayers().forEach(online -> options.add(online.getName()));
                case "kick", "promote" -> {
                    if (team != null) for (UUID member : team.members()) options.add(teams.nameOf(member));
                }
                case "info" -> teams.teams().forEach(existing -> options.add(existing.name()));
                case "admin" -> options.add("disband");
                default -> { }
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("admin")) {
            teams.teams().forEach(existing -> options.add(existing.name()));
        }
        String current = args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> filtered = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(current)) filtered.add(option);
        }
        return filtered;
    }
}
