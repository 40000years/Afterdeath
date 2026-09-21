package com.example.voidscape.team;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;

/**
 * เจ้าของข้อมูลทีมทั้งหมด: โหลด เซฟ สมัครสมาชิก และสถานะ PvP ในทีม
 * เก็บไฟล์ที่ plugins/Evergarden/teams.yml เขียนทุกครั้งที่ข้อมูลเปลี่ยน (แบบเดียวกับ dungeons.yml)
 *
 * เมธอดที่เป็นคำสั่งจะคืนค่าเป็นข้อความ error ภาษาไทย ถ้าคืน null แปลว่าสำเร็จ
 * ส่วนการส่งข้อความหาผู้เล่นทำใน TeamCommand
 */
public final class TeamService {

    /** คำเชิญที่ยังไม่หมดอายุ */
    private record Invite(String teamId, UUID from, long expiresAt) {}

    private final VoidscapePlugin plugin;
    private final File file;

    private final Map<String, Team> teams = new HashMap<>();      // teamId -> ทีม
    private final Map<UUID, String> membership = new HashMap<>(); // ผู้เล่น -> teamId
    private final Map<UUID, Invite> invites = new HashMap<>();    // คนที่ถูกเชิญ -> คำเชิญล่าสุด
    private final Map<UUID, Long> disbandConfirm = new HashMap<>();

    private boolean storageHealthy = true;

    public TeamService(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
        load();
    }

    // ==========================================================
    // การอ่านและเขียนไฟล์
    // ==========================================================

    private void load() {
        try {
            if (!file.exists()) return;
            YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
            ConfigurationSection root = data.getConfigurationSection("teams");
            if (root == null) return;
            for (String id : root.getKeys(false)) {
                ConfigurationSection section = root.getConfigurationSection(id);
                if (section == null) continue;
                String name = section.getString("name", id);
                String leaderRaw = section.getString("leader", "");
                UUID leader = parseUuid(leaderRaw);
                if (leader == null) continue;
                Team team = new Team(id, name, leader);
                team.setPvp(section.getBoolean("pvp", false));
                for (String raw : section.getStringList("members")) {
                    UUID member = parseUuid(raw);
                    if (member != null) team.add(member);
                }
                team.add(leader); // กันข้อมูลเสียกรณีหัวหน้าหลุดจากรายชื่อ
                teams.put(id, team);
                for (UUID member : team.members()) membership.put(member, id);
            }
            plugin.getLogger().info("Loaded " + teams.size() + " teams");
        } catch (Exception error) {
            storageHealthy = false;
            plugin.getLogger().warning("Cannot read teams.yml: " + error.getMessage());
        }
    }

    private UUID parseUuid(String raw) {
        try {
            return raw == null || raw.isBlank() ? null : UUID.fromString(raw);
        } catch (IllegalArgumentException invalid) {
            return null;
        }
    }

    public void save() {
        try {
            YamlConfiguration data = new YamlConfiguration();
            for (Team team : teams.values()) {
                String base = "teams." + team.id() + ".";
                data.set(base + "name", team.name());
                data.set(base + "leader", team.leader().toString());
                data.set(base + "pvp", team.pvp());
                List<String> members = new ArrayList<>();
                for (UUID member : team.members()) members.add(member.toString());
                data.set(base + "members", members);
            }
            File parent = file.getParentFile();
            if (parent != null) parent.mkdirs();
            data.save(file);
            storageHealthy = true;
        } catch (Exception error) {
            storageHealthy = false;
            plugin.getLogger().warning("Cannot save teams.yml: " + error.getMessage());
        }
    }

    public boolean storageHealthy() { return storageHealthy; }

    public void close() { save(); }

    // ==========================================================
    // การค้นหา
    // ==========================================================

    public Team teamOf(UUID uuid) {
        String id = membership.get(uuid);
        return id == null ? null : teams.get(id);
    }

    public Team teamOf(Player player) {
        return player == null ? null : teamOf(player.getUniqueId());
    }

    public Team teamByName(String name) {
        for (Team team : teams.values()) {
            if (team.name().equalsIgnoreCase(name)) return team;
        }
        return null;
    }

    public Collection<Team> teams() { return teams.values(); }

    /**
     * หัวใจของฟีเจอร์นี้: ดาเมจจาก attacker ไปยัง victim ต้องถูกยกเลิกหรือไม่
     * true เมื่อทั้งคู่เป็นคนละคน อยู่ทีมเดียวกัน และทีมนั้นปิด PvP อยู่
     */
    public boolean blocked(Player attacker, Player victim) {
        if (attacker == null || victim == null) return false;
        if (attacker.getUniqueId().equals(victim.getUniqueId())) return false;
        Team team = teamOf(attacker.getUniqueId());
        if (team == null) return false;
        if (!team.has(victim.getUniqueId())) return false;
        return !team.pvp();
    }

    // ==========================================================
    // คำสั่งจัดการทีม (คืน null = สำเร็จ, คืนข้อความ = ผิดพลาด)
    // ==========================================================

    public int maxMembers() {
        return plugin.integer("team.max-members", 8, 2, 50);
    }

    public String create(Player player, String name) {
        if (teamOf(player) != null) return "คุณอยู่ในทีมอยู่แล้ว ออกจากทีมเดิมก่อนด้วย /team leave";
        String error = validateName(name);
        if (error != null) return error;
        if (teamByName(name) != null) return "มีทีมชื่อนี้อยู่แล้ว";
        String id = UUID.randomUUID().toString().substring(0, 8);
        Team team = new Team(id, name, player.getUniqueId());
        teams.put(id, team);
        membership.put(player.getUniqueId(), id);
        save();
        return null;
    }

    private String validateName(String name) {
        if (name == null || name.isBlank()) return "ต้องใส่ชื่อทีม";
        if (name.length() < 3 || name.length() > 16) return "ชื่อทีมต้องยาว 3 ถึง 16 ตัวอักษร";
        for (char c : name.toCharArray()) {
            boolean ok = Character.isLetterOrDigit(c) || c == '_' || c == '-';
            if (!ok) return "ชื่อทีมใช้ได้เฉพาะตัวอักษร ตัวเลข _ และ -";
        }
        return null;
    }

    public String invite(Player leader, Player target) {
        Team team = teamOf(leader);
        if (team == null) return "คุณยังไม่มีทีม";
        if (!team.isLeader(leader.getUniqueId())) return "เฉพาะหัวหน้าทีมเท่านั้นที่ชวนคนได้";
        if (target.getUniqueId().equals(leader.getUniqueId())) return "ชวนตัวเองไม่ได้";
        if (team.has(target.getUniqueId())) return target.getName() + " อยู่ในทีมอยู่แล้ว";
        if (teamOf(target) != null) return target.getName() + " อยู่ในทีมอื่นอยู่";
        if (team.size() >= maxMembers()) return "ทีมเต็มแล้ว (สูงสุด " + maxMembers() + " คน)";
        long seconds = plugin.integer("team.invite-seconds", 60, 10, 600);
        invites.put(target.getUniqueId(), new Invite(team.id(), leader.getUniqueId(), System.currentTimeMillis() + seconds * 1000L));
        return null;
    }

    /** ทีมที่กำลังเชิญผู้เล่นคนนี้อยู่ หรือ null ถ้าไม่มีหรือหมดอายุแล้ว */
    public Team pendingInvite(UUID uuid) {
        Invite invite = invites.get(uuid);
        if (invite == null) return null;
        if (System.currentTimeMillis() > invite.expiresAt()) {
            invites.remove(uuid);
            return null;
        }
        Team team = teams.get(invite.teamId());
        if (team == null) {
            invites.remove(uuid);
            return null;
        }
        return team;
    }

    public String accept(Player player) {
        Team team = pendingInvite(player.getUniqueId());
        if (team == null) return "ไม่มีคำเชิญที่ยังใช้ได้";
        if (teamOf(player) != null) return "คุณอยู่ในทีมอยู่แล้ว";
        if (team.size() >= maxMembers()) return "ทีมเต็มแล้ว";
        invites.remove(player.getUniqueId());
        team.add(player.getUniqueId());
        membership.put(player.getUniqueId(), team.id());
        save();
        return null;
    }

    public String deny(Player player) {
        if (pendingInvite(player.getUniqueId()) == null) return "ไม่มีคำเชิญที่ยังใช้ได้";
        invites.remove(player.getUniqueId());
        return null;
    }

    public String leave(Player player) {
        Team team = teamOf(player);
        if (team == null) return "คุณยังไม่มีทีม";
        removeMember(team, player.getUniqueId());
        return null;
    }

    public String kick(Player leader, UUID target, String targetName) {
        Team team = teamOf(leader);
        if (team == null) return "คุณยังไม่มีทีม";
        if (!team.isLeader(leader.getUniqueId())) return "เฉพาะหัวหน้าทีมเท่านั้นที่เตะคนได้";
        if (target.equals(leader.getUniqueId())) return "เตะตัวเองไม่ได้ ใช้ /team leave แทน";
        if (!team.has(target)) return targetName + " ไม่ได้อยู่ในทีมนี้";
        removeMember(team, target);
        return null;
    }

    /** ถอดสมาชิกออก พร้อมโอนหัวหน้าหรือยุบทีมถ้าจำเป็น */
    private void removeMember(Team team, UUID member) {
        team.remove(member);
        membership.remove(member);
        if (team.members().isEmpty()) {
            teams.remove(team.id());
        } else if (team.isLeader(member)) {
            UUID next = team.oldestMemberExcept(member);
            if (next != null) {
                team.setLeader(next);
                announce(team, "หัวหน้าทีมคนใหม่คือ " + nameOf(next));
            }
        }
        save();
    }

    public String promote(Player leader, UUID target, String targetName) {
        Team team = teamOf(leader);
        if (team == null) return "คุณยังไม่มีทีม";
        if (!team.isLeader(leader.getUniqueId())) return "เฉพาะหัวหน้าทีมเท่านั้นที่โอนตำแหน่งได้";
        if (!team.has(target)) return targetName + " ไม่ได้อยู่ในทีมนี้";
        if (team.isLeader(target)) return targetName + " เป็นหัวหน้าอยู่แล้ว";
        team.setLeader(target);
        save();
        announce(team, nameOf(target) + " เป็นหัวหน้าทีมคนใหม่");
        return null;
    }

    /** ขอยุบทีม ต้องเรียกสองครั้งภายใน 15 วินาที ครั้งแรกคืนข้อความให้ยืนยัน */
    public String disband(Player leader) {
        Team team = teamOf(leader);
        if (team == null) return "คุณยังไม่มีทีม";
        if (!team.isLeader(leader.getUniqueId())) return "เฉพาะหัวหน้าทีมเท่านั้นที่ยุบทีมได้";
        Long confirm = disbandConfirm.get(leader.getUniqueId());
        long now = System.currentTimeMillis();
        if (confirm == null || now > confirm) {
            disbandConfirm.put(leader.getUniqueId(), now + 15000L);
            return "พิมพ์ /team disband อีกครั้งภายใน 15 วินาทีเพื่อยืนยันการยุบทีม " + team.name();
        }
        disbandConfirm.remove(leader.getUniqueId());
        forceDisband(team);
        return null;
    }

    public void forceDisband(Team team) {
        announce(team, "ทีม " + team.name() + " ถูกยุบแล้ว");
        for (UUID member : new ArrayList<>(team.members())) membership.remove(member);
        teams.remove(team.id());
        save();
    }

    // ==========================================================
    // เปิดปิด PvP ในทีม
    // ==========================================================

    /**
     * ปิด: มีผลทันที และยกเลิกคำสั่งเปิดที่ค้างอยู่
     * เปิด: ประกาศในทีมว่าใครสั่ง แล้วหน่วงตาม config ก่อนมีผลจริง กันการเปิดแล้วตีทันที
     */
    public String setPvp(Player player, boolean enable) {
        Team team = teamOf(player);
        if (team == null) return "คุณยังไม่มีทีม";
        if (!enable) {
            boolean hadPending = team.pvpEnableAt() > 0;
            team.setPvpEnableAt(0);
            if (!team.pvp() && !hadPending) return "PvP ในทีมปิดอยู่แล้ว";
            team.setPvp(false);
            save();
            announce(team, player.getName() + " ปิด PvP ในทีมแล้ว ตีกันเองไม่ได้");
            return null;
        }
        if (team.pvp()) return "PvP ในทีมเปิดอยู่แล้ว";
        if (team.pvpEnableAt() > System.currentTimeMillis()) return "มีคำสั่งเปิด PvP ค้างอยู่แล้ว";
        int delay = plugin.integer("team.pvp-enable-delay-seconds", 10, 0, 120);
        if (delay <= 0) {
            team.setPvp(true);
            team.setPvpEnableAt(0);
            save();
            announce(team, player.getName() + " เปิด PvP ในทีมแล้ว ตีกันเองได้");
            return null;
        }
        long at = System.currentTimeMillis() + delay * 1000L;
        team.setPvpEnableAt(at);
        announce(team, player.getName() + " สั่งเปิด PvP ในทีม จะมีผลในอีก " + delay + " วินาที (พิมพ์ /team pvp off เพื่อยกเลิก)");
        String teamId = team.id();
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> applyPendingPvp(teamId, at), delay * 20L);
        return null;
    }

    private void applyPendingPvp(String teamId, long expectedAt) {
        Team team = teams.get(teamId);
        if (team == null) return;
        if (team.pvpEnableAt() != expectedAt) return; // ถูกยกเลิกหรือถูกสั่งใหม่ไปแล้ว
        team.setPvpEnableAt(0);
        team.setPvp(true);
        save();
        announce(team, "PvP ในทีมเปิดแล้ว ตีกันเองได้");
    }

    // ==========================================================
    // ตัวช่วยเรื่องข้อความและชื่อ
    // ==========================================================

    public void announce(Team team, String text) {
        for (UUID member : team.members()) {
            Player online = Bukkit.getPlayer(member);
            if (online != null) online.sendMessage(Component.text("✦ [ทีม] " + text, NamedTextColor.AQUA));
        }
    }

    public String nameOf(UUID uuid) {
        Player online = Bukkit.getPlayer(uuid);
        if (online != null) return online.getName();
        OfflinePlayer offline = Bukkit.getOfflinePlayer(uuid);
        String name = offline.getName();
        return name != null ? name : uuid.toString().substring(0, 8);
    }
}
