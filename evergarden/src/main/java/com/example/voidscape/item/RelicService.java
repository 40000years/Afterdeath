package com.example.voidscape.item;

import com.example.voidscape.VoidscapePlugin;
import com.example.voidscape.enchant.LimitBreakType;
import com.example.voidscape.enchant.UniqueEnchant;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.*;
import org.bukkit.util.Vector;

import java.util.*;

public final class RelicService implements Listener {
    public enum Relic {
        VOID_KEY(Material.TRIAL_KEY,"กุญแจ Evergarden","ใช้สำหรับเปิด Evergarden Vault ในวิหารโบราณ"),
        RIFT_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อแยกพิภพ","ขุด 3×3 บล็อกพร้อมกัน · ย่อตัวเพื่อขุดทีละก้อน"),
        SMELTER_PICKAXE(Material.NETHERITE_PICKAXE,"อีเต้อหลอมเพลิงมิติ","หลอมบล็อกที่ขุดอัตโนมัติ (ทราย->กระจก, แร่->แท่งโลหะ)"),
        STORM_BOW(Material.BOW,"ธนูพิพากษาสายฟ้า","ยิงธนูผ่าสายฟ้าต่อเนื่องใส่ศัตรู"),
        NOVA_BOW(Material.BOW,"ธนูสะเก็ดดาว","ชาร์จเต็ม: ระเบิดพลังงาน · ไม่ทำลายบล็อก"),
        RIFT_BLADE(Material.NETHERITE_SWORD,"ดาบกรีดมิติ","คลิกขวา: วาร์ปไปข้างหน้า · ต้องมีทางโล่ง"),
        ETERNAL_AEGIS(Material.SHIELD,"โล่แห่งความอมตะ","คลิกขวา: อมตะ 3 วินาที · โจมตีไม่ได้ขณะใช้งาน"),
        SCROLL_ETERNITY(Material.PAPER,"คัมภีร์ศิลานิรันดร์","ลากทับไอเทมเพื่อทำให้อุปกรณ์ 'ไม่มีวันพังถาวร (Unbreakable)'"),
        SCROLL_LIMIT_BREAK(Material.PAPER,"คัมภีร์ทลายขีดจำกัด","ลากทับไอเทมเพื่อเพิ่มเลเวลเอนแชนต์เดิม +1"),
        SCROLL_UNIQUE(Material.PAPER,"คัมภีร์มนตราโบราณ","ลากทับไอเทมเพื่อสลักเวทมนตร์เฉพาะตัว"),
        ASTRAL_DUST(Material.SUGAR,"ผงละอองดาว","ละอองดาวดึกดำบรรพ์ สสารเวทมนตร์แห่ง Evergarden"),
        KEY_SHARD(Material.PRISMARINE_SHARD,"เศษกุญแจมิติ","รวบรวมครบ 4 ชิ้นคราฟต์เป็น Evergarden Key ได้ที่โต๊ะคราฟต์"),
        REPAIR_STONE(Material.FLINT,"ศิลาฟื้นฟูมิติ","คลิกขวาเพื่อซ่อมแซมความทนทานของอุปกรณ์ 500 หน่วย"),
        VOID_ELIXIR(Material.HONEY_BOTTLE,"น้ำยาเดินเวหา","ดื่มเพื่อรับ Speed II, Jump Boost II และ Slow Falling 3 นาที");

        public final Material material; public final String title,lore;
        Relic(Material m,String t,String l){material=m;title=t;lore=l;}
        public String id(){return name().toLowerCase(Locale.ROOT);}
    }

    private final VoidscapePlugin plugin;
    private final NamespacedKey type,shot,shotOwner,shieldUntil,voidKeyTag;
    private final Set<UUID> mining=new HashSet<>();
    private final Map<UUID,Long> arrows=new HashMap<>();

    public record MagicCore(String id, String title, String wandTitle) {}
    public static final List<MagicCore> MAGIC_CORES = List.of(
        new MagicCore("lightning_strike", "Core of Lightning", "Lightning Strike"),
        new MagicCore("frost_nova", "Core of Frost", "Frost Nova"),
        new MagicCore("shadow_step", "Core of Shadows", "Shadow Step"),
        new MagicCore("natures_bloom", "Core of Nature", "Nature's Bloom"),
        new MagicCore("earth_wall", "Core of Earth", "Earth Wall"),
        new MagicCore("dragons_breath", "Core of Dragon", "Dragon's Breath"),
        new MagicCore("void_pull", "Core of the Void", "Void Pull"),
        new MagicCore("invisibility_shroud", "Core of Invisibility", "Invisibility Shroud"),
        new MagicCore("poison_spores", "Core of Poison", "Poison Spores"),
        new MagicCore("wither_ray", "Core of Wither", "Wither Ray"),
        new MagicCore("shulker_levitation", "Core of Levitation", "Shulker Levitation"),
        new MagicCore("meteor_strike", "Core of Meteor", "Meteor Strike"),
        new MagicCore("iron_armor", "Core of Iron", "Iron Armor"),
        new MagicCore("time_dilation", "Core of Time", "Time Dilation"),
        new MagicCore("soul_drain", "Core of Souls", "Soul Drain")
    );

    public RelicService(VoidscapePlugin plugin) {
        this.plugin=plugin; type=plugin.key("relic_v2");shot=plugin.key("shot");shotOwner=plugin.key("shot_owner");shieldUntil=plugin.key("shield_until");
        voidKeyTag=plugin.key("void_key");
        registerKeyRecipe();
    }

    public void registerKeyRecipe() {
        // 1. Evergarden Key (4 Key Shards 2x2)
        NamespacedKey key = plugin.key("craft_void_key");
        try { Bukkit.removeRecipe(key); } catch (Exception ignored) {}
        ShapedRecipe recipe = new ShapedRecipe(key, createVoidKey());
        recipe.shape("SS", "SS");
        recipe.setIngredient('S', new RecipeChoice.MaterialChoice(Material.PRISMARINE_SHARD));
        try { Bukkit.addRecipe(recipe); } catch (Exception ignored) {}

        // 1b. Evergarden Key Shapeless (any 4 Prismarine Shards)
        NamespacedKey keyShapeless = plugin.key("craft_void_key_shapeless");
        try { Bukkit.removeRecipe(keyShapeless); } catch (Exception ignored) {}
        ShapelessRecipe recipeShapeless = new ShapelessRecipe(keyShapeless, createVoidKey());
        recipeShapeless.addIngredient(4, Material.PRISMARINE_SHARD);
        try { Bukkit.addRecipe(recipeShapeless); } catch (Exception ignored) {}

        // 2. Vault Repair Stone (4 Astral Dust + 1 Amethyst Shard)
        NamespacedKey repairKey = plugin.key("craft_repair_stone");
        try { Bukkit.removeRecipe(repairKey); } catch (Exception ignored) {}
        ShapedRecipe repairRecipe = new ShapedRecipe(repairKey, createRepairStone(1));
        repairRecipe.shape(" D ", "DAD", " D ");
        repairRecipe.setIngredient('D', new RecipeChoice.MaterialChoice(Material.SUGAR));
        repairRecipe.setIngredient('A', Material.AMETHYST_SHARD);
        try { Bukkit.addRecipe(repairRecipe); } catch (Exception ignored) {}

        // 3. Void Walker Elixir (1 Astral Dust + 1 Glass Bottle)
        NamespacedKey elixirKey = plugin.key("craft_void_elixir");
        try { Bukkit.removeRecipe(elixirKey); } catch (Exception ignored) {}
        ShapelessRecipe elixirRecipe = new ShapelessRecipe(elixirKey, createVoidElixir(1));
        elixirRecipe.addIngredient(new RecipeChoice.MaterialChoice(Material.SUGAR));
        elixirRecipe.addIngredient(Material.GLASS_BOTTLE);
        try { Bukkit.addRecipe(elixirRecipe); } catch (Exception ignored) {}
    }

    public ItemStack createMagicCore(MagicCore core) {
        ItemStack item=new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta=item.getItemMeta();
        if(core.id().equals("shulker_levitation")) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE+"✦ "+ChatColor.GOLD+"Core of Levitation "+ChatColor.RED+"[MYTHIC]");
            meta.setLore(List.of(
                ChatColor.GOLD+"[ระดับตำนานสูงสุด · MYTHIC 0.5%]",
                ChatColor.DARK_PURPLE+"§k||§r "+ChatColor.LIGHT_PURPLE+"Forbidden Dragon Heart "+ChatColor.DARK_PURPLE+"§k||",
                ChatColor.GRAY+"ใช้คราฟต์: "+ChatColor.LIGHT_PURPLE+"Shulker Levitation Wand",
                ChatColor.YELLOW+"สูตร: 8 Netherite Ingots หรือ Nether Stars + แกนนี้",
                ChatColor.RED+"✦ อัตราดรอป 0.5% ใน Evergarden Vault [สุดยอดของแรร์]"
            ));
        } else {
            meta.setDisplayName(ChatColor.GOLD+"✦ "+core.title());
            meta.setLore(List.of(
                ChatColor.AQUA+"Ancient Magic Core (แกนเวทมนตร์โบราณ)",
                ChatColor.GRAY+"ใช้คราฟต์: "+ChatColor.LIGHT_PURPLE+core.wandTitle()+" Wand",
                ChatColor.YELLOW+"สูตร: 8 Netherite Ingots หรือ Nether Stars + แกนนี้",
                ChatColor.DARK_AQUA+"หาได้จาก: Evergarden Vault"
            ));
        }
        var modelData=meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("advance_magic:core_"+core.id()));
        meta.setCustomModelDataComponent(modelData);
        meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","core"),PersistentDataType.STRING,core.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING,core.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","magic_core"),PersistentDataType.STRING,core.id());
        item.setItemMeta(meta);
        return item;
    }

    public ItemStack createMagicCore(String id) {
        for(MagicCore c : MAGIC_CORES) {
            if(c.id().equalsIgnoreCase(id)||c.id().replace("_","").equalsIgnoreCase(id.replace("_",""))) return createMagicCore(c);
        }
        return createMagicCore(MAGIC_CORES.get(0));
    }

    public ItemStack create(Relic relic,int count) {
        ItemStack item=new ItemStack(relic.material,Math.min(relic.material.getMaxStackSize(),Math.max(1,count)));
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text(relic.title,relic==Relic.VOID_KEY?NamedTextColor.LIGHT_PURPLE:NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of(
            Component.text(relic.lore,NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
            Component.text(relic==Relic.VOID_KEY?"EVERGARDEN · TRIAL KEY":"EVERGARDEN · RELIC",NamedTextColor.DARK_PURPLE).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setItemModel(null);
        var selector=meta.getCustomModelDataComponent();selector.setStrings(List.of("voidscape:"+relic.id()));meta.setCustomModelDataComponent(selector);
        meta.getPersistentDataContainer().set(type,PersistentDataType.STRING,relic.name());

        if(relic==Relic.VOID_KEY) {
            meta.getPersistentDataContainer().set(voidKeyTag,PersistentDataType.BYTE,(byte)1);
        } else if(relic.material==Material.NETHERITE_PICKAXE||relic.material==Material.NETHERITE_SWORD||relic.material==Material.BOW||relic.material==Material.SHIELD) {
            meta.addEnchant(Enchantment.UNBREAKING,3,true);
            if(relic==Relic.RIFT_PICKAXE||relic==Relic.SMELTER_PICKAXE) {meta.addEnchant(Enchantment.EFFICIENCY,5,true);meta.addEnchant(Enchantment.FORTUNE,3,true);}
            if(relic==Relic.RIFT_BLADE) meta.addEnchant(Enchantment.SHARPNESS,8,true);
            if(relic==Relic.NOVA_BOW||relic==Relic.STORM_BOW) meta.addEnchant(Enchantment.POWER,6,true);
        }
        item.setItemMeta(meta); return item;
    }

    public ItemStack createScrollEternity() {
        ItemStack item=create(Relic.SCROLL_ETERNITY,1);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ คัมภีร์ศิลานิรันดร์ (Scroll of Eternity)",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("§4§k||§r §c[ระดับตำนานสูงสุด · MYTHIC 0.5%] §4§k||§r"),
            Component.text("ลากคัมภีร์นี้ไปแตะที่อาวุธ ชุดเกราะ หรือเครื่องมือ",NamedTextColor.WHITE).decoration(TextDecoration.ITALIC,false),
            Component.text("ไอเทมนั้นจะได้รับสถานะ 'ไม่มีวันพังเสียหาย (Unbreakable 100%)'",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false),
            Component.text("หลอดความทนทานจะหายไปตลอดกาล",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("วิธีใช้: ลากคัมภีร์ไปแตะทับไอเทมในกระเป๋า",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("scroll_eternity"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public ItemStack createScrollLimitBreak(LimitBreakType lb) {
        ItemStack item=create(Relic.SCROLL_LIMIT_BREAK,1);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ คัมภีร์ทลายขีดจำกัด: "+lb.title(),NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("✦ "+lb.thaiTitle(),NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false),
            Component.text("ใช้สำหรับ: "+lb.targetDescription(),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("วิธีใช้: ลากคัมภีร์ไปแตะทับไอเทมในกระเป๋า",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("เพื่อเพิ่มเลเวลเอนแชนต์เดิมขึ้น +1 (สูงสุดเลเวล "+lb.maxLevel()+")",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("[อัตราสำเร็จ 100% · ลากแตะเพื่อใช้งาน]",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("limit_break_type"),PersistentDataType.STRING,lb.name());
        item.setItemMeta(meta);return item;
    }

    public ItemStack createScrollUnique(UniqueEnchant ue) {
        ItemStack item=create(Relic.SCROLL_UNIQUE,1);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ มนตราโบราณ: "+ue.title(),NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("✦ "+ue.thaiTitle(),NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false),
            Component.text("ความสามารถ: "+ue.description(),NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("ประเภทอุปกรณ์: "+ue.category().name(),NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC,false),
            Component.text("วิธีใช้: ลากคัมภีร์ไปแตะทับอุปกรณ์ในกระเป๋า",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("[อัตราสำเร็จ 100% · ลากแตะเพื่อใช้งาน]",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("unique_enchant"),PersistentDataType.STRING,ue.name());
        item.setItemMeta(meta);return item;
    }

    public ItemStack createKeyShard(int count) {
        ItemStack item=create(Relic.KEY_SHARD,count);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ เศษกุญแจมิติ (Evergarden Key Shard)",NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("เศษผลึกโบราณจาก Evergarden Vault",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("• วาง 4 ชิ้น (2×2) ที่โต๊ะคราฟต์เพื่อประกอบเป็น Evergarden Key",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("• ใช้เปิดกล่องสมบัติ Evergarden Vault ประจำวิหาร",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("key_shard"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public ItemStack createAstralDust(int count) {
        ItemStack item=create(Relic.ASTRAL_DUST,count);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ ผงละอองดาว (Astral Dust)",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("ละอองดวงดาวโบราณ สสารเวทมนตร์แห่ง Evergarden",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("• คราฟต์คู่ขวดแก้ว = น้ำยาเดินเวหา (Void Elixir)",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("• ใช้ 4 ชิ้น + 1 Amethyst = ศิลาฟื้นฟู (Repair Stone)",NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("astral_dust"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public ItemStack createRepairStone(int count) {
        ItemStack item=create(Relic.REPAIR_STONE,count);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ ศิลาฟื้นฟูมิติ (Vault Repair Stone)",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("ศิลาจารึกอักขระฟื้นฟูโบราณ",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("คลิกขวาเพื่อซ่อมแซมความทนทาน 500 หน่วย",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("(ซ่อมให้กับอุปกรณ์ที่ชำรุดมากที่สุดในตัวคุณ)",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("สูตรคราฟต์: 4 Astral Dust + 1 Amethyst Shard",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("repair_stone"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public ItemStack createVoidElixir(int count) {
        ItemStack item=create(Relic.VOID_ELIXIR,count);
        ItemMeta meta=item.getItemMeta();
        meta.displayName(Component.text("✦ น้ำยาเดินเวหา (Void Walker Elixir)",NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("น้ำยาเรืองแสงผสมละอองดาวบริสุทธิ์",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false),
            Component.text("ดื่มเพื่อรับผลลัพธ์เป็นเวลา 3 นาที:",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("• ความเร็ว Speed II",NamedTextColor.AQUA).decoration(TextDecoration.ITALIC,false),
            Component.text("• กระโดดสูง Jump Boost II",NamedTextColor.GREEN).decoration(TextDecoration.ITALIC,false),
            Component.text("• ตกช้า Slow Falling (ป้องกันตกหลุม Void)",NamedTextColor.WHITE).decoration(TextDecoration.ITALIC,false),
            Component.text("สูตรคราฟต์: 1 Astral Dust + 1 ขวดแก้ว",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false)
        ));
        meta.getPersistentDataContainer().set(plugin.key("void_elixir"),PersistentDataType.BYTE,(byte)1);
        item.setItemMeta(meta);return item;
    }

    public boolean isScrollEternity(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(plugin.key("scroll_eternity"),PersistentDataType.BYTE)
            || type(item)==Relic.SCROLL_ETERNITY;
    }

    public LimitBreakType getLimitBreakType(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return null;
        String raw=item.getItemMeta().getPersistentDataContainer().get(plugin.key("limit_break_type"),PersistentDataType.STRING);
        if(raw==null) return null;
        try{return LimitBreakType.valueOf(raw);}catch(Exception e){return null;}
    }

    public UniqueEnchant getUniqueEnchant(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return null;
        String raw=item.getItemMeta().getPersistentDataContainer().get(plugin.key("unique_enchant"),PersistentDataType.STRING);
        if(raw==null) return null;
        try{return UniqueEnchant.valueOf(raw);}catch(Exception e){return null;}
    }

    public boolean isKeyShard(ItemStack item) {
        if(item==null||item.getType()!=Material.PRISMARINE_SHARD||!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.key("key_shard"),PersistentDataType.BYTE)
            || type(item)==Relic.KEY_SHARD
            || (item.getItemMeta().hasDisplayName() && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).contains("Key Shard"));
    }

    public boolean isRepairStone(ItemStack item) {
        if(item==null||item.getType()!=Material.FLINT||!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.key("repair_stone"),PersistentDataType.BYTE)
            || type(item)==Relic.REPAIR_STONE
            || (item.getItemMeta().hasDisplayName() && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).contains("Repair Stone"));
    }

    public boolean isVoidElixir(ItemStack item) {
        if(item==null||item.getType()!=Material.HONEY_BOTTLE||!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.key("void_elixir"),PersistentDataType.BYTE)
            || type(item)==Relic.VOID_ELIXIR
            || (item.getItemMeta().hasDisplayName() && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).contains("Void Walker Elixir"));
    }

    public boolean isAstralDust(ItemStack item) {
        if(item==null||item.getType()!=Material.SUGAR||!item.hasItemMeta()) return false;
        var pdc = item.getItemMeta().getPersistentDataContainer();
        return pdc.has(plugin.key("astral_dust"),PersistentDataType.BYTE)
            || type(item)==Relic.ASTRAL_DUST
            || (item.getItemMeta().hasDisplayName() && net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(item.getItemMeta().displayName()).contains("Astral Dust"));
    }

    public static String toRoman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }

    public ItemStack evaluateScrollCraft(ItemStack scroll, ItemStack target) {
        if (scroll == null || target == null || target.getType().isAir()) return null;

        // 1. Scroll of Eternity
        if (isScrollEternity(scroll)) {
            if (target.getType().getMaxDurability() <= 0) return null;
            ItemMeta meta = target.getItemMeta();
            if (meta == null || meta.isUnbreakable()) return null;
            meta.setUnbreakable(true);
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(0, Component.text("✦ สถิตนิรันดร์: ไม่มีวันพังเสียหาย", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            ItemStack result = target.clone();
            result.setItemMeta(meta);
            return result;
        }

        // 2. Limit Break Scroll
        LimitBreakType type = getLimitBreakType(scroll);
        if (type != null) {
            if (!type.category().matches(target.getType())) return null;
            ItemMeta meta = target.getItemMeta();
            if (meta == null) return null;
            int current = meta.getEnchantLevel(type.enchantment());
            if (current <= 0 || current >= type.maxLevel()) return null;
            int next = current + 1;
            meta.addEnchant(type.enchantment(), next, true);
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.removeIf(line -> net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText().serialize(line).contains(type.thaiTitle()));
            lore.add(Component.text("✦ " + type.thaiTitle() + " ระดับ " + toRoman(next), NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            ItemStack result = target.clone();
            result.setItemMeta(meta);
            return result;
        }

        // 3. Unique Enchant Scroll
        UniqueEnchant enchant = getUniqueEnchant(scroll);
        if (enchant != null) {
            if (!enchant.category().matches(target.getType())) return null;
            ItemMeta meta = target.getItemMeta();
            if (meta == null) return null;
            NamespacedKey key = new NamespacedKey("evergarden", "ue_" + enchant.id().toLowerCase(Locale.ROOT));
            if (meta.getPersistentDataContainer().has(key)) return null;
            meta.getPersistentDataContainer().set(key, PersistentDataType.BYTE, (byte) 1);
            List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.text("✦ " + enchant.title() + " · " + enchant.thaiTitle(), NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("   §7" + enchant.description()).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            ItemStack result = target.clone();
            result.setItemMeta(meta);
            return result;
        }

        return null;
    }

    public boolean migrate(ItemStack item) {
        Relic relic=type(item);if(relic==null)return false;
        var meta=item.getItemMeta();var data=meta.getCustomModelDataComponent();String model="voidscape:"+relic.id();
        if(!meta.hasItemModel()&&data.getStrings().equals(List.of(model)))return false;
        meta.setItemModel(null);data.setStrings(List.of(model));meta.setCustomModelDataComponent(data);item.setItemMeta(meta);return true;
    }
    public void migrate(Inventory inventory){for(int i=0;i<inventory.getSize();i++){var item=inventory.getItem(i);if(migrate(item))inventory.setItem(i,item);}}
    @EventHandler public void join(PlayerJoinEvent e){
        Player p=e.getPlayer();
        migrate(p.getInventory());
        migrate(p.getEnderChest());
        p.discoverRecipes(List.of(
            plugin.key("craft_void_key"),
            plugin.key("craft_void_key_shapeless"),
            plugin.key("craft_repair_stone"),
            plugin.key("craft_void_elixir")
        ));
    }
    @EventHandler public void open(org.bukkit.event.inventory.InventoryOpenEvent e){migrate(e.getInventory());migrate(e.getPlayer().getInventory());}
    @EventHandler public void pickup(EntityPickupItemEvent e){var item=e.getItem().getItemStack();if(migrate(item))e.getItem().setItemStack(item);}
    @EventHandler public void drop(ItemSpawnEvent e){var item=e.getEntity().getItemStack();if(migrate(item))e.getEntity().setItemStack(item);}
    public void migrateEntity(Entity entity) {
        if(entity instanceof org.bukkit.entity.Item dropped){var item=dropped.getItemStack();if(migrate(item))dropped.setItemStack(item);}
        if(entity instanceof ItemFrame frame){var item=frame.getItem();if(migrate(item))frame.setItem(item);}
    }
    @EventHandler public void load(org.bukkit.event.world.EntitiesLoadEvent e){e.getEntities().forEach(this::migrateEntity);}
    @EventHandler public void click(org.bukkit.event.inventory.InventoryClickEvent e){if(migrate(e.getCurrentItem()))e.setCurrentItem(e.getCurrentItem());}

    public boolean isVoidKey(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(voidKeyTag,PersistentDataType.BYTE)
            || type(item)==Relic.VOID_KEY;
    }
    public ItemStack createVoidKey() {
        return create(Relic.VOID_KEY,1);
    }

    public ItemStack rollVaultReward() {
        var random=java.util.concurrent.ThreadLocalRandom.current();
        return switch(VaultLootTable.reward(random.nextInt(10000))) {
            case SCROLL_ETERNITY -> createScrollEternity();
            case MYTHIC_CORE -> createMagicCore("shulker_levitation");
            case LIMIT_BREAK -> {
                LimitBreakType[] types=LimitBreakType.values();
                yield createScrollLimitBreak(types[random.nextInt(types.length)]);
            }
            case UNIQUE_SCROLL -> {
                UniqueEnchant[] enchants=UniqueEnchant.values();
                yield createScrollUnique(enchants[random.nextInt(enchants.length)]);
            }
            case CORE -> {
                List<MagicCore> cores=MAGIC_CORES.stream().filter(c->!c.id().equals("shulker_levitation")).toList();
                yield createMagicCore(cores.get(random.nextInt(cores.size())));
            }
            case EQUIPMENT -> {
                Relic[] equipment={Relic.RIFT_PICKAXE,Relic.SMELTER_PICKAXE,Relic.STORM_BOW,Relic.NOVA_BOW,Relic.RIFT_BLADE,Relic.ETERNAL_AEGIS};
                yield create(equipment[random.nextInt(equipment.length)],1);
            }
            case CONSUMABLE -> {
                int sub=random.nextInt(4);
                yield switch(sub) {
                    case 0 -> createKeyShard(2);
                    case 1 -> createAstralDust(4);
                    case 2 -> createRepairStone(2);
                    default -> createVoidElixir(2);
                };
            }
        };
    }

    public ItemStack createGuideBook() {
        ItemStack book=new ItemStack(Material.WRITTEN_BOOK);
        org.bukkit.inventory.meta.BookMeta meta=(org.bukkit.inventory.meta.BookMeta)book.getItemMeta();
        meta.setTitle("คู่มือมิติ Evergarden");
        meta.setAuthor("ผู้พิทักษ์มิติ");
        meta.pages(List.of(
            Component.text("§9--- มิติ Evergarden ---\n§7สวนลอยฟ้าแห่งความว่างเปล่า§r\n\nวิธีสร้างประตูมิติ:\nสร้างกรอบ Crying Obsidian ขนาด 4x5 บล็อก (ช่องใน 2x3 เหมือนประตูเนเธอร์)\n\nจุดไฟในกรอบด้วย Flint & Steel, Fire Charge หรือ Eye of Ender เพื่อเปิดประตู\n\nคำสั่งช่วยเล่น:\n• /evergarden leave (กลับโลกเดิม)\n• /evergarden guide (เปิดคู่มือเล่มนี้)"),
            Component.text("§9--- 3 มหาวิหารธาตุ ---\n\nในมิติมีมหาวิหารโบราณ 3 แห่ง:\n• วิหารแห่งความมืด (Darkness)\n• วิหารแห่งดวงดาว (Astral)\n• วิหารแห่งกาลเวลา (Time)\n\nการท้าทาย:\nคลิกขวาที่แท่น Lodestone กลางวิหารเพื่อเริ่มสู้ (ผู้เล่นทุกคนเริ่มได้)\n\nเอาชนะมอนสเตอร์ 3 เวฟ และปราบบอสประจำวิหารเพื่อรับ Evergarden Key นำไปเปิดกล่องสมบัติ Vault"),
            Component.text("§9--- กุญแจและวัตถุดิบ ---\n\n• Evergarden Key\nกุญแจสำหรับเปิดหีบ Vault ในวิหาร\n\n• Key Shard (เศษกุญแจ)\nรวบรวมครบ 4 ชิ้น วาง 2x2 ในโต๊ะคราฟต์เพื่อประกอบเป็น Evergarden Key\n\n• Astral Dust (ผงละอองดาว)\nวัตถุดิบเวทมนตร์จากมอนสเตอร์ ใช้คราฟต์:\n1. 4 Dust + 1 Amethyst = ศิลาฟื้นฟู\n2. 1 Dust + 1 ขวดแก้ว = น้ำยาเดินเวหา"),
            Component.text("§9--- ไอเทมฟื้นฟูและน้ำยา ---\n\n• Vault Repair Stone\nศิลาฟื้นฟูมิติ (คลิกขวาเพื่อใช้)\nซ่อมแซมความทนทาน 500 หน่วย ให้กับอุปกรณ์ที่ชำรุดมากที่สุดในตัวคุณ\n(สูตร: 4 Astral Dust + 1 Amethyst)\n\n• Void Walker Elixir\nน้ำยาเดินเวหา ดื่มเพื่อรับผล 3 นาที:\n- วิ่งเร็ว Speed II\n- กระโดดสูง Jump Boost II\n- ตกช้า Slow Falling (ป้องกันตก Void)\n(สูตร: 1 Astral Dust + 1 ขวดแก้ว)"),
            Component.text("§9--- คัมภีร์เวทมนตร์ ---\n\nวิธีใช้งานคัมภีร์:\nหยิบคัมภีร์ในกระเป๋า แล้วคลิกทับลงบนอุปกรณ์เป้าหมายได้โดยตรง\n\n§6✦ Scroll of Eternity §4[MYTHIC 0.5%]§r\nสุดยอดคัมภีร์ตำนาน ทำให้อุปกรณ์ชิ้นนั้นกลายเป็น Unbreakable ไม่มีวันพังเสียหายถาวร 100% หลอดความทนทานจะหายไปตลอดกาล\n\n• Limit Break Scrolls (25%)\nทลายขีดจำกัด เพิ่มเลเวลเอนแชนต์เดิมขึ้น +1 (สูงสุดระดับ 10)"),
            Component.text("§9--- คัมภีร์ทลายขีดจำกัด ---\n\nมี 6 ชนิด ทลายขีดจำกัดได้ถึงเลเวล 10:\n\n• Sharpness +1: อาวุธประชิด (ดาบ/ขวาน)\n• Protection +1: ชุดเกราะทุกชิ้น\n• Power +1: ธนู\n• Efficiency +1: อุปกรณ์ขุดเจาะ\n• Fortune +1: ที่ขุด\n• Looting +1: ดาบ\n\nอัตราสำเร็จ 100% ลากแตะเพื่อติดตั้ง"),
            Component.text("§9--- มนตราโบราณ: ธนู (1/2) ---\n\n• Colossus Slayer (ล่าไททัน):\nยิงแรงขึ้นตาม % เลือดของเป้าหมาย เหมาะสำหรับใช้ล่าบอส\n\n• Ricochet (กระสุนชิ่งสายฟ้า):\nลูกธนูชิ่งหามอนสเตอร์รอบข้าง 3 ตัวพร้อมปล่อยสายฟ้าผ่าใส่\n\n• Kinetic Grapple (ฮุกช็อตเวหา):\nยิงปักบล็อกแล้วดึงตัวผู้เล่นพุ่งไปหาจุดปักทันที เหมาะใช้เคลื่อนที่"),
            Component.text("§9--- มนตราโบราณ: ธนู (2/2) ---\n\n• Absolute Zero (เยือกแข็งสัมบูรณ์):\nแช่แข็งศัตรูในระยะหยุดนิ่ง ขยับไม่ได้ 3.5 วินาที\n\n• Singularity (หลุมดำกลืนมิติ):\nลูกธนูปักแล้วสร้างหลุมดำดูดรวบมอนสเตอร์เข้ามาเป็นเวลา 3 วินาที\n\n• Meteor Arrow (ศรดาวตกวินาศ):\nเมื่อลูกธนูปักพื้น 1 วิ อุกกาบาตยักษ์จะตกลงมาเผาพื้นที่ 5x5"),
            Component.text("§9--- มนตรา: อุปกรณ์ขุด (1/2) ---\n\n• Seismic Slam (ขุดทลาย 3x3):\nขุด 1 ครั้งระเบิดเปิดโพรง 3x3x1 บล็อกทันที ขุดหาแร่และเปิดถ้ำได้รวดเร็ว\n\n• Vein Smelter (หลอมสายแร่คู่):\nขุดทั้งสายแร่พร้อมกัน และหลอมเป็นแท่งโลหะทันที ได้รับโบนัสแร่และ EXP เต็มระบบ\n\n• Bedrock Resonance (เรดาร์ส่องแร่):\nโซนาร์เรืองแสงส่องหาแร่หายากในกำแพงหินระยะ 12 บล็อก"),
            Component.text("§9--- มนตรา: เครื่องมือ (2/2) ---\n\n• Demeter's Scythe (เคียวเทพกสิกรรม):\nเก็บเกี่ยวและปลูกคืนพืช 9x9 บล็อกอัตโนมัติ (สำหรับจอบ)\n\n• Timber Titan (โค่นทั้งป่า):\nฟันโคนไม้แล้วต้นไม้ทั้งต้นและใบไม้ล้มลงมาเป็นไอเทมทันที (สำหรับขวาน)\n\n• Telepathy (จิตสื่อสาร):\nแร่และไอเทมที่ขุดได้ทุกชิ้นจะวาร์ปเข้าตัวผู้เล่น 100% ไม่ตกหล่น"),
            Component.text("§9--- มนตรา: ดาบประชิด (1/2) ---\n\n• Guillotine (กิโยตินปลิดชีพ):\nฟันสังหารมอนสเตอร์ที่มีเลือดต่ำกว่า 15% ทันที (Execute)\n\n• Echo Strike (เงาดาบซ้ำสอง):\nโอกาส 35% เกิดเงาฟันซ้ำดาเมจเดิม 100% ภายใน 0.2 วินาที\n\n• Blade Vortex (คลื่นดาบสุญญากาศ):\nการฟันกวาดจะปล่อยคลื่นพลังพุ่งไปข้างหน้า 7 บล็อก"),
            Component.text("§9--- มนตรา: ดาบประชิด (2/2) ---\n\n• Soul Harvest (เกี่ยววิญญาณ):\nสะสมวิญญาณรอบตัวเพิ่มเดินไว +10% และดูดเลือด 5%\n\n• Thunderlord (สายฟ้าทัณฑ์สวรรค์):\nฟันเป้าหมายเดิมครบ 3 ครั้ง ผ่าสายฟ้า True Damage ทะลวงเกราะ\n\n• Vampiric (สูบโลหิต):\nแปลง 15% ของดาเมจที่ทำได้กลับมาฟื้นฟูเลือดให้ผู้เล่น"),
            Component.text("§9--- มนตรา: ชุดเกราะและสถิต ---\n\n• Phoenix Rebirth (ฟีนิกซ์คืนชีพ):\nเมื่อตาย คืนชีพทันทีพร้อมเลือด 50% และคลื่นไฟผลักศัตรู (คูลดาวน์ 10 นาที)\n\n• Shadow Step (ก้าวพริบตา):\nกดย่อ 2 ครั้ง พริบตาวาร์ปไปข้างหน้า 6 บล็อก (คูลดาวน์ 4 วิ / สำหรับรองเท้า)\n\n• Titan Stance (ร่างศิลาไร้พ่าย):\nกันกระเด็น Knockback 100% และลดแรงระเบิด 40%\n\n• Soulbound (วิญญาณสถิต):\nไอเทมจะไม่ตกและไม่สูญหายเมื่อเสียชีวิต"),
            Component.text("§9--- ยุทธภัณฑ์โบราณ (Relics) ---\n\n• Rift Pickaxe: ขุด 3x3 บล็อกพร้อมกัน (ย่อตัวเพื่อขุด 1 บล็อก)\n• Smelter Pickaxe: หลอมแร่เป็นแท่งโลหะอัตโนมัติขณะขุด\n• Storm Bow: ชาร์จยิงผ่าสายฟ้าต่อเนื่องใส่ศัตรู 3 ตัว\n• Nova Bow: ชาร์จเต็มยิงระเบิดพลังงานหมู่รุนแรง\n• Rift Blade: คลิกขวาวาร์ปพุ่งไปข้างหน้า 8 บล็อก\n• Eternal Aegis: คลิกขวาเปิดบาเรียอมตะ 3 วิ"),
            Component.text("§9--- คทาเวทมนตร์โบราณ ---\n\nคทามีทั้งหมด 15 ธาตุ ได้รับแกน Magic Core จาก Evergarden Vault\n\nสูตรคราฟต์:\nวาง Magic Core ตรงกลางโต๊ะคราฟต์ ล้อมด้วย Netherite Ingot หรือ Nether Star 8 ชิ้น\n\nถือคทาแล้วคลิกขวาเพื่อร่ายเวทมนตร์\nมีเอฟเฟกต์ต่อเนื่องอัตโนมัติ ไม่เสียมานาเพิ่ม ตรวจสอบมานาด้วย /magic mana"),
            Component.text("§9--- คทา: มหาเวทต้องห้าม ---\n\n§5✦ Shulker Levitation §4[MYTHIC 0.5%]§r\n(มานา 95 / คูลดาวน์ 35 วิ)\nคทาต้องห้ามระดับตำนาน เรียกพายุสายฟ้าและมังกรคำราม ดึงศัตรูในระยะ 22 บล็อกลอยขึ้นฟ้าพร้อมดูดเลือด\n\nเมื่อสิ้นสุดจะระเบิด Singularity 120 ดาเมจผลักกระเด็น และเปลี่ยนพื้นเป็น Sculk Corruption ศัตรูที่เหยียบจะติด Wither III นาน 15 วิ"),
            Component.text("§9--- คทา: สายฟ้าและน้ำแข็ง ---\n\n• Lightning Strike (มานา 60 / คูลดาวน์ 8 วิ):\nผ่าสายฟ้า 60 ดาเมจ + สโลว์ จากนั้นอีก 0.5 วิ จะระเบิดคลื่นไฟฟ้าสถิตซ้ำระลอกสอง 30 ดาเมจ + ตาบอดและมึนงง\n\n• Frost Nova (มานา 50 / คูลดาวน์ 12 วิ):\nระเบิดไอเย็นแช่แข็งศัตรูรอบตัว 7 บล็อก ขยับไม่ได้ แล้วระเบิดสะเก็ดน้ำแข็ง 35 ดาเมจผลักศัตรูกระเด็น"),
            Component.text("§9--- คทา: อุกกาบาตและมังกร ---\n\n• Meteor Strike (มานา 90 / คูลดาวน์ 20 วิ):\nเรียกห่าฝนอุกกาบาตยักษ์ 3 ลูกถล่มจากฟ้า ระเบิด 90 ดาเมจและจุดไฟเผาศัตรู\n\n• Dragon's Breath (มานา 75 / คูลดาวน์ 18 วิ):\nพ่นเพลิงมังกรโบราณ 30 ดาเมจต่อวินาที พร้อมหมอกพิษกัดกร่อน Wither และ Weakness"),
            Component.text("§9--- คทา: หลุมดำและวิเธอร์ ---\n\n• Void Pull (มานา 65 / คูลดาวน์ 14 วิ):\nยิงบอลมิติดูดรวบศัตรูเข้าจุดศูนย์กลาง ตรึงขา แล้วระเบิดขอบฟ้า 45 ดาเมจผลักลอยขึ้นฟ้า\n\n• Wither Ray (มานา 85 / คูลดาวน์ 12 วิ):\nยิงกะโหลกวิเธอร์ต่อเนื่อง 6 ลูกรัวๆ ปิดท้ายด้วยกะโหลกชาร์จพลังระเบิดแรง 60 ดาเมจ + ติด Wither II"),
            Component.text("§9--- คทา: พฤกษาและกำแพงศิลา ---\n\n• Nature's Bloom (มานา 70 / คูลดาวน์ 25 วิ):\nลบล้างดีบัฟ ฮีลทั้งปาร์ตี้ พร้อมมอบ Regen IV, Absorption V, Speed II และหนามแทงศัตรู\n\n• Earth Wall (มานา 40 / คูลดาวน์ 10 วิ):\nยกกำแพงหินลึกหนา 2 ชั้น กว้าง 7 สูง 4 บล็อก กันลูกธนูและเวทมนตร์ 100% พร้อมคลื่นผลักศัตรู"),
            Component.text("§9--- คทา: พริบตาและสปอร์พิษ ---\n\n• Shadow Step (มานา 45 / คูลดาวน์ 6 วิ):\nวาร์ปทะลุกำแพง 12 บล็อก ทิ้งควันตาบอด ระเบิดเงา 25 ดาเมจ และได้รับ Speed II + ล่องหน\n\n• Poison Spores (มานา 45 / คูลดาวน์ 10 วิ):\nยิงสปอร์พิษแตกตัว Poison II และแตกหน่อสปอร์ย่อยระเบิดซ้ำอีก 3 ทิศทาง"),
            Component.text("§9--- คทา: เกราะเหล็กและกาลเวลา ---\n\n• Iron Armor (มานา 60 / คูลดาวน์ 35 วิ):\nสวมเกราะเหล็ก ผลักศัตรูรอบตัว และรับ Resistance IV, Fire Resis, Strength II นาน 1 นาที\n\n• Time Dilation (มานา 80 / คูลดาวน์ 25 วิ):\nโดมเวลา ลูกธนูช้าลง ศัตรูติด Slowness VII ส่วนเพื่อนร่วมทีมได้รับ Speed และ Haste"),
            Component.text("§9--- คทา: กลืนวิญญาณและพรางกาย ---\n\n• Soul Drain (มานา 70 / คูลดาวน์ 16 วิ):\nลำแสงดูดเลือดศัตรูมาฮีลตัวเอง แล้วระเบิด Soul Nova 35 ดาเมจ ฮีลเพื่อนร่วมทีม 15 หน่วย\n\n• Invisibility Shroud (มานา 50 / คูลดาวน์ 30 วิ):\nม่านหมอกล่องหนสมบูรณ์แบบ มอนสเตอร์มองไม่เห็น และการโจมตีเปิดตัวเพิ่มดาเมจ +50"),
            Component.text("§9--- อัตราสุ่ม Evergarden Vault ---\n\n• 0.5% : Scroll of Eternity (ไม่มีวันพัง)\n• 0.5% : Core of Levitation (คทาระดับตำนาน)\n• 25% : Limit Break Scrolls (+1 ทลายขีดจำกัด)\n• 20% : Unique Enchants (มนตรา 22 สกิล)\n• 14% : Magic Cores 14 ธาตุ (แบบละ 1%)\n• 10% : ยุทธภัณฑ์โบราณ 6 ชนิด\n• 30% : วัตถุดิบ Dust, Shards, หินซ่อม, น้ำยา")
        ));
        book.setItemMeta(meta);
        return book;
    }

    public Relic type(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(type,PersistentDataType.STRING);
        try { Relic r=Relic.valueOf(id==null?"":id);return item.getType()==r.material?r:null; } catch(IllegalArgumentException e){return null;}
    }
    public boolean immune(Player p) {
        return p.getPersistentDataContainer().getOrDefault(shieldUntil,PersistentDataType.LONG,0L)>System.currentTimeMillis();
    }
    private boolean ready(Player p,Relic relic) {
        long end=p.getPersistentDataContainer().getOrDefault(plugin.key("cd_"+relic.id()),PersistentDataType.LONG,0L);
        long left=end-System.currentTimeMillis();
        if(left<=0)return true;
        p.sendActionBar(Component.text("คูลดาวน์ " + ((left+999)/1000) + " วินาที",NamedTextColor.GRAY));return false;
    }
    private void cooldown(Player p,Relic r,int seconds) {
        p.getPersistentDataContainer().set(plugin.key("cd_"+r.id()),PersistentDataType.LONG,System.currentTimeMillis()+seconds*1000L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPrepareCraft(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        CraftingInventory inv = e.getInventory();
        ItemStack[] matrix = inv.getMatrix();

        int totalItems = 0;
        int keyShards = 0;
        int astralDust = 0;
        int repairStones = 0;
        int amethystShards = 0;
        int glassBottles = 0;
        int scrollCount = 0;
        int damagedEquipCount = 0;
        ItemStack singleScroll = null;
        ItemStack singleEquip = null;
        ItemStack singleDamagedEquip = null;

        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            totalItems++;
            if (isKeyShard(it)) {
                keyShards++;
            } else if (isAstralDust(it)) {
                astralDust++;
            } else if (isRepairStone(it)) {
                repairStones++;
            } else if (isScrollEternity(it) || getLimitBreakType(it) != null || getUniqueEnchant(it) != null) {
                scrollCount++;
                singleScroll = it;
            } else if (it.getType() == Material.AMETHYST_SHARD) {
                amethystShards++;
            } else if (it.getType() == Material.GLASS_BOTTLE) {
                glassBottles++;
            } else if (it.getType().getMaxDurability() > 0) {
                singleEquip = it;
                if (it.hasItemMeta() && it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.getDamage() > 0) {
                    damagedEquipCount++;
                    singleDamagedEquip = it;
                }
            }
        }

        // 1. Evergarden Key: exactly 4 Key Shards and nothing else
        if (keyShards == 4 && totalItems == 4) {
            inv.setResult(createVoidKey());
            return;
        }

        // 2. Vault Repair Stone: exactly 4 Astral Dust + 1 Amethyst Shard and nothing else
        if (astralDust == 4 && amethystShards == 1 && totalItems == 5) {
            inv.setResult(createRepairStone(1));
            return;
        }

        // 3. Void Walker Elixir: exactly 1 Astral Dust + 1 Glass Bottle and nothing else
        if (astralDust == 1 && glassBottles == 1 && totalItems == 2) {
            inv.setResult(createVoidElixir(1));
            return;
        }

        // 4. Bedrock Crafting Table Repair: 1 Repair Stone + 1 Damaged Item
        if (repairStones == 1 && damagedEquipCount == 1 && totalItems == 2) {
            org.bukkit.inventory.meta.Damageable dmg = (org.bukkit.inventory.meta.Damageable) singleDamagedEquip.getItemMeta();
            if (dmg.getDamage() > 0) {
                ItemStack result = singleDamagedEquip.clone();
                org.bukkit.inventory.meta.Damageable resDmg = (org.bukkit.inventory.meta.Damageable) result.getItemMeta();
                resDmg.setDamage(Math.max(0, resDmg.getDamage() - 500));
                result.setItemMeta(resDmg);
                inv.setResult(result);
                return;
            }
        }

        // 5. Bedrock Scrolls in Crafting Table: 1 Scroll + 1 Target Equipment
        if (scrollCount == 1 && singleEquip != null && totalItems == 2) {
            ItemStack result = evaluateScrollCraft(singleScroll, singleEquip);
            if (result != null) {
                inv.setResult(result);
                return;
            }
        }

        // 6. Block custom items from being consumed in vanilla recipes
        if (keyShards > 0 || astralDust > 0 || repairStones > 0 || scrollCount > 0) {
            inv.setResult(null);
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCraftItem(org.bukkit.event.inventory.CraftItemEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        CraftingInventory inv = e.getInventory();
        ItemStack result = inv.getResult();
        if (result == null || result.getType().isAir()) return;

        ItemStack[] matrix = inv.getMatrix();
        int totalItems = 0;
        int keyShards = 0;
        int astralDust = 0;
        int repairStones = 0;
        int scrollCount = 0;
        int damagedEquipCount = 0;

        for (ItemStack it : matrix) {
            if (it == null || it.getType().isAir()) continue;
            totalItems++;
            if (isKeyShard(it)) keyShards++;
            else if (isAstralDust(it)) astralDust++;
            else if (isRepairStone(it)) repairStones++;
            else if (isScrollEternity(it) || getLimitBreakType(it) != null || getUniqueEnchant(it) != null) scrollCount++;
            else if (it.hasItemMeta() && it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg && dmg.getDamage() > 0) damagedEquipCount++;
        }

        if (keyShards == 4 && totalItems == 4) {
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.0f, 1.2f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 10, 0.3, 0.3, 0.3, 0.05);
            p.sendActionBar(Component.text("✦ ประกอบ Evergarden Key สำเร็จ!", NamedTextColor.LIGHT_PURPLE));
            return;
        }

        if (repairStones == 1 && damagedEquipCount == 1 && totalItems == 2) {
            p.playSound(p.getLocation(), Sound.BLOCK_GRINDSTONE_USE, 1.0f, 1.2f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, p.getLocation().add(0, 1, 0), 15, 0.3, 0.3, 0.3, 0.05);
            p.sendActionBar(Component.text("✦ ศิลาฟื้นฟูมิติ ซ่อมแซมความทนทาน 500 หน่วย!", NamedTextColor.GREEN));
            return;
        }

        if (scrollCount == 1 && totalItems == 2) {
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 1.0f, 1.25f);
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.35f);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1.2, 0), 25, 0.35, 0.35, 0.35, 0.1);
            p.sendActionBar(Component.text("✦ ปลุกเสกมนตราผ่านโต๊ะคราฟต์สำเร็จ!", NamedTextColor.GREEN));
            return;
        }
    }

    public void triggerRiftBladeWarp(Player p) {
        if(p.getGameMode()==GameMode.SPECTATOR||!ready(p,Relic.RIFT_BLADE)||immune(p))return;
        Location start=p.getLocation(),target=null;
        Vector direction=start.getDirection();
        double distance=plugin.integer("relics.blink.distance",8,2,12);
        for(double d=0.5;d<=distance;d+=0.5) {
            Location next=start.clone().add(direction.clone().multiply(d));
            if(!p.getWorld().getWorldBorder().isInside(next))break;
            if(!p.getWorld().isChunkLoaded(next.getBlockX()>>4,next.getBlockZ()>>4))break;
            BoundingBox body=BoundingBox.of(next.clone().add(-0.31,0,-0.31),next.clone().add(0.31,1.85,0.31));
            boolean collision=false;
            for(int x=(int)Math.floor(body.getMinX());x<=Math.floor(body.getMaxX());x++)
                for(int y=(int)Math.floor(body.getMinY());y<=Math.floor(body.getMaxY());y++)
                    for(int z=(int)Math.floor(body.getMinZ());z<=Math.floor(body.getMaxZ());z++)
                        if(!p.getWorld().getBlockAt(x,y,z).isPassable())collision=true;
            if(collision)break;target=next;
        }
        if(target!=null&&start.distanceSquared(target)>=1&&p.teleport(target,PlayerTeleportEvent.TeleportCause.PLUGIN)) {
            cooldown(p,Relic.RIFT_BLADE,plugin.integer("relics.blink.cooldown-seconds",8,2,120));
            p.setFallDistance(0);p.playSound(target,Sound.ENTITY_ENDERMAN_TELEPORT,0.7f,0.7f);
            p.spawnParticle(Particle.REVERSE_PORTAL,target.clone().add(0,1,0),12,0.3,0.5,0.3,0.03);
            p.sendActionBar(Component.text("✦ กรีดมิติวาร์ปพริบตา!", NamedTextColor.AQUA));
        }
    }

    public void triggerAegis(Player p) {
        if(p.getGameMode()==GameMode.SPECTATOR||!ready(p,Relic.ETERNAL_AEGIS))return;
        int seconds=plugin.integer("relics.shield.duration-seconds",3,1,5);
        p.getPersistentDataContainer().set(shieldUntil,PersistentDataType.LONG,System.currentTimeMillis()+seconds*1000L);
        cooldown(p,Relic.ETERNAL_AEGIS,plugin.integer("relics.shield.cooldown-seconds",75,10,600));
        p.playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,0.6f,0.7f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1,0),20,0.4,0.4,0.4,0.05);
        p.sendActionBar(Component.text("โล่แห่งความอมตะ · "+seconds+" วินาที",NamedTextColor.GOLD));
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        Player p=e.getPlayer();
        ItemStack held=e.getItem();
        if(held==null||held.getType().isAir())return;

        // 1. Vault Repair Stone interaction (support right click, sneak + left click / swing for Bedrock)
        if(isRepairStone(held)) {
            if(e.getAction().isRightClick() || (p.isSneaking() && (e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_AIR||e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK))) {
                e.setCancelled(true);
                useRepairStone(p,held);
                return;
            }
        }

        Relic r=type(held);
        if(r==Relic.ETERNAL_AEGIS) {
            if(e.getAction().isRightClick()) {
                e.setCancelled(true);
                triggerAegis(p);
                return;
            }
        } else if(r==Relic.RIFT_BLADE) {
            if(e.getAction().isRightClick() || (p.isSneaking() && (e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_AIR||e.getAction()==org.bukkit.event.block.Action.LEFT_CLICK_BLOCK))) {
                e.setCancelled(true);
                triggerRiftBladeWarp(p);
                return;
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onSwapHand(PlayerSwapHandItemsEvent e) {
        Player p=e.getPlayer();
        if(type(e.getMainHandItem())==Relic.RIFT_BLADE) {
            e.setCancelled(true);
            triggerRiftBladeWarp(p);
        }
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onSneak(PlayerToggleSneakEvent e) {
        if(!e.isSneaking())return;
        Player p=e.getPlayer();
        ItemStack main=p.getInventory().getItemInMainHand();
        ItemStack off=p.getInventory().getItemInOffHand();
        if(type(main)==Relic.ETERNAL_AEGIS||type(off)==Relic.ETERNAL_AEGIS) {
            triggerAegis(p);
        }
    }

    private void useRepairStone(Player p,ItemStack item) {
        ItemStack best=null;
        int maxDamage=0;
        List<ItemStack> candidates=new ArrayList<>();
        if(p.getInventory().getArmorContents()!=null) {
            candidates.addAll(Arrays.asList(p.getInventory().getArmorContents()));
        }
        candidates.add(p.getInventory().getItemInOffHand());
        candidates.add(p.getInventory().getItemInMainHand());
        for(ItemStack it:candidates) {
            if(it!=null&&it.hasItemMeta()&&it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable dmg) {
                if(dmg.getDamage()>maxDamage&&!it.equals(item)) {
                    maxDamage=dmg.getDamage();
                    best=it;
                }
            }
        }
        if(best==null||maxDamage<=0) {
            p.playSound(p.getLocation(),Sound.ENTITY_VILLAGER_NO,0.8f,1.0f);
            p.sendActionBar(Component.text("⚠ อุปกรณ์และชุดเกราะของคุณไม่ได้รับความเสียหาย",NamedTextColor.YELLOW));
            return;
        }
        org.bukkit.inventory.meta.Damageable dmg=(org.bukkit.inventory.meta.Damageable)best.getItemMeta();
        int repaired=Math.min(500,dmg.getDamage());
        dmg.setDamage(dmg.getDamage()-repaired);
        best.setItemMeta(dmg);

        item.subtract(1);
        p.playSound(p.getLocation(),Sound.BLOCK_GRINDSTONE_USE,1.0f,1.2f);
        p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER,p.getLocation().add(0,1,0),15,0.3,0.3,0.3,0.05);
        p.sendActionBar(Component.text("✦ ศิลาฟื้นฟูมิติ ซ่อมแซมความทนทาน "+repaired+" หน่วย!",NamedTextColor.GREEN));
    }

    @EventHandler(priority=EventPriority.HIGH,ignoreCancelled=true)
    public void onConsume(PlayerItemConsumeEvent e) {
        if(isVoidElixir(e.getItem())) {
            Player p=e.getPlayer();
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED,20*180,1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.JUMP_BOOST,20*180,1));
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,20*180,0));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,20*30,0));
            p.playSound(p.getLocation(),Sound.BLOCK_AMETHYST_BLOCK_CHIME,1.0f,1.4f);
            p.getWorld().spawnParticle(Particle.PORTAL,p.getLocation().add(0,1,0),30,0.5,0.5,0.5,0.1);
            p.sendActionBar(Component.text("✦ พลังแห่งเดินเวหาตื่นขึ้น! (Speed II + Jump II + Slow Falling 3 นาที)",NamedTextColor.LIGHT_PURPLE));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void shieldDamage(EntityDamageEvent e) {if(e.getEntity() instanceof Player p&&immune(p))e.setCancelled(true);}
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void shieldAttack(EntityDamageByEntityEvent e) {
        Player p=e.getDamager() instanceof Player direct?direct:e.getDamager() instanceof Projectile pr&&pr.getShooter() instanceof Player shooter?shooter:null;
        if(p!=null&&immune(p))e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void shoot(EntityShootBowEvent e) {
        if(!(e.getEntity() instanceof Player p))return;
        if(immune(p)){e.setCancelled(true);return;}
        Relic r=type(e.getBow());
        if((r!=Relic.NOVA_BOW&&r!=Relic.STORM_BOW)||e.getForce()<0.95)return;
        if(!ready(p,r)||arrows.size()>=plugin.integer("performance.max-special-projectiles",64,1,256)) {e.setCancelled(true);return;}
        Entity arrow=e.getProjectile();
        arrow.getPersistentDataContainer().set(shot,PersistentDataType.STRING,r.name());
        arrow.getPersistentDataContainer().set(shotOwner,PersistentDataType.STRING,p.getUniqueId().toString());
        arrows.put(arrow.getUniqueId(),System.currentTimeMillis()+10000);
        cooldown(p,r,plugin.integer("relics.bow.cooldown-seconds",5,1,60));
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void hit(ProjectileHitEvent e) {
        Projectile arrow=e.getEntity();String id=arrow.getPersistentDataContainer().get(shot,PersistentDataType.STRING);
        if(id==null)return;
        arrow.getPersistentDataContainer().remove(shot);arrows.remove(arrow.getUniqueId());
        if(!(arrow.getShooter() instanceof Player p)||!p.isOnline()||p.getWorld()!=arrow.getWorld()||immune(p)){arrow.remove();return;}
        Location at=arrow.getLocation();arrow.remove();
        List<LivingEntity> targets=new ArrayList<>(at.getNearbyLivingEntities(5.0));
        targets.removeIf(t->t.equals(p)||t instanceof ArmorStand||t.isDead()||t instanceof Player&&!plugin.getConfig().getBoolean("relics.allow-pvp",false));
        targets.sort(Comparator.comparingDouble(t->t.getLocation().distanceSquared(at)));
        if(id.equals(Relic.NOVA_BOW.name())) {
            p.getWorld().spawnParticle(Particle.EXPLOSION,at,1);
            p.getWorld().playSound(at,Sound.ENTITY_GENERIC_EXPLODE,0.7f,1.3f);
            for(LivingEntity t:targets.subList(0,Math.min(8,targets.size())))
                if(clear(at,t.getEyeLocation()))t.damage(Math.max(2,16-at.distance(t.getLocation())*3),p);
        } else {
            int n=0;
            for(LivingEntity t:targets) {
                if(!clear(at,t.getEyeLocation()))continue;
                t.getWorld().strikeLightningEffect(t.getLocation());t.damage(14-n*3,p);
                if(++n==3)break;
            }
        }
    }
    private boolean clear(Location a,Location b) {
        Vector v=b.toVector().subtract(a.toVector());double d=v.length();
        return d<0.3||a.getWorld().rayTraceBlocks(a,v,d-0.2,FluidCollisionMode.NEVER,true)==null;
    }
    @EventHandler(priority=EventPriority.MONITOR,ignoreCancelled=true)
    public void mine(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.isSneaking()||p.getGameMode()!=GameMode.SURVIVAL||mining.contains(p.getUniqueId())||type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)return;
        Block origin=e.getBlock();if(!Tag.MINEABLE_PICKAXE.isTagged(origin.getType()))return;
        RayTraceResult ray=p.rayTraceBlocks(6);
        org.bukkit.block.BlockFace face=ray==null?org.bukkit.block.BlockFace.UP:ray.getHitBlockFace();
        if(face==null)return;
        UUID id=p.getUniqueId();ItemStack held=p.getInventory().getItemInMainHand();
        Bukkit.getScheduler().runTask(plugin,()->{
            if(!p.isOnline()||p.getWorld()!=origin.getWorld()||type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)return;
            if(!p.getInventory().getItemInMainHand().equals(held))return;
            mining.add(id);
            try {
                for(int a=-1;a<=1;a++)for(int b=-1;b<=1;b++) {
                    if(a==0&&b==0)continue;
                    Block block=face.getModY()!=0?origin.getRelative(a,0,b):face.getModX()!=0?origin.getRelative(0,a,b):origin.getRelative(a,b,0);
                    if(!block.getWorld().isChunkLoaded(block.getX()>>4,block.getZ()>>4)||p.getLocation().distanceSquared(block.getLocation())>64)continue;
                    if(!Tag.MINEABLE_PICKAXE.isTagged(block.getType())||block.getType().getHardness()<0||block.getState() instanceof org.bukkit.inventory.InventoryHolder)continue;
                    if(type(p.getInventory().getItemInMainHand())!=Relic.RIFT_PICKAXE)break;
                    p.breakBlock(block);
                }
            } finally {mining.remove(id);}
        });
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void smeltMine(BlockBreakEvent e) {
        Player p=e.getPlayer();
        if(p.getGameMode()!=GameMode.SURVIVAL)return;
        ItemStack held=p.getInventory().getItemInMainHand();
        if(type(held)!=Relic.SMELTER_PICKAXE)return;
        Block block=e.getBlock();
        ItemStack smelted=smeltResult(block.getType());
        if(smelted==null)return;
        e.setDropItems(false);
        e.setExpToDrop(Math.max(e.getExpToDrop(),1));
        Location loc=block.getLocation().add(0.5,0.5,0.5);
        block.getWorld().dropItemNaturally(loc,smelted);
        block.getWorld().spawnParticle(Particle.FLAME,loc,6,0.2,0.2,0.2,0.02);
        block.getWorld().spawnParticle(Particle.SMOKE,loc,3,0.1,0.1,0.1,0.01);
        p.playSound(loc,Sound.BLOCK_FURNACE_FIRE_CRACKLE,0.6f,1.2f);
    }
    private ItemStack smeltResult(Material m) {
        return switch(m) {
            case SAND, RED_SAND -> new ItemStack(Material.GLASS,1);
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON_BLOCK -> new ItemStack(Material.IRON_INGOT,m==Material.RAW_IRON_BLOCK?9:1);
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD_BLOCK -> new ItemStack(Material.GOLD_INGOT,m==Material.RAW_GOLD_BLOCK?9:1);
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER_BLOCK -> new ItemStack(Material.COPPER_INGOT,m==Material.RAW_COPPER_BLOCK?9:1);
            case ANCIENT_DEBRIS -> new ItemStack(Material.NETHERITE_SCRAP,1);
            case COBBLESTONE -> new ItemStack(Material.STONE,1);
            case COBBLED_DEEPSLATE -> new ItemStack(Material.DEEPSLATE,1);
            case STONE -> new ItemStack(Material.SMOOTH_STONE,1);
            case CLAY -> new ItemStack(Material.TERRACOTTA,1);
            case NETHERRACK -> new ItemStack(Material.NETHER_BRICK,1);
            case WET_SPONGE -> new ItemStack(Material.SPONGE,1);
            case CACTUS -> new ItemStack(Material.GREEN_DYE,1);
            case OAK_LOG, SPRUCE_LOG, BIRCH_LOG, JUNGLE_LOG, ACACIA_LOG, DARK_OAK_LOG, MANGROVE_LOG, CHERRY_LOG -> new ItemStack(Material.CHARCOAL,1);
            default -> null;
        };
    }
    public void tick() {
        long now=System.currentTimeMillis();
        arrows.entrySet().removeIf(e->{Entity a=Bukkit.getEntity(e.getKey());if(a==null)return true;if(now>=e.getValue()){a.remove();return true;}return false;});
    }
    public void close(){for(UUID id:arrows.keySet()){Entity e=Bukkit.getEntity(id);if(e!=null)e.remove();}arrows.clear();}
}
