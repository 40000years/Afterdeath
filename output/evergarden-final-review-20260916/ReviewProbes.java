import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.crop.*;
import com.example.voidscape.listener.*;
import com.example.voidscape.pack.PackHttpServer;
import com.example.voidscape.dungeon.*;
import org.bukkit.*;
import org.bukkit.block.*;
import org.bukkit.entity.*;
import org.bukkit.persistence.*;
import org.bukkit.inventory.*;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.lang.reflect.*;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;

/** Diagnostic mocks only: no Minecraft server or live world is opened. */
public class ReviewProbes {
    static final Path DIR=diagnosticDirectory();
    static Path diagnosticDirectory(){try{return Files.createTempDirectory("evergarden-review-probes-");}catch(java.io.IOException e){throw new java.io.UncheckedIOException(e);}}
    static sun.misc.Unsafe unsafe;
    static Map<Object,World> worlds=new HashMap<>();
    static Map<UUID,Player> players=new HashMap<>();
    static Object empty(Method m) {
        Class<?> t=m.getReturnType();
        if(t==boolean.class)return false;if(t==int.class)return 0;if(t==long.class)return 0L;
        if(t==double.class)return 0d;if(t==float.class)return 0f;
        return null;
    }
    static <T>T proxy(Class<T> t,InvocationHandler h){return t.cast(java.lang.reflect.Proxy.newProxyInstance(t.getClassLoader(),new Class<?>[]{t},(o,m,a)->{
        if(m.getName().equals("hashCode"))return System.identityHashCode(o);
        if(m.getName().equals("equals"))return o==a[0];
        if(m.getName().equals("toString"))return "Mock"+t.getSimpleName();
        return h.invoke(o,m,a);
    }));}
    static void field(Object object,Class<?> type,String name,Object value)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);f.set(object,value);}
    static <T>T allocate(Class<T> t)throws Exception{return t.cast(unsafe.allocateInstance(t));}
    static void check(boolean v,String message){if(!v)throw new AssertionError(message);System.out.println("PASS "+message);}
    static Object call(Object o,String name,Class<?>[] types,Object... args)throws Exception{
        Method m=o.getClass().getDeclaredMethod(name,types);m.setAccessible(true);
        try{return m.invoke(o,args);}catch(InvocationTargetException e){throw new RuntimeException(e.getCause());}
    }
    static final class Grid {
        UUID id=UUID.randomUUID();Map<String,Material> blocks=new HashMap<>();
        Collection<Entity> nearby=new ArrayList<>();String name;World w;
        Grid(String name){this.name=name;w=proxy(World.class,(o,m,a)->switch(m.getName()){
            case "getUID"->id;case "getName"->name;case "getMinHeight"->-64;case "getMaxHeight"->320;
            case "getBlockAt"->a[0] instanceof Location l?block(l.getBlockX(),l.getBlockY(),l.getBlockZ()):block((int)a[0],(int)a[1],(int)a[2]);
            case "getNearbyEntities"->nearby;default->empty(m);
        });worlds.put(name,w);worlds.put(id,w);}
        String key(int x,int y,int z){return x+","+y+","+z;}
        void set(int x,int y,int z,Material mat){blocks.put(key(x,y,z),mat);}
        Block block(int x,int y,int z){return proxy(Block.class,(o,m,a)->switch(m.getName()){
            case "getX"->x;case "getY"->y;case "getZ"->z;case "getWorld"->w;
            case "getType"->blocks.getOrDefault(key(x,y,z),Material.AIR);
            case "setType"->{set(x,y,z,(Material)a[0]);yield null;}
            case "getLocation"->new Location(w,x,y,z);
            case "getRelative"->{if(a[0] instanceof BlockFace f){int n=a.length==2?(int)a[1]:1;yield block(x+f.getModX()*n,y+f.getModY()*n,z+f.getModZ()*n);}yield block(x+(int)a[0],y+(int)a[1],z+(int)a[2]);}
            default->empty(m);
        });}
    }
    static class AirStack extends ItemStack { @Override public Material getType(){return Material.AIR;} }
    public static void main(String[] args)throws Exception {
        if(args.length>0&&args[0].equals("particle")){
            System.out.println("Particle.FLASH data type: "+Particle.FLASH.getDataType());
            return;
        }
        if(args.length>0&&args[0].equals("dependency")){
            try {Class.forName("com.example.voidscape.crop.CropBuffListener").getDeclaredMethods();System.out.println("CropBuffListener reflection: OK");}
            catch(LinkageError e){System.out.println("CropBuffListener reflection: "+e);}
            return;
        }
        Field uf=sun.misc.Unsafe.class.getDeclaredField("theUnsafe");uf.setAccessible(true);unsafe=(sun.misc.Unsafe)uf.get(null);
        Server server=proxy(Server.class,(o,m,a)->switch(m.getName()){
            case "getLogger"->Logger.getLogger("review-mocks");case "getName","getVersion","getBukkitVersion"->"review-mocks";
            case "getWorld"->worlds.get(a[0]);case "getPlayer"->players.get(a[0]);default->empty(m);
        });Field bs=Bukkit.class.getDeclaredField("server");bs.setAccessible(true);bs.set(null,server);
        VoidscapePlugin plugin=allocate(VoidscapePlugin.class);
        field(plugin,JavaPlugin.class,"dataFolder",DIR.toFile());field(plugin,JavaPlugin.class,"server",server);
        field(plugin,JavaPlugin.class,"newConfig",new YamlConfiguration());field(plugin,JavaPlugin.class,"logger",Logger.getLogger("review"));
        for(Axis axis:List.of(Axis.X,Axis.Z)){
            Grid grid=new Grid("portal-"+axis);
            for(int d=0;d<4;d++)for(int y=64;y<69;y++)if(d==0||d==3||y==64||y==68)grid.set(axis==Axis.X?d:0,y,axis==Axis.X?0:d,Material.QUARTZ_BLOCK);
            TravelListener travel=allocate(TravelListener.class);PortalVisuals visuals=new PortalVisuals(plugin);
            field(travel,TravelListener.class,"visuals",visuals);
            Block inner=grid.block(axis==Axis.X?1:0,65,axis==Axis.X?0:1);
            check((boolean)call(travel,"checkAndFillPortal",new Class<?>[]{Block.class,Axis.class},inner,axis),"Quartz 4x5 frame accepted axis "+axis);
            check(grid.blocks.values().stream().filter(v->v==Material.STRUCTURE_VOID).count()==6,"six registered portal cells axis "+axis);
        }
        Grid farm=new Grid("farm");
        Grid cave=new Grid("cave");cave.set(0,65,0,Material.CAVE_AIR);
        TravelListener caveTravel=allocate(TravelListener.class);
        check(!(boolean)call(caveTravel,"tryIgnitePortal",new Class<?>[]{Block.class,Player.class},cave.block(0,65,0),null),"REPRODUCED: ignition entry rejects CAVE_AIR before checking quartz frame");
        CropService crops=allocate(CropService.class);field(crops,CropService.class,"cropEntityKey",new NamespacedKey("voidscape","crop_entity"));
        field(crops,CropService.class,"entityUuidToCrop",new HashMap<UUID,PlantedCrop>());field(crops,CropService.class,"dirty",new AtomicBoolean(false));
        NamespacedKey cropKey=new NamespacedKey("voidscape","crop_entity");
        PersistentDataContainer neighborData=proxy(PersistentDataContainer.class,(o,m,a)->switch(m.getName()){
            case "has"->a[0].equals(cropKey);case "get"->a[0].equals(cropKey)?"farm,1,65,0":null;default->empty(m);
        });
        UUID neighborId=UUID.randomUUID();Interaction neighbor=proxy(Interaction.class,(o,m,a)->switch(m.getName()){
            case "getUniqueId"->neighborId;case "isValid"->true;case "getPersistentDataContainer"->neighborData;default->empty(m);
        });farm.nearby.add(neighbor);
        PlantedCrop crop=new PlantedCrop(new Location(farm.w,0,65,0),CropType.MANA_DEW_BERRY,0,0,null,null);
        Object adopted=call(crops,"getOrSpawnInteraction",new Class<?>[]{PlantedCrop.class},crop);
        check(adopted==neighbor,"REPRODUCED: crop adopts neighboring interaction with a different ownership key");

        field(crops,CropService.class,"plugin",plugin);field(crops,CropService.class,"saveFile",DIR.resolve("mock-crops.yml").toFile());
        field(crops,CropService.class,"plantedCrops",new HashMap<String,PlantedCrop>());
        YamlConfiguration saved=new YamlConfiguration();
        saved.set("farm,0,65,0.type","mana_dew_berry");saved.set("farm,0,65,0.stage",0);
        saved.set("not_loaded_world,1,65,0.type","mana_dew_berry");saved.set("not_loaded_world,1,65,0.stage",1);
        saved.save(DIR.resolve("mock-crops.yml").toFile());
        crops.loadCrops();crops.saveCropsSync();
        check(!YamlConfiguration.loadConfiguration(DIR.resolve("mock-crops.yml").toFile()).contains("not_loaded_world,1,65,0"),"REPRODUCED: loading before a world exists then saving discards its crops");

        DungeonManager dungeon=allocate(DungeonManager.class);
        NamespacedKey hitsKey=new NamespacedKey("voidscape","true_death_hits"),levelKey=new NamespacedKey("voidscape","true_death_level");
        field(dungeon,DungeonManager.class,"trueDeathHitsKey",hitsKey);field(dungeon,DungeonManager.class,"trueDeathLevelKey",levelKey);
        field(dungeon,DungeonManager.class,"trueDeathBars",new HashMap<>());
        Map<Object,Object> pdcData=new HashMap<>();
        PersistentDataContainer pdc=proxy(PersistentDataContainer.class,(o,m,a)->switch(m.getName()){
            case "getOrDefault"->pdcData.getOrDefault(a[0],a[2]);case "get"->pdcData.get(a[0]);
            case "remove"->{pdcData.remove(a[0]);yield null;}default->empty(m);
        });
        ItemStack air=allocate(AirStack.class);
        PlayerInventory inv=proxy(PlayerInventory.class,(o,m,a)->switch(m.getName()){
            case "getItemInMainHand","getItemInOffHand"->air;default->empty(m);
        });
        UUID playerId=UUID.randomUUID();double[] hp={1.0};boolean[] online={true};
        Player player=proxy(Player.class,(o,m,a)->switch(m.getName()){
            case "getUniqueId"->playerId;case "isOnline"->online[0];case "getHealth"->hp[0];case "isDead"->hp[0]<=0;
            case "getInventory"->inv;case "getPersistentDataContainer"->pdc;
            case "setHealth"->{hp[0]=(double)a[0];yield null;}default->empty(m);
        });players.put(playerId,player);pdcData.put(levelKey,1);
        call(dungeon,"lambda$recordTrueDeathHit$0",new Class<?>[]{UUID.class,int.class,TrueDeathProgress.class},playerId,1,new TrueDeathProgress(0,1,true,false));
        check(hp[0]==0,"REPRODUCED: queued True Death I kills a 1-HP player whose last Totem was already consumed by the boss hit");
        hp[0]=20;online[0]=false;pdcData.put(levelKey,5);pdcData.put(hitsKey,0);
        call(dungeon,"lambda$recordTrueDeathHit$0",new Class<?>[]{UUID.class,int.class,TrueDeathProgress.class},playerId,5,new TrueDeathProgress(0,5,true,true));
        check(hp[0]==20&&pdcData.get(levelKey).equals(5),"REPRODUCED: queued True Death V is dropped for offline player while retaining level V");
        check(!TrueDeathProgress.hit(0,5,10,5).finalDeath(),"next hit after missed V execution does not retry final death");
        check((boolean)call(dungeon,"clearTrueDeath",new Class<?>[]{Player.class},player)&&pdcData.isEmpty(),"curse clear removes both persisted counters");

        byte[] pack=Files.readAllBytes(Path.of("evergarden/dist/evergarden-java.zip"));
        String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
        try(PackHttpServer host=new PackHttpServer("127.0.0.1",0,pack,digest);HttpClient client=HttpClient.newHttpClient()){
            String base="http://127.0.0.1:"+host.port(),url=base+host.path();
            var get=client.send(HttpRequest.newBuilder(URI.create(url)).GET().build(),HttpResponse.BodyHandlers.ofByteArray());
            check(get.statusCode()==200&&Arrays.equals(get.body(),pack),"HTTP GET serves exact embedded ZIP bytes");
            var head=client.send(HttpRequest.newBuilder(URI.create(url)).method("HEAD",HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.ofByteArray());
            check(head.statusCode()==200&&head.body().length==0&&head.headers().firstValueAsLong("Content-Length").orElse(-1)==pack.length,"HTTP HEAD metadata");
            var cached=client.send(HttpRequest.newBuilder(URI.create(url)).header("If-None-Match","\""+digest+"\"").GET().build(),HttpResponse.BodyHandlers.discarding());
            check(cached.statusCode()==304,"HTTP ETag cache response");
            for(String path:List.of("/","/../config.yml","/%2e%2e/config.yml","/evergarden/other.zip")){
                var res=client.send(HttpRequest.newBuilder(URI.create(base+path)).GET().build(),HttpResponse.BodyHandlers.discarding());
                check(res.statusCode()==404,"HTTP refuses "+path);
            }
            var post=client.send(HttpRequest.newBuilder(URI.create(url)).POST(HttpRequest.BodyPublishers.noBody()).build(),HttpResponse.BodyHandlers.discarding());
            check(post.statusCode()==405,"HTTP refuses POST");
        }
    }
}
