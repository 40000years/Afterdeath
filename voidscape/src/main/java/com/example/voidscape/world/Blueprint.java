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
    public static Blueprint sanctumDark() {
        Blueprint b=new Blueprint();
        // Base foundation and tiled floor
        b.box(-23,94,-23,23,96,23,Material.DEEPSLATE_TILES);
        // Outer dark fortress walls
        b.shell(-22,96,-22,22,112,22,Material.POLISHED_BLACKSTONE_BRICKS);
        // Arched gateways on North (entrance) and South
        b.box(-4,97,-22,4,103,-22,Material.AIR);
        b.box(-4,97,22,4,103,22,Material.AIR);
        // Arch decorative trim
        b.box(-5,97,-23,-5,104,-23,Material.CHISELED_DEEPSLATE);
        b.box(5,97,-23,5,104,-23,Material.CHISELED_DEEPSLATE);
        b.box(-5,104,-23,5,104,-23,Material.CRYING_OBSIDIAN);
        // 4 massive gothic corner spires
        for(int x:new int[]{-22,20}) for(int z:new int[]{-22,20}) {
            b.box(x,96,z,x+2,120,z+2,Material.POLISHED_BASALT);
            b.box(x,121,z,x+2,121,z+2,Material.SEA_LANTERN);
            b.box(x,122,z,x+2,124,z+2,Material.POLISHED_BLACKSTONE_WALL);
        }
        // Interior colonnade & lighting
        for(int x:new int[]{-14,14}) for(int z=-14;z<=14;z+=7) {
            b.box(x,97,z,x,111,z,Material.BASALT);
            b.box(x,111,z,x,111,z,Material.SEA_LANTERN);
        }
        // Stepped roof with crying obsidian pinnacle
        for(int n=0;n<10;n++) b.box(-20+n,113+n,-20+n,20-n,113+n,20-n,Material.POLISHED_BLACKSTONE);
        b.box(-3,123,-3,3,125,3,Material.CRYING_OBSIDIAN);
        // Sky beacon spire visible from far away
        b.box(-1,126,-1,1,210,1,Material.CRYING_OBSIDIAN);
        for(int y=126;y<=210;y+=6) b.box(0,y,0,0,y+1,0,Material.SEA_LANTERN);
        // Decorative chains
        for(int x:new int[]{-2,2}) for(int z:new int[]{6,10}) b.box(x,105,z,x,114,z,Material.IRON_CHAIN);
        // Central altar dais for the Lodestone seal (where fight is summoned)
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

    public static Blueprint sanctumAstral() {
        Blueprint b=new Blueprint();
        // Base foundation of End Stone and Purpur
        b.box(-23,94,-23,23,96,23,Material.END_STONE_BRICKS);
        // Outer astral temple walls
        b.shell(-22,96,-22,22,112,22,Material.PURPUR_BLOCK);
        // Grand arched gateways on North and South
        b.box(-4,97,-22,4,103,-22,Material.AIR);
        b.box(-4,97,22,4,103,22,Material.AIR);
        // Arch decorative trim
        b.box(-5,97,-23,-5,104,-23,Material.PURPUR_PILLAR);
        b.box(5,97,-23,5,104,-23,Material.PURPUR_PILLAR);
        b.box(-5,104,-23,5,104,-23,Material.CRYING_OBSIDIAN);
        // 4 glowing star spires rising to Y=124
        for(int x:new int[]{-22,20}) for(int z:new int[]{-22,20}) {
            b.box(x,96,z,x+2,120,z+2,Material.PURPUR_PILLAR);
            b.box(x,121,z,x+2,121,z+2,Material.SEA_LANTERN);
            b.box(x,122,z,x+2,124,z+2,Material.END_STONE_BRICK_WALL);
        }
        // Interior colonnade & starlight lanterns
        for(int x:new int[]{-14,14}) for(int z=-14;z<=14;z+=7) {
            b.box(x,97,z,x,111,z,Material.PURPUR_PILLAR);
            b.box(x,111,z,x,111,z,Material.SEA_LANTERN);
            b.box(x,97,z,x,97,z,Material.AMETHYST_BLOCK);
        }
        // Stepped pyramid roof with amethyst & crying obsidian spire
        for(int n=0;n<10;n++) b.box(-20+n,113+n,-20+n,20-n,113+n,20-n,Material.END_STONE_BRICKS);
        b.box(-3,123,-3,3,125,3,Material.AMETHYST_BLOCK);
        b.box(0,126,0,0,128,0,Material.CRYING_OBSIDIAN);
        // Sky beacon spire visible from far away
        b.box(-1,129,-1,1,210,1,Material.PURPUR_PILLAR);
        for(int y=129;y<=210;y+=6) b.box(0,y,0,0,y+1,0,Material.SEA_LANTERN);
        // Central altar dais
        b.box(-3,96,5,3,96,11,Material.PURPUR_PILLAR);
        b.box(-1,96,7,1,96,9,Material.SEA_LANTERN);
        b.box(0,97,8,0,97,8,Material.LODESTONE);
        // Vault niche
        b.box(-2,96,-18,2,96,-14,Material.PURPUR_PILLAR);
        b.box(0,97,-16,0,97,-16,Material.VAULT);
        b.box(-2,97,-17,-2,100,-15,Material.END_STONE_BRICK_WALL);
        b.box(2,97,-17,2,100,-15,Material.END_STONE_BRICK_WALL);
        // Lighting
        for(int x:new int[]{-10,10}) for(int z:new int[]{-10,10}) b.box(x,96,z,x,96,z,Material.SEA_LANTERN);
        return b;
    }

    public static Blueprint sanctumTime() {
        Blueprint b=new Blueprint();
        // Base foundation of Polished Blackstone and Gilded Blackstone
        b.box(-23,94,-23,23,96,23,Material.POLISHED_BLACKSTONE);
        // Outer chrono temple walls
        b.shell(-22,96,-22,22,112,22,Material.POLISHED_BLACKSTONE_BRICKS);
        // Arched gateways on North and South
        b.box(-4,97,-22,4,103,-22,Material.AIR);
        b.box(-4,97,22,4,103,22,Material.AIR);
        // Arch decorative trim
        b.box(-5,97,-23,-5,104,-23,Material.GILDED_BLACKSTONE);
        b.box(5,97,-23,5,104,-23,Material.GILDED_BLACKSTONE);
        b.box(-5,104,-23,5,104,-23,Material.CRYING_OBSIDIAN);
        // 4 golden chrono spires
        for(int x:new int[]{-22,20}) for(int z:new int[]{-22,20}) {
            b.box(x,96,z,x+2,120,z+2,Material.POLISHED_BLACKSTONE);
            b.box(x,121,z,x+2,121,z+2,Material.OCHRE_FROGLIGHT);
            b.box(x,122,z,x+2,124,z+2,Material.POLISHED_BLACKSTONE_WALL);
        }
        // Interior colonnade with gilded blackstone and copper/gold motifs
        for(int x:new int[]{-14,14}) for(int z=-14;z<=14;z+=7) {
            b.box(x,97,z,x,111,z,Material.POLISHED_BASALT);
            b.box(x,102,z,x,102,z,Material.GILDED_BLACKSTONE);
            b.box(x,111,z,x,111,z,Material.OCHRE_FROGLIGHT);
        }
        // Stepped roof with gilded blackstone pinnacle
        for(int n=0;n<10;n++) b.box(-20+n,113+n,-20+n,20-n,113+n,20-n,Material.POLISHED_BLACKSTONE_BRICKS);
        b.box(-3,123,-3,3,125,3,Material.GILDED_BLACKSTONE);
        b.box(0,126,0,0,128,0,Material.CRYING_OBSIDIAN);
        // Sky beacon spire visible from far away
        b.box(-1,129,-1,1,210,1,Material.COPPER_BLOCK);
        for(int y=129;y<=210;y+=6) b.box(0,y,0,0,y+1,0,Material.OCHRE_FROGLIGHT);
        // Central altar dais for the Lodestone
        b.box(-3,96,5,3,96,11,Material.GILDED_BLACKSTONE);
        b.box(-1,96,7,1,96,9,Material.CHISELED_COPPER);
        b.box(0,97,8,0,97,8,Material.LODESTONE);
        // Vault niche
        b.box(-2,96,-18,2,96,-14,Material.GILDED_BLACKSTONE);
        b.box(0,97,-16,0,97,-16,Material.VAULT);
        b.box(-2,97,-17,-2,100,-15,Material.POLISHED_BLACKSTONE_WALL);
        b.box(2,97,-17,2,100,-15,Material.POLISHED_BLACKSTONE_WALL);
        // Lighting
        for(int x:new int[]{-10,10}) for(int z:new int[]{-10,10}) b.box(x,96,z,x,96,z,Material.OCHRE_FROGLIGHT);
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
