package com.example.voidscape.world;

import org.bukkit.Material;
import org.bukkit.generator.*;
import org.bukkit.util.noise.SimplexNoiseGenerator;
import java.util.*;

public final class VoidGenerator extends ChunkGenerator {
    private final DungeonLayout layout;
    private final SimplexNoiseGenerator islands,detail;
    private final Blueprint sanctumDark=Blueprint.sanctumDark(),sanctumAstral=Blueprint.sanctumAstral(),sanctumTime=Blueprint.sanctumTime();
    public VoidGenerator(long seed,DungeonLayout layout) {
        this.layout=layout; islands=new SimplexNoiseGenerator(seed); detail=new SimplexNoiseGenerator(seed^721945L);
    }
    @Override public void generateNoise(WorldInfo info,Random random,int cx,int cz,ChunkData data) {
        List<DungeonLayout.Site> sites=layout.nearby(cx*16+8,cz*16+8);
        for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
            int wx=cx*16+x,wz=cz*16+z;
            double density=islands.noise(wx/135.0,wz/135.0);
            double spawn=Math.hypot(wx,wz);
            DungeonLayout.Site structure=null;
            for(var site:sites) if(Math.hypot((double)wx-site.x(),(double)wz-site.z())<site.radius()+18) { structure=site; break; }
            boolean spawnIsland=spawn<85;
            if(!spawnIsland && structure==null && density<0.10) continue;
            int top=spawnIsland||structure!=null?95:88+(int)(detail.noise(wx/90.0,wz/90.0)*18);
            int depth=spawnIsland?(int)(14+(85-spawn)*0.55):structure!=null?48:12+(int)((density-0.10)*90);
            for(int y=Math.max(-50,top-depth);y<=top;y++) {
                Material m=y==top?Material.POLISHED_BLACKSTONE:Material.DEEPSLATE;
                if(y<top-6 && (wx*31+wz*17+y)%23==0) m=Material.BASALT;
                data.setBlock(x,y,z,m);
            }
            if(structure==null && !spawnIsland && ((DungeonLayout.mix((long)wx*1949+wz)&255)==0))
                data.setBlock(x,top+1,z,Material.AMETHYST_BLOCK);
        }
        for(var site:sites) if(site.contains(cx*16+8,cz*16+8,12)) {
            Blueprint bp=switch(site.kind()) {
                case SANCTUM_DARK -> sanctumDark;
                case SANCTUM_ASTRAL -> sanctumAstral;
                case SANCTUM_TIME -> sanctumTime;
            };
            bp.render(data,cx,cz,site);
        }
        if(Math.abs(cx)<=1&&Math.abs(cz)<=1) {
            for(int x=0;x<16;x++) for(int z=0;z<16;z++) {
                int wx=cx*16+x,wz=cz*16+z;
                if(Math.abs(wx)<=8&&Math.abs(wz)<=8) data.setBlock(x,96,z,Material.DEEPSLATE_TILES);
                if((Math.abs(wx)==8&&Math.abs(wz)<=8)||(Math.abs(wz)==8&&Math.abs(wx)<=8))
                    data.setBlock(x,97,z,Material.POLISHED_BLACKSTONE_WALL);
                if(wx==0&&wz==0) data.setBlock(x,96,z,Material.SEA_LANTERN);
                if(wx==0&&wz==4) data.setBlock(x,97,z,Material.LECTERN);
                if(wx==0&&Math.abs(wz)==8) data.setBlock(x,97,z,Material.AIR);
                // Return Crying Obsidian Portal at Z=-5
                if(wz==-5) {
                    if(wx>=-1 && wx<=2) {
                        data.setBlock(x,96,z,Material.CRYING_OBSIDIAN);
                        data.setBlock(x,100,z,Material.CRYING_OBSIDIAN);
                    }
                    if(wx==-1 || wx==2) {
                        data.setBlock(x,97,z,Material.CRYING_OBSIDIAN);
                        data.setBlock(x,98,z,Material.CRYING_OBSIDIAN);
                        data.setBlock(x,99,z,Material.CRYING_OBSIDIAN);
                    }
                    if(wx>=0 && wx<=1) {
                        data.setBlock(x,97,z,Material.NETHER_PORTAL);
                        data.setBlock(x,98,z,Material.NETHER_PORTAL);
                        data.setBlock(x,99,z,Material.NETHER_PORTAL);
                    }
                }
            }
        }
    }
    @Override public boolean shouldGenerateNoise(){return false;}
    @Override public boolean shouldGenerateSurface(){return false;}
    @Override public boolean shouldGenerateBedrock(){return false;}
    @Override public boolean shouldGenerateCaves(){return false;}
    @Override public boolean shouldGenerateDecorations(){return false;}
    @Override public boolean shouldGenerateMobs(){return false;}
    @Override public boolean shouldGenerateStructures(){return false;}
}
