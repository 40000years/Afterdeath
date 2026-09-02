package com.example.voidscape.listener;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

import java.util.Set;

/**
 * VoidLockdownListener: ระบบปิดผนึกมิติ The Void 100%
 * - แบนคำสั่งวาร์ปหนีทุกชนิด (/spawn, /home, /warp, /tp, /tpa, /back, etc.)
 * - ปิดกั้นการเทเลพอร์ตออกนอกมิติทุกชนิด ยกเว้นกิน Null Fruit หรือตายเท่านั้น
 */
public class VoidLockdownListener implements Listener {

    private final VoidscapePlugin plugin;
    private static final Set<String> BLOCKED_COMMANDS = Set.of(
            "spawn", "home", "warp", "tp", "tpa", "tpaccept", "back",
            "rtp", "wild", "hub", "lobby", "is", "island", "suicide"
    );

    public VoidLockdownListener(VoidscapePlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!player.getWorld().getName().equals(plugin.getVoidWorldName())) return;

        // ให้สิทธิ์ OP ใช้คำสั่งแอดมินหรือคำสั่ง /voidscape ได้
        if (player.isOp()) return;

        String raw = event.getMessage().toLowerCase().trim();
        if (raw.startsWith("/")) {
            raw = raw.substring(1);
        }
        String[] parts = raw.split(" ");
        String cmd = parts[0];

        if (BLOCKED_COMMANDS.contains(cmd)) {
            event.setCancelled(true);
            player.sendMessage(Component.text("✦ มิติ The Void ปิดผนึกคุณไว้... เวทมนตร์หลบหนีถูกดูดกลืน!", NamedTextColor.RED));
            player.sendMessage(Component.text("💡 ทางรอดเดียว: กินผลไม้ Null Fruit หรือยอมจำนนต่อความตายเท่านั้น!", NamedTextColor.GRAY));
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() == null || to == null || to.getWorld() == null) return;

        // ถ้ากำลังพยายามวาร์ปออกจาก The Void ไปยังโลกอื่น
        if (from.getWorld().getName().equals(plugin.getVoidWorldName())
                && !to.getWorld().getName().equals(plugin.getVoidWorldName())) {

            Player player = event.getPlayer();

            // ตรวจสอบว่าได้รับอนุญาตให้ออกจาก The Void ไหม (เช่น กิน Null Fruit หรือโดน Eject)
            if (player.hasMetadata("void_authorized_escape")) {
                player.removeMetadata("void_authorized_escape", plugin);
                return; // อนุญาตให้ผ่านได้
            }

            // ถ้าเป็น OP ไม่บล็อก
            if (player.isOp()) return;

            // บล็อกการวาร์ปทุกชนิด (End Pearl, คำสั่งปลั๊กอินอื่น ฯลฯ)
            event.setCancelled(true);
            player.sendMessage(Component.text("✦ พลังมิติมืดกักขังคุณไว้... คุณไม่สามารถวาร์ปออกจากที่นี่ได้!", NamedTextColor.RED));
        }
    }
}
