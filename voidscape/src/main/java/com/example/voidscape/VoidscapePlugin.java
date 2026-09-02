package com.example.voidscape;

import com.example.voidscape.command.VoidscapeCommand;
import com.example.voidscape.generator.VoidChunkGenerator;
import com.example.voidscape.item.VoidItemManager;
import com.example.voidscape.listener.VoidEjectionListener;
import com.example.voidscape.listener.VoidItemListener;
import com.example.voidscape.listener.VoidPortalListener;
import com.example.voidscape.task.VoidStatusTask;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public class VoidscapePlugin extends JavaPlugin {

    private String voidWorldName;
    private World voidWorld;
    private VoidItemManager itemManager;
    private VoidStatusTask statusTask;
    private NamespacedKey keyShadowStalker;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        voidWorldName = getConfig().getString("dimension.world-name", "the_void");

        keyShadowStalker = new NamespacedKey(this, "is_shadow_stalker");
        itemManager = new VoidItemManager(this);

        // โหลดหรือสร้างโลกมิติ The Void
        loadVoidWorld();

        // ลงทะเบียน Event Listeners
        getServer().getPluginManager().registerEvents(new VoidPortalListener(this), this);
        getServer().getPluginManager().registerEvents(new VoidEjectionListener(this), this);
        getServer().getPluginManager().registerEvents(new VoidItemListener(this), this);
        getServer().getPluginManager().registerEvents(new com.example.voidscape.listener.VoidLockdownListener(this), this);

        // เริ่มต้น Background Task ตรวจจับ Voidic Infusion และ Paranoia (ทุกๆ 10 วินาที)
        statusTask = new VoidStatusTask(this);
        statusTask.runTaskTimer(this, 200L, 200L);

        // ลงทะเบียนคำสั่ง
        PluginCommand cmd = getCommand("voidscape");
        if (cmd != null) {
            cmd.setExecutor(new VoidscapeCommand(this));
        }

        getLogger().info("Voidscape Lite เปิดใช้งานเรียบร้อยแล้ว! 🌌");
    }

    @Override
    public void onDisable() {
        if (statusTask != null) {
            statusTask.cancel();
        }
        getLogger().info("Voidscape Lite ปิดการทำงานเรียบร้อยแล้ว");
    }

    private void loadVoidWorld() {
        getLogger().info("กำลังเตรียมโหลดโลกมิติ The Void: " + voidWorldName + "...");
        WorldCreator creator = new WorldCreator(voidWorldName);
        // ใช้ Environment.NORMAL เพื่อตัดระบบ Ender Dragon Battle ออก 100% และได้ความมืดสนิท
        creator.environment(World.Environment.NORMAL);
        creator.generator(new VoidChunkGenerator());
        voidWorld = creator.createWorld();

        if (voidWorld != null) {
            // ตั้งค่าให้เกาะกลางเป็นจุดเกิดที่ปลอดภัย (Tier 1 Zenith Altar ที่ Y=261)
            Location spawn = new Location(voidWorld, 0.5, 261.0, 0.5, 0f, 0f);
            voidWorld.setSpawnLocation(spawn);

            // ปิดวงจรเวลากลางวัน ปิดมอนสเตอร์ปกติ และตั้งเวลาเที่ยงคืนตลอดกาล
            voidWorld.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
            voidWorld.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
            voidWorld.setGameRule(GameRule.DO_MOB_SPAWNING, false);
            voidWorld.setTime(18000L); // เที่ยงคืนสนิท
            voidWorld.setStorm(true);  // เมฆดำทะมึน บดบังแสงดาวและดวงจันทร์ ได้ความมืดสนิทจริง

            getLogger().info("โหลดโลก The Void สำเร็จเรียบร้อย! 🌌 (โฟลเดอร์อยู่ที่: " + voidWorld.getWorldFolder().getAbsolutePath() + ")");
        } else {
            getLogger().severe("ไม่สามารถสร้างโลก The Void ได้!");
        }
    }

    /**
     * กำจัด Ender Dragon และซ่อนหลอดเลือดบอส Ender Dragon ไม่ให้โผล่มาในมิติ The Void
     */
    public void suppressEnderDragon(World world) {
        if (world == null) return;
        try {
            org.bukkit.boss.DragonBattle battle = world.getEnderDragonBattle();
            if (battle != null) {
                if (battle.getBossBar() != null) {
                    battle.getBossBar().setVisible(false);
                    battle.getBossBar().removeAll();
                }
                if (battle.getEnderDragon() != null) {
                    battle.getEnderDragon().remove();
                }
            }
            for (org.bukkit.entity.EnderDragon dragon : world.getEntitiesByClass(org.bukkit.entity.EnderDragon.class)) {
                dragon.remove();
            }
        } catch (Exception ignored) {}
    }

    public String getVoidWorldName() { return voidWorldName; }
    public World getVoidWorld() { return voidWorld; }
    public VoidItemManager getItemManager() { return itemManager; }
    public NamespacedKey getKeyShadowStalker() { return keyShadowStalker; }
}
