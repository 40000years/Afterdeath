package com.example.voidscape.enchant;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.RelicService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class EnchantApplyListener implements Listener {
    private final VoidscapePlugin plugin;
    private final RelicService relics;

    public EnchantApplyListener(VoidscapePlugin plugin, RelicService relics) {
        this.plugin = plugin;
        this.relics = relics;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }

        // 1. Scroll of Eternity (Unbreakable)
        if (relics.isScrollEternity(cursor)) {
            event.setCancelled(true);
            applyScrollEternity(player, cursor, target, true);
            return;
        }

        // 2. Limit Break Scroll
        LimitBreakType lbType = relics.getLimitBreakType(cursor);
        if (lbType != null) {
            event.setCancelled(true);
            applyLimitBreak(player, cursor, target, lbType, true);
            return;
        }

        // 3. Unique Enchant Scroll
        UniqueEnchant unique = relics.getUniqueEnchant(cursor);
        if (unique != null) {
            event.setCancelled(true);
            applyUniqueEnchant(player, cursor, target, unique, true);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        if (!event.getAction().isRightClick()) return;
        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        if (main == null || main.getType().isAir() || off == null || off.getType().isAir()) return;

        if (relics.isScrollEternity(main)) {
            event.setCancelled(true);
            applyScrollEternity(player, main, off, false);
            return;
        }

        LimitBreakType lbType = relics.getLimitBreakType(main);
        if (lbType != null) {
            event.setCancelled(true);
            applyLimitBreak(player, main, off, lbType, false);
            return;
        }

        UniqueEnchant unique = relics.getUniqueEnchant(main);
        if (unique != null) {
            event.setCancelled(true);
            applyUniqueEnchant(player, main, off, unique, false);
            return;
        }
    }

    private void applyScrollEternity(Player player, ItemStack source, ItemStack target, boolean isCursor) {
        if (target.getType().getMaxDurability() <= 0) {
            fail(player, "ไอเทมนี้ไม่มีความทนทาน ไม่จำเป็นต้องใช้คัมภีร์ศิลานิรันดร์");
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;

        if (meta.isUnbreakable()) {
            fail(player, "ไอเทมนี้สถิตนิรันดร์อยู่แล้ว (ไม่มีวันพัง)");
            return;
        }

        meta.setUnbreakable(true);
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(0, Component.text("✦ สถิตนิรันดร์: ไม่มีวันพังเสียหาย", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        target.setItemMeta(meta);

        consumeSource(player, source, isCursor);
        success(player, "✦ ปลุกเสกศิลานิรันดร์สำเร็จ! อุปกรณ์นี้จะไม่มีวันพังถาวร");
    }

    private void applyLimitBreak(Player player, ItemStack source, ItemStack target, LimitBreakType type, boolean isCursor) {
        if (!type.category().matches(target.getType())) {
            fail(player, "คัมภีร์นี้ใช้ได้กับ " + type.targetDescription() + " เท่านั้น");
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;

        int current = meta.getEnchantLevel(type.enchantment());
        if (current <= 0) {
            fail(player, "อุปกรณ์ต้องมีเอนแชนต์ " + type.enchantment().getKey().getKey() + " อยู่ก่อนแล้ว");
            return;
        }
        if (current >= type.maxLevel()) {
            fail(player, "เอนแชนต์นี้ถึงระดับสูงสุดแล้ว (" + type.maxLevel() + ")");
            return;
        }

        int next = current + 1;
        meta.addEnchant(type.enchantment(), next, true);

        // Add or update custom lore line for limit break
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).contains(type.thaiTitle()));
        lore.add(Component.text("✦ " + type.thaiTitle() + " ระดับ " + toRoman(next), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        target.setItemMeta(meta);

        consumeSource(player, source, isCursor);
        success(player, "✦ ทลายขีดจำกัดสำเร็จ! " + type.title() + " ระดับ " + toRoman(next));
    }

    private void applyUniqueEnchant(Player player, ItemStack source, ItemStack target, UniqueEnchant enchant, boolean isCursor) {
        if (!enchant.category().matches(target.getType())) {
            fail(player, "คัมภีร์นี้ใช้ได้กับ " + enchant.category().name() + " เท่านั้น");
            return;
        }
        ItemMeta meta = target.getItemMeta();
        if (meta == null) return;

        NamespacedKey key = new NamespacedKey("evergarden", "ue_" + enchant.id().toLowerCase(Locale.ROOT));
        if (meta.getPersistentDataContainer().has(key)) {
            fail(player, "อุปกรณ์นี้มีมนตรา " + enchant.thaiTitle() + " อยู่แล้ว");
            return;
        }

        meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(Component.text("✦ " + enchant.title() + " · " + enchant.thaiTitle(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text("   §7" + enchant.description()).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        target.setItemMeta(meta);

        consumeSource(player, source, isCursor);
        success(player, "✦ สลักมนตราสำเร็จ! ได้รับ " + enchant.title());
    }

    private void consumeSource(Player player, ItemStack source, boolean isCursor) {
        if (isCursor) {
            if (source.getAmount() <= 1) {
                player.setItemOnCursor(null);
            } else {
                source.setAmount(source.getAmount() - 1);
                player.setItemOnCursor(source);
            }
        } else {
            if (source.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                source.setAmount(source.getAmount() - 1);
            }
        }
        player.updateInventory();
    }

    private void fail(Player player, String message) {
        player.playSound(player.getLocation(), Sound.ENTITY_VILLAGER_NO, 0.8f, 1.0f);
        player.sendActionBar(Component.text("⚠ " + message, NamedTextColor.RED));
    }

    private void success(Player player, String message) {
        player.playSound(player.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.25f);
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
        player.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, player.getLocation().add(0, 1.2, 0), 25, 0.35, 0.35, 0.35, 0.1);
        player.getWorld().spawnParticle(Particle.FIREWORK, player.getLocation().add(0, 1.2, 0), 12, 0.25, 0.25, 0.25, 0.05);
        player.sendActionBar(Component.text(message, NamedTextColor.GREEN));
    }

    private static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    public static boolean hasUnique(ItemStack item, UniqueEnchant enchant) {
        if (item == null || !item.hasItemMeta()) return false;
        NamespacedKey key = new NamespacedKey("evergarden", "ue_" + enchant.id().toLowerCase(Locale.ROOT));
        return item.getItemMeta().getPersistentDataContainer().has(key, PersistentDataType.BYTE);
    }
}
