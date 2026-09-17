package com.example.voidscape.enchant;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.item.RelicService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.EquipmentSlot;
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

    public boolean isScroll(ItemStack item) {
        if (item == null || item.getType().isAir()) return false;
        return relics.isScrollEternity(item)
            || relics.getLimitBreakType(item) != null
            || relics.getUniqueEnchant(item) != null;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();

        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) {
            return;
        }

        if (isScroll(cursor)) {
            event.setCancelled(true);
            applyAnyScroll(player, cursor, target, null, event);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerInteract(org.bukkit.event.player.PlayerInteractEvent event) {
        // Prevent double-firing: Paper fires PlayerInteractEvent for both HAND and OFF_HAND in the same tick
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!event.getAction().isRightClick()) return;

        Player player = event.getPlayer();
        ItemStack main = player.getInventory().getItemInMainHand();
        ItemStack off = player.getInventory().getItemInOffHand();

        if (main.getType().isAir() || off.getType().isAir()) return;

        // Case 1: Scroll in main hand, target item in off hand
        if (isScroll(main)) {
            event.setCancelled(true);
            applyAnyScroll(player, main, off, EquipmentSlot.HAND, null);
            return;
        }

        // Case 2: Scroll in off hand, target item in main hand
        if (isScroll(off)) {
            event.setCancelled(true);
            applyAnyScroll(player, off, main, EquipmentSlot.OFF_HAND, null);
            return;
        }
    }

    private void applyAnyScroll(Player player, ItemStack source, ItemStack target, EquipmentSlot hand, InventoryClickEvent clickEvent) {
        boolean isCursor = (clickEvent != null);

        if (relics.isScrollEternity(source)) {
            applyScrollEternity(player, source, target, isCursor, hand, clickEvent);
            return;
        }

        LimitBreakType lbType = relics.getLimitBreakType(source);
        if (lbType != null) {
            applyLimitBreak(player, source, target, lbType, isCursor, hand, clickEvent);
            return;
        }

        UniqueEnchant unique = relics.getUniqueEnchant(source);
        if (unique != null) {
            applyUniqueEnchant(player, source, target, unique, isCursor, hand, clickEvent);
            return;
        }
    }

    private void applyScrollEternity(Player player, ItemStack source, ItemStack target, boolean isCursor, EquipmentSlot hand, InventoryClickEvent clickEvent) {
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

        if (isCursor && clickEvent != null) {
            clickEvent.setCurrentItem(target);
        }

        consumeSource(player, source, target, isCursor, hand, clickEvent);
        success(player, "✦ ปลุกเสกศิลานิรันดร์สำเร็จ! อุปกรณ์นี้จะไม่มีวันพังถาวร");
    }

    private void applyLimitBreak(Player player, ItemStack source, ItemStack target, LimitBreakType type, boolean isCursor, EquipmentSlot hand, InventoryClickEvent clickEvent) {
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

        if (isCursor && clickEvent != null) {
            clickEvent.setCurrentItem(target);
        }

        consumeSource(player, source, target, isCursor, hand, clickEvent);
        success(player, "✦ ทลายขีดจำกัดสำเร็จ! " + type.title() + " ระดับ " + toRoman(next));
    }

    private void applyUniqueEnchant(Player player, ItemStack source, ItemStack target, UniqueEnchant enchant, boolean isCursor, EquipmentSlot hand, InventoryClickEvent clickEvent) {
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

        if (isCursor && clickEvent != null) {
            clickEvent.setCurrentItem(target);
        }

        consumeSource(player, source, target, isCursor, hand, clickEvent);
        success(player, "✦ สลักมนตราสำเร็จ! ได้รับ " + enchant.title());
    }

    private void consumeSource(Player player, ItemStack source, ItemStack target, boolean isCursor, EquipmentSlot hand, InventoryClickEvent clickEvent) {
        if (isCursor) {
            if (source.getAmount() <= 1) {
                player.setItemOnCursor(null);
                if (clickEvent != null) {
                    try { clickEvent.setCursor(null); } catch (Throwable ignored) {}
                    try { clickEvent.getView().setCursor(null); } catch (Throwable ignored) {}
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.setItemOnCursor(null);
                        if (clickEvent != null && clickEvent.getSlot() >= 0 && clickEvent.getClickedInventory() != null) {
                            clickEvent.getClickedInventory().setItem(clickEvent.getSlot(), target);
                        }
                        player.updateInventory();
                    }
                });
            } else {
                ItemStack remaining = source.clone();
                remaining.setAmount(source.getAmount() - 1);
                player.setItemOnCursor(remaining);
                if (clickEvent != null) {
                    try { clickEvent.setCursor(remaining); } catch (Throwable ignored) {}
                    try { clickEvent.getView().setCursor(remaining); } catch (Throwable ignored) {}
                }
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.isOnline()) {
                        player.setItemOnCursor(remaining);
                        if (clickEvent != null && clickEvent.getSlot() >= 0 && clickEvent.getClickedInventory() != null) {
                            clickEvent.getClickedInventory().setItem(clickEvent.getSlot(), target);
                        }
                        player.updateInventory();
                    }
                });
            }
        } else {
            ItemStack remaining = null;
            if (source.getAmount() > 1) {
                remaining = source.clone();
                remaining.setAmount(source.getAmount() - 1);
            }
            if (hand == EquipmentSlot.OFF_HAND) {
                player.getInventory().setItemInOffHand(remaining);
                player.getInventory().setItemInMainHand(target);
            } else {
                player.getInventory().setItemInMainHand(remaining);
                player.getInventory().setItemInOffHand(target);
            }
            final ItemStack finalRemaining = remaining;
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (player.isOnline()) {
                    if (hand == EquipmentSlot.OFF_HAND) {
                        player.getInventory().setItemInOffHand(finalRemaining);
                        player.getInventory().setItemInMainHand(target);
                    } else {
                        player.getInventory().setItemInMainHand(finalRemaining);
                        player.getInventory().setItemInOffHand(target);
                    }
                    player.updateInventory();
                }
            });
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
