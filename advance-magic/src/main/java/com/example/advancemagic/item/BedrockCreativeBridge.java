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
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bridges Bedrock Edition creative inventory actions with Java server.
 * When a Bedrock player selects a custom wand or core from Bedrock creative menu,
 * Geyser does not reverse-map custom model data / PDC back to Java, causing
 * it to revert to a plain vanilla item. This bridge intercepts the Bedrock
 * ItemStackRequestPacket, tracks which custom item was selected, and restores
 * the full custom ItemStack (with CustomModelData, PDC, lore, etc.) when Paper's
 * InventoryCreativeEvent fires.
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

    public void hookPlayer(Player player) {
        if (!geyserPresent || player == null || !player.isOnline()) return;
        UUID uuid = player.getUniqueId();
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = apiClass.getMethod("api").invoke(null);
            Object conn = apiClass.getMethod("connectionByUuid", UUID.class).invoke(api, uuid);
            if (conn == null) return; // Not a Bedrock player

            Method getUpstream = conn.getClass().getMethod("getUpstream");
            Object upstream = getUpstream.invoke(conn);
            if (upstream == null) return;

            Method getSession = upstream.getClass().getMethod("getSession");
            Object bedrockSession = getSession.invoke(upstream);
            if (bedrockSession == null) return;

            Method getPacketHandler = bedrockSession.getClass().getMethod("getPacketHandler");
            Object currentHandler = getPacketHandler.invoke(bedrockSession);
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
            setPacketHandler.invoke(bedrockSession, proxy);
            hookedPlayers.add(uuid);
            plugin.getLogger().info("[BedrockCreativeBridge] Hooked creative packet listener for Bedrock player " + player.getName());
        } catch (Throwable t) {
            plugin.getLogger().warning("[BedrockCreativeBridge] Could not hook player " + player.getName() + ": " + t.getMessage());
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
            if (("handle".equals(name) || "handlePacket".equals(name)) && args != null && args.length == 1 && args[0] != null) {
                Object packet = args[0];
                if ("ItemStackRequestPacket".equals(packet.getClass().getSimpleName())) {
                    try {
                        onItemStackRequest(player, geyserSession, packet);
                    } catch (Throwable t) {
                        // Suppress reflection issues
                    }
                }
            }
            return method.invoke(delegate, args);
        }
    }

    private void onItemStackRequest(Player player, Object session, Object packet) {
        try {
            Method getRequests = packet.getClass().getMethod("getRequests");
            List<?> requests = (List<?>) getRequests.invoke(packet);
            if (requests == null) return;

            for (Object req : requests) {
                Method getActions = req.getClass().getMethod("getActions");
                Object actionsObj = getActions.invoke(req);
                Object[] actions = actionsObj instanceof Object[] a ? a :
                                   actionsObj instanceof List<?> l ? l.toArray() : null;
                if (actions == null) continue;

                int creativeNetId = -1;
                for (Object action : actions) {
                    if (action == null) continue;
                    if (action.getClass().getSimpleName().contains("CraftCreative")) {
                        Method getNetId = action.getClass().getMethod("getCreativeItemNetworkId");
                        creativeNetId = (Integer) getNetId.invoke(action);
                        break;
                    }
                }

                if (creativeNetId > 0) {
                    Method getItemMappings = session.getClass().getMethod("getItemMappings");
                    Object itemMappings = getItemMappings.invoke(session);
                    Method getCreativeItems = itemMappings.getClass().getMethod("getCreativeItems");
                    List<?> creativeList = (List<?>) getCreativeItems.invoke(itemMappings);
                    int index = creativeNetId - 1;
                    if (index >= 0 && index < creativeList.size()) {
                        Object creativeItem = creativeList.get(index);
                        Method getItem = creativeItem.getClass().getMethod("getItem");
                        Object itemData = getItem.invoke(creativeItem);
                        Method getDef = itemData.getClass().getMethod("getDefinition");
                        Object def = getDef.invoke(itemData);
                        Method getId = def.getClass().getMethod("getIdentifier");
                        String identifier = (String) getId.invoke(def);

                        if (identifier != null && identifier.startsWith("advance_magic:")) {
                            pendingItems.put(player.getUniqueId(), new PendingCreative(identifier, System.currentTimeMillis()));
                            plugin.getLogger().info("[BedrockCreativeBridge] Intercepted Bedrock creative pick: " + identifier + " by " + player.getName());
                        }
                    }
                }
            }
        } catch (Throwable t) {
            // Ignore reflection issues during fast packet parsing
        }
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
        if (pending == null) return;

        // Pending pickup valid for up to 6 seconds
        if (System.currentTimeMillis() - pending.timestamp > 6000L) {
            pendingItems.remove(player.getUniqueId());
            return;
        }

        String id = pending.identifier;
        ItemStack replacement = null;

        if (id.startsWith("advance_magic:core_")) {
            String spellId = id.substring("advance_magic:core_".length());
            Spell spell = Spell.parse(spellId);
            if (spell != null) {
                replacement = plugin.wands().createCore(spell);
            }
        } else if (id.startsWith("advance_magic:")) {
            String spellId = id.substring("advance_magic:".length());
            Spell spell = Spell.parse(spellId);
            if (spell != null) {
                replacement = plugin.wands().create(spell);
            }
        }

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

            plugin.getLogger().info("[BedrockCreativeBridge] Restored " + id + " for " + player.getName() + " in slot " + slot);
            pendingItems.remove(player.getUniqueId());
        }
    }
}
