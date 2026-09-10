package com.example.voidscape.world;

import java.util.*;

/** Pure, seed-stable placement for the 3 Voidscape elemental shrines. */
public final class DungeonLayout {
    public enum Kind {
        SANCTUM_DARK("วิหารความมืด"),
        SANCTUM_ASTRAL("วิหารดวงดาว"),
        SANCTUM_TIME("วิหารกาลเวลา");

        public final String displayName;
        Kind(String name) { this.displayName = name; }
    }

    public record Site(Kind kind, int x, int z, long variant) {
        public String id() { return kind.name().toLowerCase(Locale.ROOT) + "_" + x + "_" + z; }
        public int radius() { return 28; }
        public boolean contains(double px, double pz, int margin) {
            return Math.abs(px - x) <= radius() + margin && Math.abs(pz - z) <= radius() + margin;
        }
    }

    private final long seed;
    private final int spacing;
    private final double chance;

    public DungeonLayout(long seed, int spacingChunks, double chance) {
        this.seed = seed;
        this.spacing = Math.max(24, Math.min(256, spacingChunks));
        this.chance = clamp(chance);
    }

    private static double clamp(double d) { return Double.isFinite(d) ? Math.max(0, Math.min(1, d)) : 0; }
    public static long mix(long n) {
        n = (n ^ (n >>> 30)) * 0xbf58476d1ce4e5b9L;
        n = (n ^ (n >>> 27)) * 0x94d049bb133111ebL;
        return n ^ (n >>> 31);
    }

    private Site candidate(int gx, int gz) {
        long cellSeed = mix(seed ^ ((long) gx * 341873128712L) ^ ((long) gz * 132897987541L));
        Random r = new Random(cellSeed);
        if (r.nextDouble() >= chance) return null;
        int margin = Math.max(4, spacing / 5);
        int x = (gx * spacing + margin + r.nextInt(Math.max(1, spacing - 2 * margin))) * 16 + 8;
        int z = (gz * spacing + margin + r.nextInt(Math.max(1, spacing - 2 * margin))) * 16 + 8;
        if (Math.hypot(x, z) < 180) return null; // Keep spawn island area clear
        Kind kind = Kind.values()[Math.floorMod((int) (cellSeed >>> 16), Kind.values().length)];
        return new Site(kind, x, z, cellSeed);
    }

    public List<Site> nearby(int blockX, int blockZ) {
        List<Site> sites = new ArrayList<>(9);
        int blockSize = spacing * 16;
        int gx = Math.floorDiv(blockX, blockSize), gz = Math.floorDiv(blockZ, blockSize);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                Site s = candidate(gx + dx, gz + dz);
                if (s != null) sites.add(s);
            }
        }
        return sites;
    }

    public Site at(int x, int z, int margin) {
        for (Site s : nearby(x, z)) {
            if (s.contains(x, z, margin)) return s;
        }
        return null;
    }

    public Site locate(int x, int z, Kind targetKind, int cells) {
        Site nearest = null;
        double best = Double.MAX_VALUE;
        int blockSize = spacing * 16;
        int gx = Math.floorDiv(x, blockSize), gz = Math.floorDiv(z, blockSize);
        for (int dx = -cells; dx <= cells; dx++) {
            for (int dz = -cells; dz <= cells; dz++) {
                Site s = candidate(gx + dx, gz + dz);
                if (s == null) continue;
                if (targetKind != null && s.kind() != targetKind) continue;
                double d = Math.hypot((double) s.x - x, (double) s.z - z);
                if (d < best) {
                    best = d;
                    nearest = s;
                }
            }
        }
        return nearest;
    }
}
