package com.example.voidscape.item;

/** One exclusive reward per vault; integer tickets make even mythic odds exact. */
public final class VaultLootTable {
    public enum Reward { DIAMONDS, NETHERITE, TRIM, EQUIPMENT, CORE, MYTHIC_CORE }
    private VaultLootTable() {}
    public static Reward reward(int ticket) {
        if(ticket<0||ticket>=10000)throw new IllegalArgumentException("Vault ticket must be 0..9999");
        if(ticket<3500)return Reward.DIAMONDS;
        if(ticket<7000)return Reward.NETHERITE;
        if(ticket<8000)return Reward.TRIM;
        if(ticket<9500)return Reward.EQUIPMENT;
        if(ticket<9990)return Reward.CORE;
        return Reward.MYTHIC_CORE;
    }
}
