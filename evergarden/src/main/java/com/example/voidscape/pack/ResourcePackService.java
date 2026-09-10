package com.example.voidscape.pack;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.*;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.jar.JarFile;

public final class ResourcePackService implements Listener, AutoCloseable {
    public static final UUID PACK_ID=UUID.fromString("5f118489-cb55-4d09-91c7-24cd9a2ce188");
    private static final List<String> FILES=List.of("evergarden-java.zip","evergarden-bedrock.mcpack",
            "geyser-mappings.json","pack-hashes.json");
    private final JavaPlugin plugin;
    private final Map<UUID,String> statuses=new HashMap<>();
    private PackHttpServer http;
    private String sha1="",failure="",geyserStatus="External Geyser: copy files from resource-packs/ manually.";
    public ResourcePackService(JavaPlugin plugin){this.plugin=plugin;}

    /** Runs in onLoad, before Geyser reads mappings and packs. */
    public void extract() throws IOException {
        Path output=plugin.getDataFolder().toPath().resolve("resource-packs");
        for(String name:FILES) {
            try(InputStream input=plugin.getResource("resource-packs/"+name)) {
                if(input==null)throw new IOException("Missing embedded asset: "+name+". Rebuild with evergarden/build.ps1.");
                writeChanged(output.resolve(name),input.readAllBytes());
            }
        }
        if(!plugin.getConfig().getBoolean("resource-pack.geyser.auto-install",true))return;
        Path plugins=plugin.getDataFolder().toPath().toAbsolutePath().getParent();
        Path geyser=plugins.resolve("Geyser-Spigot");
        boolean present=Files.isDirectory(geyser);
        if(!present) {
            Path altGeyser=plugins.resolve("Geyser");
            if(Files.isDirectory(altGeyser)){geyser=altGeyser;present=true;}
        }
        if(!present)try(var jars=Files.list(plugins)) {
            for(Path path:jars.filter(p->p.getFileName().toString().endsWith(".jar")).toList()) {
                try(JarFile jar=new JarFile(path.toFile())) {
                    var entry=jar.getJarEntry("plugin.yml");
                    if(entry!=null)try(var in=jar.getInputStream(entry)) {
                        String descriptor=new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8);
                        if(descriptor.matches("(?s).*\\bname:\\s*['\"]?Geyser-Spigot['\"]?\\s.*")){present=true;break;}
                    }
                }catch(IOException ignored){/* Other plugins may be being updated. */}
            }
        }
        if(present) {
            writeChanged(geyser.resolve("packs/voidscape-bedrock.mcpack"),Files.readAllBytes(output.resolve("evergarden-bedrock.mcpack")));
            writeChanged(geyser.resolve("custom_mappings/voidscape.json"),Files.readAllBytes(output.resolve("geyser-mappings.json")));
            try{Files.deleteIfExists(geyser.resolve("custom_mappings/voidscape-mappings.json"));}catch(IOException ignored){}
            geyserStatus="Bedrock pack + mappings installed before Geyser-Spigot loads.";
            plugin.getLogger().info(geyserStatus);
        }
    }
    private static void writeChanged(Path target,byte[] bytes) throws IOException {
        if(Files.exists(target)&&Arrays.equals(Files.readAllBytes(target),bytes))return;
        Files.createDirectories(target.getParent());
        Path staged=Files.createTempFile(target.getParent(),"evergarden-",".tmp");
        try {
            Files.write(staged,bytes);
            try {Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}
            catch(AtomicMoveNotSupportedException e){Files.move(staged,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(staged);}
    }
    public void start() {
        try(InputStream input=plugin.getResource("resource-packs/evergarden-java.zip")) {
            if(input==null)throw new IOException("Embedded Java pack is missing");
            byte[] pack=input.readAllBytes();
            sha1=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(pack));
            if(!plugin.getConfig().getBoolean("resource-pack.enabled",true))return;
            if(!plugin.getConfig().getString("resource-pack.url","").isBlank())return;
            if(!plugin.getConfig().getBoolean("resource-pack.host.enabled",true))return;
            http=new PackHttpServer(plugin.getConfig().getString("resource-pack.host.bind","0.0.0.0"),
                    plugin.getConfig().getInt("resource-pack.host.port",8188),pack,sha1);
            plugin.getLogger().info("Bundled Java pack served on TCP "+http.port()+". Allow this port through your host/firewall; /evergarden pack shows status.");
        }catch(IOException|GeneralSecurityException|IllegalArgumentException e) {
            failure=e.getMessage();plugin.getLogger().warning("Pack host could not start: "+failure+". Use an allocated TCP port or resource-pack.url.");
        }
    }
    private boolean bedrock(Player player) {
        for(String name:List.of("org.geysermc.floodgate.api.FloodgateApi","org.geysermc.geyser.api.GeyserApi")) {
            try {
                Class<?> type=Class.forName(name);boolean floodgate=name.contains("floodgate");
                Object api=type.getMethod(floodgate?"getInstance":"api").invoke(null);
                if((boolean)type.getMethod(floodgate?"isFloodgatePlayer":"isBedrockPlayer",UUID.class).invoke(api,player.getUniqueId()))return true;
            }catch(ReflectiveOperationException|LinkageError ignored){}
        }
        return false;
    }
    public String url(Player player) {
        String external=plugin.getConfig().getString("resource-pack.url","").trim();
        if(!external.isEmpty())return validateUrl(external);
        if(http==null)return "";
        String base=plugin.getConfig().getString("resource-pack.host.public-url","").trim();
        if(!base.isEmpty())return validateUrl(base.replaceAll("/+$","")+http.path());
        String host=plugin.getConfig().getString("resource-pack.host.public-host","").trim();
        if(host.isBlank()&&player!=null) {
            InetSocketAddress address=player.getVirtualHost();
            if(address!=null)host=address.getHostString();
        }
        if(host.isBlank())return "";
        try{return validateUrl(new URI("http",null,host,http.port(),http.path(),null,null).toASCIIString());}
        catch(URISyntaxException e){return "";}
    }
    private String validateUrl(String value) {
        try {
            URI uri=URI.create(value);
            if(!List.of("http","https").contains(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null)return "";
            return uri.toASCIIString();
        }catch(IllegalArgumentException e){return "";}
    }
    public void offer(Player player) {
        if(!plugin.getConfig().getBoolean("resource-pack.enabled",true)||bedrock(player))return;
        String url=url(player);
        if(url.isEmpty()) {statuses.put(player.getUniqueId(),"NOT_OFFERED: configure the public host/URL");return;}
        String digest=plugin.getConfig().getString("resource-pack.url","").isBlank()?sha1:
                plugin.getConfig().getString("resource-pack.sha1","").trim();
        // An empty external hash means the admin hosted the exact bundled ZIP.
        if(digest.isEmpty())digest=sha1;
        if(!digest.matches("[a-fA-F0-9]{40}")) {statuses.put(player.getUniqueId(),"INVALID_SHA1");return;}
        try {
            player.addResourcePack(PACK_ID,url,HexFormat.of().parseHex(digest),"Evergarden: relics, keys and sanctuary creatures",
                    plugin.getConfig().getBoolean("resource-pack.required",false));
            statuses.put(player.getUniqueId(),"OFFERED");
        }catch(IllegalArgumentException e){statuses.put(player.getUniqueId(),"INVALID_URL");}
    }
    @EventHandler public void join(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin,()->{if(event.getPlayer().isOnline())offer(event.getPlayer());},2);
    }
    @EventHandler public void status(PlayerResourcePackStatusEvent event) {
        if(!PACK_ID.equals(event.getID()))return;
        String status=event.getStatus().name();statuses.put(event.getPlayer().getUniqueId(),status);
        if(status.startsWith("FAILED")||status.equals("INVALID_URL")||status.equals("DISCARDED")) {
            plugin.getLogger().warning("Evergarden pack for "+event.getPlayer().getName()+": "+status+". Check TCP port/public URL and /evergarden pack.");
            event.getPlayer().sendMessage(ChatColor.YELLOW+"Evergarden textures could not load. Items still work; ask an admin to check /evergarden pack.");
        }
    }
    @EventHandler public void quit(PlayerQuitEvent event){statuses.remove(event.getPlayer().getUniqueId());}
    public void describe(CommandSender sender) {
        sender.sendMessage(ChatColor.LIGHT_PURPLE+"Evergarden resource pack");
        sender.sendMessage("Enabled: "+plugin.getConfig().getBoolean("resource-pack.enabled",true)+" | Host: "+(http==null?"off":"TCP "+http.port()));
        sender.sendMessage("Bundled SHA-1: "+sha1);
        String url=url(sender instanceof Player p?p:null);sender.sendMessage("URL: "+(url.isEmpty()?"auto from joining player's server address; set host.public-host for proxies/SRV":url));
        sender.sendMessage(geyserStatus);
        if(!failure.isEmpty())sender.sendMessage(ChatColor.RED+failure);
        if(sender instanceof Player p)sender.sendMessage("Your pack: "+statuses.getOrDefault(p.getUniqueId(),"not offered (or Bedrock)"));
        sender.sendMessage("Files: plugins/Evergarden/resource-packs/ | Retry: /evergarden pack resend");
    }
    @Override public void close(){if(http!=null){http.close();http=null;}statuses.clear();}
}
