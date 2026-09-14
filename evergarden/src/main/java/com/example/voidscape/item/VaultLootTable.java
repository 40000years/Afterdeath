package com.example.voidscape.item;

/**
 * 10,000-ticket balanced loot table for Evergarden Vault:
 * 0..2999    (30.0%) : Healthy Consumables (Key Shard, Astral Dust, Repair Stone, Void Elixir)
 * 3000..3999 (10.0%) : Special Relic Equipment (Rift Pickaxe, Smelter Pickaxe, Storm Bow, Nova Bow, Rift Blade, Eternal Aegis)
 * 4000..5399 (14.0%) : Advance Magic Cores (14 elements)
 * 5400..7399 (20.0%) : Unique Enchant Scrolls
 * 7400..9899 (25.0%) : Limit Break Scrolls (+1)
 * 9900..9949 (0.5%)  : Mythic Core (Shulker Levitation)
 * 9950..9999 (0.5%)  : Scroll of Eternity (Unbreakable 100% Mythic)
 */
public final class VaultLootTable {
    public enum Reward {
        CONSUMABLE,
        EQUIPMENT,
        CORE,
        UNIQUE_SCROLL,
        LIMIT_BREAK,
        MYTHIC_CORE,
        SCROLL_ETERNITY
    }

    private VaultLootTable() {}

    public static Reward reward(int ticket) {
        if (ticket < 0 || ticket >= 10000) throw new IllegalArgumentException("Vault ticket must be 0..9999");
        if (ticket < 3000) return Reward.CONSUMABLE;
        if (ticket < 4000) return Reward.EQUIPMENT;
        if (ticket < 5400) return Reward.CORE;
        if (ticket < 7400) return Reward.UNIQUE_SCROLL;
        if (ticket < 9900) return Reward.LIMIT_BREAK;
        if (ticket < 9950) return Reward.MYTHIC_CORE;
        return Reward.SCROLL_ETERNITY;
    }
}
