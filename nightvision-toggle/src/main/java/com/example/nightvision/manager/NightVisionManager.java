package com.example.nightvision.manager;

import com.example.nightvision.NightVisionPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NightVisionManager {

    private final NightVisionPlugin plugin;
    private final NamespacedKey keyNvEnabled;
    private final Set<UUID> activePlayers = ConcurrentHashMap.newKeySet();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public NightVisionManager(NightVisionPlugin plugin) {
        this.plugin = plugin;
        this.keyNvEnabled = new NamespacedKey(plugin, "nv_enabled");
    }

    public boolean isEnabled(Player player) {
        return activePlayers.contains(player.getUniqueId());
    }

    public void loadPlayer(Player player) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        Byte value = pdc.get(keyNvEnabled, PersistentDataType.BYTE);

        boolean shouldEnable;
        if (value != null) {
            shouldEnable = (value == (byte) 1);
        } else {
            shouldEnable = plugin.getConfig().getBoolean("default-enabled", false);
        }

        if (shouldEnable) {
            activePlayers.add(player.getUniqueId());
            applyEffect(player);
        } else {
            activePlayers.remove(player.getUniqueId());
            removeEffect(player);
        }
    }

    public void setEnabled(Player player, boolean enable, boolean sendFeedback) {
        PersistentDataContainer pdc = player.getPersistentDataContainer();
        pdc.set(keyNvEnabled, PersistentDataType.BYTE, enable ? (byte) 1 : (byte) 0);

        if (enable) {
            activePlayers.add(player.getUniqueId());
            applyEffect(player);

            if (sendFeedback) {
                sendMessage(player, "messages.enabled");
                playSound(player, "sound-on");
            }
        } else {
            activePlayers.remove(player.getUniqueId());
            removeEffect(player);

            if (sendFeedback) {
                sendMessage(player, "messages.disabled");
                playSound(player, "sound-off");
            }
        }
    }

    public void toggle(Player player) {
        setEnabled(player, !isEnabled(player), true);
    }

    public void applyEffect(Player player) {
        if (!isEnabled(player)) return;

        // PotionEffect: duration INFINITE_DURATION (หรือ 1 วัน), ambient=false, particles=false, icon=false
        PotionEffect effect = new PotionEffect(
                PotionEffectType.NIGHT_VISION,
                PotionEffect.INFINITE_DURATION,
                0,
                false,
                false,
                false
        );
        player.addPotionEffect(effect);
    }

    public void removeEffect(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }

    public void unloadPlayer(Player player) {
        activePlayers.remove(player.getUniqueId());
    }

    public void sendMessage(Player player, String configPath) {
        String prefix = plugin.getConfig().getString("messages.prefix", "");
        String msg = plugin.getConfig().getString(configPath, "");
        if (msg.isEmpty()) return;

        Component component = miniMessage.deserialize(prefix + msg);
        player.sendMessage(component);
    }

    private void playSound(Player player, String configPath) {
        if (!plugin.getConfig().getBoolean("play-sound", true)) return;
        String soundName = plugin.getConfig().getString(configPath, "");
        if (soundName.isEmpty()) return;

        try {
            org.bukkit.NamespacedKey key = org.bukkit.NamespacedKey.minecraft(soundName.toLowerCase().replace("minecraft:", ""));
            Sound sound = org.bukkit.Registry.SOUNDS.get(key);
            if (sound != null) {
                player.playSound(player.getLocation(), sound, 0.8f, 1.2f);
            }
        } catch (Throwable ignored) {}
    }
}
