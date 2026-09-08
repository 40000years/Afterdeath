package com.example.voidscape.world;

import java.util.*;

/** Pure, seed-stable placement. No world loads, Bukkit calls, or mutable shared random. */
public final class DungeonLayout {
    public enum Kind { DREADSHIP, SANCTUM }
    public record Site(Kind kind, int x, int z, long variant) {
        public String id() { return kind.name().toLowerCase(Locale.ROOT) + "_" + x + "_" + z; }
        public int radius() { return kind == Kind.DREADSHIP ? 52 : 27; }
        public boolean contains(double px, double pz, int margin) {
            return Math.abs(px-x) <= radius()+margin && Math.abs(pz-z) <= radius()+margin;
        }
    }
    private final long seed;
    private final int majorSpacing, minorSpacing;
    private final double majorChance, minorChance;
    public DungeonLayout(long seed, int majorSpacing, int minorSpacing, double majorChance, double minorChance) {
        this.seed=seed;
        this.majorSpacing=Math.max(96,Math.min(1024,majorSpacing));
        this.minorSpacing=Math.max(32,Math.min(256,minorSpacing));
        this.majorChance=clamp(majorChance); this.minorChance=clamp(minorChance);
    }
    private static double clamp(double d) { return Double.isFinite(d)?Math.max(0,Math.min(1,d)):0; }
    public static long mix(long n) { n=(n^(n>>>30))*0xbf58476d1ce4e5b9L; n=(n^(n>>>27))*0x94d049bb133111ebL; return n^(n>>>31); }
    private Site candidate(Kind kind, int gx, int gz) {
        int spacing=kind==Kind.DREADSHIP?majorSpacing:minorSpacing;
        Random r=new Random(mix(seed ^ ((long)gx*341873128712L) ^ ((long)gz*132897987541L) ^ (kind.ordinal()*91278319L)));
        if(r.nextDouble() >= (kind==Kind.DREADSHIP?majorChance:minorChance)) return null;
        int margin=spacing/4;
        int x=(gx*spacing+margin+r.nextInt(spacing-2*margin))*16+8;
        int z=(gz*spacing+margin+r.nextInt(spacing-2*margin))*16+8;
        if(Math.hypot(x,z)<320) return null;
        return new Site(kind,x,z,r.nextLong());
    }
    public List<Site> nearby(int blockX,int blockZ) {
        List<Site> sites=new ArrayList<>(18);
        for(Kind k:Kind.values()) {
            int spacing=(k==Kind.DREADSHIP?majorSpacing:minorSpacing)*16;
            int gx=Math.floorDiv(blockX,spacing), gz=Math.floorDiv(blockZ,spacing);
            for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
                Site s=candidate(k,gx+dx,gz+dz);
                if(s!=null && (k==Kind.DREADSHIP || clearOfMajor(s))) sites.add(s);
            }
        }
        return sites;
    }
    private boolean clearOfMajor(Site s) {
        int spacing=majorSpacing*16, gx=Math.floorDiv(s.x,spacing), gz=Math.floorDiv(s.z,spacing);
        for(int dx=-1;dx<=1;dx++) for(int dz=-1;dz<=1;dz++) {
            Site other=candidate(Kind.DREADSHIP,gx+dx,gz+dz);
            if(other!=null && Math.hypot(other.x-s.x,other.z-s.z)<200) return false;
        }
        return true;
    }
    public Site at(int x,int z,int margin) {
        for(Site s:nearby(x,z)) if(s.contains(x,z,margin)) return s;
        return null;
    }
    public Site locate(int x,int z,Kind kind,int cells) {
        Site nearest=null; double best=Double.MAX_VALUE;
        int spacing=(kind==Kind.DREADSHIP?majorSpacing:minorSpacing)*16;
        int gx=Math.floorDiv(x,spacing),gz=Math.floorDiv(z,spacing);
        for(int dx=-cells;dx<=cells;dx++) for(int dz=-cells;dz<=cells;dz++) {
            Site s=candidate(kind,gx+dx,gz+dz);
            if(s==null || (kind==Kind.SANCTUM&&!clearOfMajor(s))) continue;
            double d=Math.hypot((double)s.x-x,(double)s.z-z);
            if(d<best) { best=d; nearest=s; }
        }
        return nearest;
    }
}
