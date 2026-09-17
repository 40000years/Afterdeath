package com.example.voidscape.item;

/**
 * 10,000-ticket balanced loot table for Evergarden Vault (No junk/filler drops):
 * 0..2499    (25.0%) : Limit Break Scrolls (+1)
 * 2500..4999 (25.0%) : Unique Enchant Scrolls (22 abilities)
 * 5000..6499 (15.0%) : Advance Magic Cores (14 elements)
 * 6500..7999 (15.0%) : Special Relic Equipment (Rift Pickaxe, Smelter Pickaxe, Storm Bow, Nova Bow, Rift Blade, Eternal Aegis)
 * 8000..9599 (16.0%) : Useful Reagents & Elixirs (Key Shard, Repair Stone, Void Elixir, Echo Shard)
 * 9600..9799  (2.0%) : Mythic Core (Shulker Levitation)
 * 9800..9999  (2.0%) : Scroll of Eternity (Unbreakable 100% Mythic)
 */
public final class VaultLootTable {
    public enum Reward {
        LIMIT_BREAK,
        UNIQUE_SCROLL,
        CORE,
        EQUIPMENT,
        CONSUMABLE,
        MYTHIC_CORE,
        SCROLL_ETERNITY
    }

    private VaultLootTable() {}

    public static Reward reward(int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Vault ticket must be 0..9999");
        if (ticket < 2500) return Reward.LIMIT_BREAK;
        if (ticket < 5000) return Reward.UNIQUE_SCROLL;
        if (ticket < 6500) return Reward.CORE;
        if (ticket < 8000) return Reward.EQUIPMENT;
        if (ticket < 9600) return Reward.CONSUMABLE;
        if (ticket < 9800) return Reward.MYTHIC_CORE;
        return Reward.SCROLL_ETERNITY;
    }
}
