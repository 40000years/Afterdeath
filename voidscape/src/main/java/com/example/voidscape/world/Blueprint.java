package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;
import java.util.*;

/** Immutable boxes, clipped into each generated chunk. Buildings never use display entities. */
public final class Blueprint {
    public record Box(int x1,int y1,int z1,int x2,int y2,int z2,Material material) {}
    private final List<Box> boxes=new ArrayList<>();
    private void box(int x1,int y1,int z1,int x2,int y2,int z2,Material m) { boxes.add(new Box(x1,y1,z1,x2,y2,z2,m)); }
    private void shell(int x1,int y1,int z1,int x2,int y2,int z2,Material m) {
        box(x1,y1,z1,x2,y2,z2,m); box(x1+1,y1+1,z1+1,x2-1,y2-1,z2-1,Material.AIR);
    }
    public List<Box> boxes() { return List.copyOf(boxes); }
    public static Blueprint mansion() {
        Blueprint b=new Blueprint();
        b.box(-47,95,-37,47,96,37,Material.DEEPSLATE_BRICKS);
        b.shell(-37,96,-27,37,117,27,Material.POLISHED_BLACKSTONE_BRICKS);
        for(int floor=96;floor<=110;floor+=7) {
            b.box(-36,floor,-26,36,floor,26,Material.DEEPSLATE_TILES);
            for(int x:new int[]{-11,11}) {
                b.box(x,floor+1,-26,x,floor+6,26,Material.POLISHED_DEEPSLATE);
                for(int z:new int[]{-16,0,16}) b.box(x,floor+1,z-2,x,floor+4,z+2,Material.AIR);
            }
            for(int z:new int[]{-8,8}) {
                b.box(-36,floor+1,z,-12,floor+6,z,Material.DEEPSLATE_BRICKS);
                b.box(12,floor+1,z,36,floor+6,z,Material.DEEPSLATE_BRICKS);
                b.box(-25,floor+1,z,-23,floor+3,z,Material.AIR);
                b.box(23,floor+1,z,25,floor+3,z,Material.AIR);
            }
            for(int x=-30;x<=30;x+=10) for(int z:new int[]{-27,27})
                b.box(x,floor+2,z,x+2,floor+4,z,Material.CYAN_STAINED_GLASS);
            for(int x:new int[]{-37,37}) for(int z=-20;z<=20;z+=10)
                b.box(x,floor+2,z,x,floor+4,z+2,Material.CYAN_STAINED_GLASS);
            for(int x:new int[]{-20,0,20}) for(int z:new int[]{-19,0,19})
                b.box(x,floor,z,x,floor,z,Material.SEA_LANTERN);
            // Carved altar tables and grave benches give rooms distinct silhouettes.
            for(int x:new int[]{-30,30}) for(int z:new int[]{-20,20}) {
                b.box(x-2,floor+1,z-1,x+2,floor+1,z+1,Material.CHISELED_DEEPSLATE);
                b.box(x,floor+2,z,x,floor+2,z,Material.AMETHYST_BLOCK);
            }
        }
        b.box(-3,97,-27,3,101,-26,Material.AIR);
        b.box(-5,96,-45,5,96,-28,Material.POLISHED_BLACKSTONE);
        for(int step=0;step<6;step++) b.box(-39+step,118+step,-29,39-step,118+step,29,Material.DEEPSLATE_TILES);
        // East stair tower, continuous 2-wide switchback stairs from every mansion floor to deck.
        b.shell(23,96,-7,33,137,7,Material.DEEPSLATE_BRICKS);
        for(int floor=97;floor<=135;floor+=7) {
            boolean north=((floor-97)/7)%2==0;
            b.box(22,floor,-2,24,floor+4,2,Material.AIR);
            b.box(24,floor-1,-6,32,floor-1,6,Material.POLISHED_BLACKSTONE);
            if(floor>97) {
                int previousZ=north?2:-4;
                b.box(25,floor-1,previousZ,31,floor-1,previousZ+2,Material.AIR);
                int previousTop=((floor-97)/7)%2==1?31:25;
                b.box(previousTop,floor-1,previousZ,previousTop,floor-1,previousZ+2,Material.POLISHED_BLACKSTONE);
            }
            b.box(25,floor,-5,31,floor+6,5,Material.AIR);
            for(int i=0;i<7 && floor+i<=134;i++) {
                int sx=((floor-97)/7)%2==0?25+i:31-i;
                b.box(sx,floor+i,north?-4:2,sx,floor+i,north?-2:4,Material.POLISHED_BLACKSTONE);
            }
            b.box(25,floor+6,-1,31,floor+6,1,Material.POLISHED_BLACKSTONE);
        }
        b.box(8,134,-1,31,134,1,Material.POLISHED_BLACKSTONE);
        b.box(23,135,-2,24,138,2,Material.AIR);
        // End-ship inspired hull: tapered keel, deck, bow, stern cabin and two masts.
        for(int y=126;y<=133;y++) {
            int width=3+(y-126);
            b.box(-width,y,-23-(y-126),width,y,23+(y-126),Material.POLISHED_BLACKSTONE);
            if(y>128) b.box(-width+1,y,-21-(y-126),width-1,y,21+(y-126),Material.AIR);
        }
        b.box(-10,134,-30,10,134,30,Material.DEEPSLATE_TILES);
        for(int x:new int[]{-10,10}) b.box(x,135,-29,x,135,29,Material.POLISHED_BLACKSTONE_WALL);
        b.box(9,135,-2,10,135,2,Material.AIR);
        b.shell(-7,134,17,7,141,27,Material.DEEPSLATE_BRICKS);
        b.box(-2,135,17,2,138,17,Material.AIR);
        for(int z:new int[]{-15,8}) {
            b.box(0,135,z,0,157,z,Material.BASALT);
            b.box(-9,152,z,9,152,z,Material.POLISHED_BASALT);
            b.box(-8,143,z,8,151,z,Material.CYAN_STAINED_GLASS);
            b.box(0,143,z,0,151,z,Material.BASALT);
        }
        for(int x:new int[]{-15,15}) for(int z:new int[]{-20,20}) {
            b.box(x,117,z,x,138,z,Material.IRON_CHAIN);
            b.box(x-1,116,z-1,x+1,117,z+1,Material.CHISELED_DEEPSLATE);
        }
        b.box(-25,97,-16,-25,97,-16,Material.LODESTONE);
        b.box(25,104,16,25,104,16,Material.LODESTONE);
        b.box(-25,111,16,-25,111,16,Material.LODESTONE);
        b.box(0,97,-23,0,97,-23,Material.VAULT);
        return b;
    }
    public static Blueprint sanctum() {
        Blueprint b=new Blueprint();
        // Base foundation and tiled floor
        b.box(-23,94,-23,23,96,23,Material.DEEPSLATE_TILES);
        // Outer dark fortress walls enclosing the sanctum (not an open gazebo!)
        b.shell(-22,96,-22,22,112,22,Material.POLISHED_BLACKSTONE_BRICKS);
        // Grand arched gateways on North (entrance) and South
        b.box(-4,97,-22,4,103,-22,Material.AIR);
        b.box(-4,97,22,4,103,22,Material.AIR);
        // Arch decorative trim
        b.box(-5,97,-23,-5,104,-23,Material.CHISELED_DEEPSLATE);
        b.box(5,97,-23,5,104,-23,Material.CHISELED_DEEPSLATE);
        b.box(-5,104,-23,5,104,-23,Material.CRYING_OBSIDIAN);
        // 4 massive gothic corner spires rising above the sanctum to Y=124
        for(int x:new int[]{-22,20}) for(int z:new int[]{-22,20}) {
            b.box(x,96,z,x+2,120,z+2,Material.POLISHED_BASALT);
            b.box(x,121,z,x+2,121,z+2,Material.SEA_LANTERN);
            b.box(x,122,z,x+2,124,z+2,Material.POLISHED_BLACKSTONE_WALL);
        }
        // Interior colonnade & high ceiling
        for(int x:new int[]{-14,14}) for(int z=-14;z<=14;z+=7) {
            b.box(x,97,z,x,111,z,Material.BASALT);
            b.box(x,111,z,x,111,z,Material.SEA_LANTERN);
        }
        // High stepped pyramid roof with crying obsidian pinnacle
        for(int n=0;n<10;n++) b.box(-20+n,113+n,-20+n,20-n,113+n,20-n,Material.POLISHED_BLACKSTONE);
        b.box(-3,123,-3,3,125,3,Material.CRYING_OBSIDIAN);
        // Iron chains hanging from the high ceiling down towards the altar
        for(int x:new int[]{-2,2}) for(int z:new int[]{6,10}) b.box(x,105,z,x,114,z,Material.IRON_CHAIN);
        // Central altar dais for the Lodestone seal
        b.box(-3,96,5,3,96,11,Material.CHISELED_DEEPSLATE);
        b.box(-1,96,7,1,96,9,Material.AMETHYST_BLOCK);
        b.box(0,97,8,0,97,8,Material.LODESTONE);
        // Vault sanctuary niche
        b.box(-2,96,-18,2,96,-14,Material.CHISELED_DEEPSLATE);
        b.box(0,97,-16,0,97,-16,Material.VAULT);
        b.box(-2,97,-17,-2,100,-15,Material.POLISHED_BLACKSTONE_WALL);
        b.box(2,97,-17,2,100,-15,Material.POLISHED_BLACKSTONE_WALL);
        // Lighting
        for(int x:new int[]{-10,10}) for(int z:new int[]{-10,10}) b.box(x,96,z,x,96,z,Material.SEA_LANTERN);
        return b;
    }
    public void render(ChunkData data,int chunkX,int chunkZ,DungeonLayout.Site site) {
        int ox=chunkX*16-site.x(),oz=chunkZ*16-site.z();
        for(Box b:boxes) {
            int x1=Math.max(0,b.x1-ox), x2=Math.min(15,b.x2-ox);
            int z1=Math.max(0,b.z1-oz), z2=Math.min(15,b.z2-oz);
            if(x1>x2 || z1>z2) continue;
            data.setRegion(x1,b.y1,z1,x2+1,b.y2+1,z2+1,b.material);
        }
    }
}
