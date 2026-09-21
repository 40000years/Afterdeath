package com.example.voidscape.team;

import java.util.*;

/**
 * ข้อมูลของทีมหนึ่งทีม
 * เก็บเฉพาะข้อมูลล้วนๆ ไม่มี logic การเซฟหรือการตรวจสิทธิ์ (อยู่ใน TeamService)
 */
public final class Team {

    private final String id;                 // id ภายใน ไม่เปลี่ยนตลอดอายุทีม
    private String name;                     // ชื่อที่ผู้เล่นตั้ง ใช้แสดงผล
    private UUID leader;
    private final LinkedHashSet<UUID> members = new LinkedHashSet<>();
    private boolean pvp;                     // true = ตีกันเองได้

    /** เวลา (epoch millis) ที่คำสั่งเปิด PvP จะมีผล 0 = ไม่มีคำสั่งค้างอยู่ ค่านี้ไม่ถูกบันทึกลงไฟล์ */
    private long pvpEnableAt;

    public Team(String id, String name, UUID leader) {
        this.id = id;
        this.name = name;
        this.leader = leader;
        this.members.add(leader);
        this.pvp = false;               // ค่าเริ่มต้น: ตีกันเองไม่ได้
    }

    public String id() { return id; }

    public String name() { return name; }

    public void setName(String name) { this.name = name; }

    public UUID leader() { return leader; }

    public void setLeader(UUID leader) { this.leader = leader; }

    public boolean isLeader(UUID uuid) { return leader.equals(uuid); }

    /** สมาชิกทั้งหมดรวมหัวหน้า เรียงตามลำดับที่เข้าทีม */
    public Set<UUID> members() { return members; }

    public boolean has(UUID uuid) { return members.contains(uuid); }

    public int size() { return members.size(); }

    public void add(UUID uuid) { members.add(uuid); }

    public void remove(UUID uuid) { members.remove(uuid); }

    public boolean pvp() { return pvp; }

    public void setPvp(boolean pvp) { this.pvp = pvp; }

    public long pvpEnableAt() { return pvpEnableAt; }

    public void setPvpEnableAt(long pvpEnableAt) { this.pvpEnableAt = pvpEnableAt; }

    /** คนที่เข้าทีมนานที่สุดซึ่งไม่ใช่หัวหน้าปัจจุบัน ใช้ตอนหัวหน้าออกจากทีม */
    public UUID oldestMemberExcept(UUID exclude) {
        for (UUID uuid : members) {
            if (!uuid.equals(exclude)) return uuid;
        }
        return null;
    }
}
