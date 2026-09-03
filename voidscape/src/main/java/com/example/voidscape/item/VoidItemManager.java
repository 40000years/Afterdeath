package com.example.voidscape.item;

import com.example.voidscape.VoidscapePlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class VoidItemManager {

    private final VoidscapePlugin plugin;
    private final NamespacedKey keyItemType;

    public VoidItemManager(VoidscapePlugin plugin) {
        this.plugin = plugin;
        this.keyItemType = new NamespacedKey(plugin, "void_item_type");
    }

    public ItemStack createVoidCrystal(int amount) {
        ItemStack item = new ItemStack(Material.ECHO_SHARD, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("ผลึกความว่างเปล่า (Voidic Crystal)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("ผลึกพลังงานมืดที่สกัดได้จาก The Void", NamedTextColor.GRAY),
                Component.text("👁 [คลิกขวา] เบิกเนตรแห่งความมืด (มองเห็นชัดเจน 1 นาที)", NamedTextColor.AQUA, TextDecoration.BOLD),
                Component.text("✦ ใช้สำหรับคราฟต์หรืออัปเกรดอุปกรณ์ระดับสูง", NamedTextColor.DARK_AQUA)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyItemType, PersistentDataType.STRING, "VOID_CRYSTAL");
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createNullFruit(int amount) {
        ItemStack item = new ItemStack(Material.CHORUS_FRUIT, amount);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("ผลไม้ไร้รูป (Null Fruit)", NamedTextColor.LIGHT_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("ผลไม้ลึกลับที่เติบโตในความว่างเปล่า", NamedTextColor.GRAY),
                Component.text("✔ กินเพื่อฉีกมิติ หลบหนีออกจาก The Void ทันที!", NamedTextColor.GOLD, TextDecoration.BOLD),
                Component.text("✔ ฟื้นฟูหลอดเลือดหัวใจสูงสุดที่สูญเสียไป +2 ดวง", NamedTextColor.GREEN),
                Component.text("✔ ลบล้างพลังความมืด Voidic Infusion", NamedTextColor.AQUA)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyItemType, PersistentDataType.STRING, "NULL_FRUIT");
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createShadowBlade() {
        ItemStack item = new ItemStack(Material.NETHERITE_SWORD);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("ดาบเงาทมิฬ (Shadow Blade)", NamedTextColor.DARK_RED, TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("ดาบกลืนกินแสง หลอมจากเหล็กดำและผลึก Void", NamedTextColor.GRAY),
                Component.text("⚡ [คลิกขวา] Shadow Dash: พุ่งวาร์ป 8 บล็อก", NamedTextColor.GOLD),
                Component.text("   พร้อมฟันศัตรูติดสถานะตาบอด (คูลดาวน์ 6 วิ)", NamedTextColor.DARK_GRAY)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyItemType, PersistentDataType.STRING, "SHADOW_BLADE");
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createPocketVoid() {
        ItemStack item = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text("กระเป๋าหลุมดำ (Pocket Void)", NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("ฉีกมิติเชื่อมต่อกับมิติมืดส่วนตัว", NamedTextColor.GRAY),
                Component.text("📦 [คลิกขวา] เปิดคลัง Ender Chest พกพาได้ทุกที่", NamedTextColor.AQUA)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyItemType, PersistentDataType.STRING, "POCKET_VOID");
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack createVoidArmor(Material armorMaterial, String name) {
        ItemStack item = new ItemStack(armorMaterial);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.displayName(Component.text(name, NamedTextColor.DARK_PURPLE, TextDecoration.BOLD));
            meta.lore(List.of(
                Component.text("ชุดเกราะเคลือบด้วยผลึก Voidic Crystal", NamedTextColor.GRAY),
                Component.text("👁 มองเห็นในที่มืดได้ชัดเจน (Night Vision)", NamedTextColor.AQUA),
                Component.text("🛡 ป้องกันการตก Void (ดีดวาร์ปกลับขึ้นมาทันที)", NamedTextColor.GOLD)
            ));
            meta.setEnchantmentGlintOverride(true);
            meta.getPersistentDataContainer().set(keyItemType, PersistentDataType.STRING, "VOID_ARMOR");
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean isVoidItem(ItemStack item, String type) {
        if (item == null || !item.hasItemMeta()) return false;
        String val = item.getItemMeta().getPersistentDataContainer().get(keyItemType, PersistentDataType.STRING);
        return type.equalsIgnoreCase(val);
    }

    public boolean isAnyVoidItem(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(keyItemType, PersistentDataType.STRING);
    }

    private final java.util.Map<java.util.UUID, Long> voidSightExpiry = new java.util.concurrent.ConcurrentHashMap<>();

    public void grantVoidSight(org.bukkit.entity.Player player, long durationMs) {
        voidSightExpiry.put(player.getUniqueId(), System.currentTimeMillis() + durationMs);
    }

    public boolean hasVoidSight(org.bukkit.entity.Player player) {
        Long expiry = voidSightExpiry.get(player.getUniqueId());
        if (expiry == null) return false;
        if (System.currentTimeMillis() > expiry) {
            voidSightExpiry.remove(player.getUniqueId());
            return false;
        }
        return true;
    }

    public long getVoidSightRemainingSeconds(org.bukkit.entity.Player player) {
        Long expiry = voidSightExpiry.get(player.getUniqueId());
        if (expiry == null) return 0;
        long rem = (expiry - System.currentTimeMillis()) / 1000L;
        return Math.max(0, rem);
    }
}
