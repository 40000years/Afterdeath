package com.example.advancemagic.item;

import com.example.advancemagic.AdvanceMagicPlugin;
import com.example.advancemagic.spell.Spell;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCreativeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

/**
 * Bridges Bedrock Edition creative inventory actions with Java server.
 * Intercepts Bedrock ItemStackRequestPacket to detect custom item selections,
 * and restores the full ItemStack (CustomModelData, PDC, lore, etc.)
 * via InventoryCreativeEvent and interact fallback.
 */
public final class BedrockCreativeBridge implements Listener {
    private final AdvanceMagicPlugin plugin;
    private final Map<UUID, PendingCreative> pendingItems = new ConcurrentHashMap<>();
    private final Set<UUID> hookedPlayers = Collections.synchronizedSet(new HashSet<>());
    private boolean geyserPresent = false;

    private static final class PendingCreative {
        final String identifier;
        final long timestamp;
        PendingCreative(String identifier, long timestamp) {
            this.identifier = identifier;
            this.timestamp = timestamp;
        }
    }

    public BedrockCreativeBridge(AdvanceMagicPlugin plugin) {
        this.plugin = plugin;
        try {
            Class.forName("org.geysermc.geyser.api.GeyserApi");
            geyserPresent = true;
            plugin.getLogger().info("[BedrockCreativeBridge] Geyser detected! Bedrock creative mode item spawning is enabled.");
        } catch (ClassNotFoundException e) {
            plugin.getLogger().info("[BedrockCreativeBridge] Geyser not found. Bedrock creative bridge is inactive.");
        }
    }

    public void init() {
        if (!geyserPresent) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            hookPlayer(p);
        }
    }

    private static Object invokeMethod(Object target, String methodName, Class<?>[] paramTypes, Object[] args) throws Exception {
        if (target == null) return null;
        Method m = null;
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                m = clazz.getDeclaredMethod(methodName, paramTypes == null ? new Class<?>[0] : paramTypes);
                break;
            } catch (NoSuchMethodException e) {
                clazz = clazz.getSuperclass();
            }
        }
        if (m == null) {
            m = target.getClass().getMethod(methodName, paramTypes == null ? new Class<?>[0] : paramTypes);
        }
        m.setAccessible(true);
        return m.invoke(target, args == null ? new Object[0] : args);
    }

    public void hookPlayer(Player player) {
        if (!geyserPresent || player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Method apiMethod = apiClass.getMethod("api");
            Object api = apiMethod.invoke(null);
            Method connMethod = apiClass.getMethod("connectionByUuid", UUID.class);
            Object conn = connMethod.invoke(api, uuid);
            if (conn == null) return; // Not a Bedrock player

            Object upstream = invokeMethod(conn, "getUpstream", null, null);
            if (upstream == null) return;

            Object bedrockSession = invokeMethod(upstream, "getSession", null, null);
            if (bedrockSession == null) return;

            Object currentHandler = invokeMethod(bedrockSession, "getPacketHandler", null, null);
            if (currentHandler == null) return;

            if (Proxy.isProxyClass(currentHandler.getClass())) {
                InvocationHandler existing = Proxy.getInvocationHandler(currentHandler);
                if (existing instanceof CreativePacketInterceptor) {
                    hookedPlayers.add(uuid);
                    return; // Already hooked
                }
            }

            Set<Class<?>> interfaces = new LinkedHashSet<>();
            for (Class<?> c = currentHandler.getClass(); c != null; c = c.getSuperclass()) {
                Collections.addAll(interfaces, c.getInterfaces());
            }
            try {
                interfaces.add(Class.forName("org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler"));
            } catch (ClassNotFoundException ignored) {}

            CreativePacketInterceptor interceptor = new CreativePacketInterceptor(player, conn, currentHandler);
            Object proxy = Proxy.newProxyInstance(
                currentHandler.getClass().getClassLoader(),
                interfaces.toArray(new Class<?>[0]),
                interceptor
            );

            Method setPacketHandler = bedrockSession.getClass().getMethod("setPacketHandler",
                Class.forName("org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler"));
            setPacketHandler.setAccessible(true);
            setPacketHandler.invoke(bedrockSession, proxy);
            hookedPlayers.add(uuid);
            plugin.getLogger().info("[BedrockCreativeBridge] Hooked creative packet listener for Bedrock player " + player.getName());
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[BedrockCreativeBridge] Could not hook player " + player.getName(), t);
        }
    }

    private final class CreativePacketInterceptor implements InvocationHandler {
        private final Player player;
        private final Object geyserSession;
        private final Object delegate;

        CreativePacketInterceptor(Player player, Object geyserSession, Object delegate) {
            this.player = player;
            this.geyserSession = geyserSession;
            this.delegate = delegate;
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            String name = method.getName();
            if (args != null && args.length == 1 && args[0] != null) {
                Object packet = args[0];
                String packetName = packet.getClass().getSimpleName();
                if ("ItemStackRequestPacket".equals(packetName)) {
                    try {
                        onItemStackRequest(player, geyserSession, packet);
                    } catch (Throwable t) {
                        plugin.getLogger().log(Level.WARNING, "[BedrockCreativeBridge] Error in onItemStackRequest", t);
                    }
                }
            }
            return method.invoke(delegate, args);
        }
    }

    private void onItemStackRequest(Player player, Object session, Object packet) {
        try {
            List<?> requests = (List<?>) invokeMethod(packet, "getRequests", null, null);
            if (requests == null) return;

            for (Object req : requests) {
                Object actionsObj = invokeMethod(req, "getActions", null, null);
                Object[] actions = actionsObj instanceof Object[] a ? a :
                                   actionsObj instanceof List<?> l ? l.toArray() : null;
                if (actions == null) continue;

                int creativeNetId = -1;
                for (Object action : actions) {
                    if (action == null) continue;
                    if (action.getClass().getSimpleName().contains("CraftCreative")) {
                        Object netIdObj = invokeMethod(action, "getCreativeItemNetworkId", null, null);
                        if (netIdObj instanceof Integer id) {
                            creativeNetId = id;
                        }
                        break;
                    }
                }

                if (creativeNetId > 0) {
                    Object itemMappings = invokeMethod(session, "getItemMappings", null, null);
                    List<?> creativeList = (List<?>) invokeMethod(itemMappings, "getCreativeItems", null, null);
                    int index = creativeNetId - 1;
                    if (creativeList != null && index >= 0 && index < creativeList.size()) {
                        Object creativeItem = creativeList.get(index);
                        Object itemData = invokeMethod(creativeItem, "getItem", null, null);
                        Object def = invokeMethod(itemData, "getDefinition", null, null);
                        String identifier = (String) invokeMethod(def, "getIdentifier", null, null);

                        plugin.getLogger().info("[BedrockCreativeBridge] Bedrock creative select: netId=" + creativeNetId + ", id=" + identifier);
                        if (identifier != null && identifier.startsWith("advance_magic:")) {
                            pendingItems.put(player.getUniqueId(), new PendingCreative(identifier, System.currentTimeMillis()));
                            plugin.getLogger().info("[BedrockCreativeBridge] Queued creative item: " + identifier + " for " + player.getName());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "[BedrockCreativeBridge] Error in onItemStackRequest", t);
        }
    }

    private ItemStack resolveCustomItem(String identifier) {
        if (identifier == null) return null;
        if (identifier.startsWith("advance_magic:core_")) {
            String spellId = identifier.substring("advance_magic:core_".length());
            Spell spell = Spell.parse(spellId);
            if (spell != null) return plugin.wands().createCore(spell);
        } else if (identifier.startsWith("advance_magic:")) {
            String spellId = identifier.substring("advance_magic:".length());
            Spell spell = Spell.parse(spellId);
            if (spell != null) return plugin.wands().create(spell);
        }
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Player p = event.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> hookPlayer(p), 10L);
        Bukkit.getScheduler().runTaskLater(plugin, () -> hookPlayer(p), 30L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        pendingItems.remove(uuid);
        hookedPlayers.remove(uuid);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreative(InventoryCreativeEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        PendingCreative pending = pendingItems.get(player.getUniqueId());
        plugin.getLogger().info("[BedrockCreativeBridge] onCreative fired: slot=" + event.getSlot()
            + ", cursor=" + (event.getCursor() != null ? event.getCursor().getType() : "null")
            + ", current=" + (event.getCurrentItem() != null ? event.getCurrentItem().getType() : "null")
            + ", pending=" + (pending != null ? pending.identifier : "none"));

        if (pending == null) return;

        // Pending pickup valid for up to 10 seconds
        if (System.currentTimeMillis() - pending.timestamp > 10000L) {
            pendingItems.remove(player.getUniqueId());
            return;
        }

        ItemStack replacement = resolveCustomItem(pending.identifier);
        if (replacement != null) {
            final ItemStack finalItem = replacement;
            event.setCursor(finalItem);
            event.setCurrentItem(finalItem);

            final int slot = event.getSlot();
            if (slot == -1) {
                player.getWorld().dropItemNaturally(player.getLocation(), finalItem);
            } else {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (slot >= 0 && slot < player.getInventory().getSize()) {
                        player.getInventory().setItem(slot, finalItem);
                    }
                    player.updateInventory();
                });
            }

            plugin.getLogger().info("[BedrockCreativeBridge] Restored " + pending.identifier + " for " + player.getName() + " in slot " + slot);
            pendingItems.remove(player.getUniqueId());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent event) {
        Player player = event.getPlayer();
        ItemStack inHand = event.getItem();
        if (inHand == null) return;
        if (inHand.getType() == Material.CARROT_ON_A_STICK || inHand.getType() == Material.HEART_OF_THE_SEA) {
            boolean isPlain = !inHand.hasItemMeta() ||
                (!inHand.getItemMeta().hasDisplayName() && !inHand.getItemMeta().hasCustomModelData());
            if (isPlain) {
                PendingCreative pending = pendingItems.get(player.getUniqueId());
                if (pending != null && System.currentTimeMillis() - pending.timestamp < 15000L) {
                    ItemStack restored = resolveCustomItem(pending.identifier);
                    if (restored != null) {
                        if (event.getHand() == EquipmentSlot.HAND) {
                            player.getInventory().setItemInMainHand(restored);
                        } else if (event.getHand() == EquipmentSlot.OFF_HAND) {
                            player.getInventory().setItemInOffHand(restored);
                        }
                        player.updateInventory();
                        player.sendMessage(ChatColor.GREEN + "✦ กู้คืนคทาเวทมนตร์จาก Creative เรียบร้อย!");
                        plugin.getLogger().info("[BedrockCreativeBridge] Restored plain item in hand on interact: " + pending.identifier);
                        pendingItems.remove(player.getUniqueId());
                    }
                }
            }
        }
    }
}
