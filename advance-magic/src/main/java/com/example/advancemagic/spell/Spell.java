package com.example.advancemagic.spell;

import org.bukkit.Material;
import java.util.Locale;

/** Spell costs are shared by casting, recipes, help and the pack generator. */
public enum Spell {
    LIGHTNING_STRIKE("Lightning Strike", Material.LIGHTNING_ROD, 60, 8, 0xFFE578),
    FROST_NOVA("Frost Nova", Material.BLUE_ICE, 50, 12, 0x8DEAFF),
    SHADOW_STEP("Shadow Step", Material.ENDER_PEARL, 45, 6, 0x8E69D4),
    NATURES_BLOOM("Nature's Bloom", Material.ENCHANTED_GOLDEN_APPLE, 70, 25, 0x8DEF81),
    EARTH_WALL("Earth Wall", Material.REINFORCED_DEEPSLATE, 40, 10, 0xAD9877),
    DRAGONS_BREATH("Dragon's Breath", Material.DRAGON_BREATH, 75, 18, 0xE07FFF),
    VOID_PULL("Void Pull", Material.LODESTONE, 65, 14, 0x6658C9),
    INVISIBILITY_SHROUD("Invisibility Shroud", Material.PHANTOM_MEMBRANE, 50, 30, 0xBBBEDF),
    POISON_SPORES("Poison Spores", Material.SPORE_BLOSSOM, 45, 10, 0xA5D957),
    WITHER_RAY("Wither Ray", Material.NETHER_STAR, 85, 12, 0x827A91),
    SHULKER_LEVITATION("Shulker Levitation", Material.SHULKER_SHELL, 95, 35, 0x110822),
    METEOR_STRIKE("Meteor Strike", Material.MAGMA_BLOCK, 90, 20, 0xFF8546),
    IRON_ARMOR("Iron Armor", Material.IRON_BLOCK, 60, 35, 0xCCD8E0),
    TIME_DILATION("Time Dilation", Material.CLOCK, 80, 25, 0x6ADAD2),
    SOUL_DRAIN("Soul Drain", Material.SCULK_CATALYST, 70, 16, 0x51DECC);

    public final String title;
    public final Material core;
    public final int mana, cooldown, color;
    Spell(String title, Material core, int mana, int cooldown, int color) {
        this.title=title; this.core=core; this.mana=mana; this.cooldown=cooldown; this.color=color;
    }
    public String id() { return name().toLowerCase(Locale.ROOT); }
    public static Spell parse(String text) {
        try { return valueOf(text.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { return null; }
    }
}
