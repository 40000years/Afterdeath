package com.example.antifreecam.masker;

import com.example.antifreecam.AntiFreecamPlugin;
import org.bukkit.Chunk;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.BlockState;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ContainerMasker {

    private final AntiFreecamPlugin plugin;
    private final Map<UUID, Set<Location>> playerMaskedBlocks = new ConcurrentHashMap<>();

    private final BlockData stoneData = Material.STONE.createBlockData();
    private final BlockData deepslateData = Material.DEEPSLATE.createBlockData();

    public ContainerMasker(AntiFreecamPlugin plugin) {
        this.plugin = plugin;
    }

    public void updatePlayer(Player player) {
        if (!plugin.getConfig().getBoolean("masking.enabled", true)) {
            restorePlayer(player);
            return;
        }

        if (player.hasPermission("antifreecam.bypass")) {
            restorePlayer(player);
            return;
        }

        UUID uuid = player.getUniqueId();
        Set<Location> maskedSet = playerMaskedBlocks.computeIfAbsent(uuid, k -> ConcurrentHashMap.newKeySet());

        double scanRadius = plugin.getConfig().getDouble("masking.scan-radius", 20.0);
        double revealDist = plugin.getConfig().getDouble("masking.reveal-distance", 6.5);
        double revealDistSq = revealDist * revealDist;
        boolean requireLos = plugin.getConfig().getBoolean("masking.require-line-of-sight", true);
        int deepslateY = plugin.getConfig().getInt("masking.deepslate-threshold-y", 0);

        Location playerLoc = player.getLocation();
        Location eyeLoc = player.getEyeLocation();

        int chunkRadius = (int) Math.ceil(scanRadius / 16.0);
        int playerChunkX = playerLoc.getBlockX() >> 4;
        int playerChunkZ = playerLoc.getBlockZ() >> 4;

        Set<Location> validLocationsInScan = new HashSet<>();

        for (int cx = playerChunkX - chunkRadius; cx <= playerChunkX + chunkRadius; cx++) {
            for (int cz = playerChunkZ - chunkRadius; cz <= playerChunkZ + chunkRadius; cz++) {
                if (!player.getWorld().isChunkLoaded(cx, cz)) continue;
                Chunk chunk = player.getWorld().getChunkAt(cx, cz);

                for (BlockState tile : chunk.getTileEntities()) {
                    Material mat = tile.getType();
                    if (!isTargetValuable(mat)) continue;

                    Location loc = tile.getLocation();
                    double distSq = loc.distanceSquared(playerLoc);
                    if (distSq > (scanRadius * scanRadius)) continue;

                    validLocationsInScan.add(loc);

                    boolean shouldMask = false;
                    if (distSq > revealDistSq) {
                        shouldMask = true;
                    } else if (requireLos) {
                        shouldMask = !hasLineOfSight(eyeLoc, loc);
                    }

                    if (shouldMask) {
                        if (maskedSet.add(loc)) {
                            // ส่งบล็อกปลอมพรางเป็นหิน (Stone หรือ Deepslate ตามระดับ Y)
                            BlockData fakeData = (loc.getY() < deepslateY) ? deepslateData : stoneData;
                            player.sendBlockChange(loc, fakeData);
                        }
                    } else {
                        if (maskedSet.remove(loc)) {
                            // แสดงบล็อกจริงกลับคืนมา
                            player.sendBlockChange(loc, loc.getBlock().getBlockData());
                        }
                    }
                }
            }
        }

        // คืนค่าบล็อกที่อยู่นอกระยะสแกนแล้ว
        maskedSet.removeIf(loc -> {
            if (!validLocationsInScan.contains(loc)) {
                if (loc.isWorldLoaded() && loc.getWorld().equals(player.getWorld())) {
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                }
                return true;
            }
            return false;
        });
    }

    public void restorePlayer(Player player) {
        Set<Location> maskedSet = playerMaskedBlocks.remove(player.getUniqueId());
        if (maskedSet != null) {
            for (Location loc : maskedSet) {
                if (loc.isWorldLoaded() && loc.getWorld().equals(player.getWorld())) {
                    player.sendBlockChange(loc, loc.getBlock().getBlockData());
                }
            }
        }
    }

    public boolean hasLineOfSight(Location eyeLoc, Location targetLoc) {
        Vector targetCenter = targetLoc.toVector().add(new Vector(0.5, 0.5, 0.5));
        Vector dir = targetCenter.clone().subtract(eyeLoc.toVector());
        double dist = dir.length();
        if (dist < 0.8) return true;

        dir.normalize();
        RayTraceResult result = eyeLoc.getWorld().rayTraceBlocks(
                eyeLoc,
                dir,
                dist - 0.25,
                FluidCollisionMode.NEVER,
                true
        );

        return result == null || result.getHitBlock() == null;
    }

    public boolean isTargetValuable(Material material) {
        if (material == null) return false;
        return switch (material) {
            case CHEST, TRAPPED_CHEST, BARREL, ENDER_CHEST,
                 SHULKER_BOX, WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX,
                 LIGHT_BLUE_SHULKER_BOX, YELLOW_SHULKER_BOX, LIME_SHULKER_BOX, PINK_SHULKER_BOX,
                 GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX, CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX,
                 BLUE_SHULKER_BOX, BROWN_SHULKER_BOX, GREEN_SHULKER_BOX, RED_SHULKER_BOX,
                 BLACK_SHULKER_BOX,
                 SPAWNER, TRIAL_SPAWNER, VAULT -> true;
            default -> false;
        };
    }
}
