import com.example.voidscape.world.*;
import org.bukkit.Material;
import java.util.*;

public final class GeometryChecks {
    static void check(boolean v,String s){if(!v)throw new AssertionError(s);System.out.println("PASS "+s);}
    static int index(int x,int y,int z){return ((x+55)*80+(y-90))*91+(z+45);}
    static boolean inside(int x,int y,int z){return Math.abs(x)<=55&&Math.abs(z)<=45&&y>=90&&y<170;}
    public static void main(String[] args) {
        DungeonLayout planner=new DungeonLayout(72819345,128,40,0.45,0.65);
        Set<String> major=new HashSet<>(),minor=new HashSet<>();List<DungeonLayout.Site> majors=new ArrayList<>();
        for(int x=-20000;x<=20000;x+=640)for(int z=-20000;z<=20000;z+=640)
            for(var site:planner.nearby(x,z))if(site.kind()==DungeonLayout.Kind.DREADSHIP){if(major.add(site.id()))majors.add(site);}else minor.add(site.id());
        check(minor.size()>major.size()*5,"minor structures substantially more common than mansions");
        double nearest=Double.MAX_VALUE;
        for(int i=0;i<majors.size();i++)for(int j=i+1;j<majors.size();j++)nearest=Math.min(nearest,Math.hypot((double)majors.get(i).x()-majors.get(j).x(),(double)majors.get(i).z()-majors.get(j).z()));
        check(nearest>=1024,"major minimum separation at least 1024 blocks; observed "+(int)nearest);
        for(var site:majors)checkSilent(planner.at(site.x(),site.z(),0).equals(site),"stable lookup including negative coordinates");
        check(planner.at(0,0,0)==null,"spawn island has no dungeon");
        check(new DungeonLayout(1,128,40,0,0).nearby(5000,-5000).isEmpty(),"chance zero disables structures");
        boolean[] solid=new boolean[111*80*91];
        for(int x=-55;x<=55;x++)for(int z=-45;z<=45;z++)for(int y=90;y<=95;y++)solid[index(x,y,z)]=true;
        for(var b:Blueprint.mansion().boxes())for(int x=b.x1();x<=b.x2();x++)for(int z=b.z1();z<=b.z2();z++)for(int y=b.y1();y<=b.y2();y++)
            if(inside(x,y,z))solid[index(x,y,z)]=b.material()!=Material.AIR;
        boolean[] seen=new boolean[solid.length];ArrayDeque<int[]> queue=new ArrayDeque<>();queue.add(new int[]{0,97,-32});seen[index(0,97,-32)]=true;
        while(!queue.isEmpty()) {
            int[] p=queue.removeFirst();
            for(int[] dir:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})for(int dy:new int[]{0,1,-1}) {
                int x=p[0]+dir[0],y=p[1]+dy,z=p[2]+dir[1];
                if(!inside(x,y-1,z)||!inside(x,y+1,z))continue;
                int n=index(x,y,z);if(seen[n]||solid[n]||solid[index(x,y+1,z)]||!solid[index(x,y-1,z)])continue;
                if(dy==1&&solid[index(p[0],p[1]+2,p[2])])continue;
                seen[n]=true;queue.add(new int[]{x,y,z});
            }
        }
        for(int[] target:new int[][]{{-25,97,-16},{25,104,16},{-25,111,16},{0,135,0}}) {
            boolean reached=false;
            for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(seen[index(target[0]+dx,target[1],target[2]+dz)])reached=true;
            if(!reached)for(int z:new int[]{-3,0})for(int y=96;y<=112;y++) {
                StringBuilder row=new StringBuilder("stairs z="+z+" y="+y+" ");
                for(int x=22;x<=33;x++)row.append(solid[index(x,y,z)]?'#':seen[index(x,y,z)]?'o':'.');
                System.out.println(row);
            }
            check(reached,"walkable route from entrance to "+Arrays.toString(target));
        }
        check(!solid[index(0,135,0)]&&!solid[index(0,136,0)]&&solid[index(0,134,0)],"captain spawn has clear feet/head and a deck below");
        System.out.println("STRUCTURES sampled: major="+major.size()+" minor="+minor.size());
    }
    static void checkSilent(boolean value,String message){if(!value)throw new AssertionError(message);}
}
