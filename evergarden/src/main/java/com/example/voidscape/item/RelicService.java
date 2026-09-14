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
        recipe.setIngredient('S', new RecipeChoice.ExactChoice(createKeyShard(1)));
        try { Bukkit.addRecipe(recipe); } catch (Exception ignored) {}

        // 2. Vault Repair Stone (4 Astral Dust + 1 Amethyst Shard)
        NamespacedKey repairKey = plugin.key("craft_repair_stone");
        try { Bukkit.removeRecipe(repairKey); } catch (Exception ignored) {}
        ShapedRecipe repairRecipe = new ShapedRecipe(repairKey, createRepairStone(1));
        repairRecipe.shape(" D ", "DAD", " D ");
        repairRecipe.setIngredient('D', new RecipeChoice.ExactChoice(createAstralDust(1)));
        repairRecipe.setIngredient('A', Material.AMETHYST_SHARD);
        try { Bukkit.addRecipe(repairRecipe); } catch (Exception ignored) {}

        // 3. Void Walker Elixir (1 Astral Dust + 1 Glass Bottle)
        NamespacedKey elixirKey = plugin.key("craft_void_elixir");
        try { Bukkit.removeRecipe(elixirKey); } catch (Exception ignored) {}
        ShapelessRecipe elixirRecipe = new ShapelessRecipe(elixirKey, createVoidElixir(1));
        elixirRecipe.addIngredient(new RecipeChoice.ExactChoice(createAstralDust(1)));
        elixirRecipe.addIngredient(Material.GLASS_BOTTLE);
        try { Bukkit.addRecipe(elixirRecipe); } catch (Exception ignored) {}
    }

    public ItemStack createMagicCore(MagicCore core) {
        ItemStack item=new ItemStack(Material.HEART_OF_THE_SEA);
        ItemMeta meta=item.getItemMeta();
        if(core.id().equals("shulker_levitation")) {
            meta.setDisplayName(ChatColor.BLACK+""+ChatColor.BOLD+"✦ Core of Levitation "+ChatColor.DARK_RED+"[MYTHIC]");
            meta.setLore(List.of(
                ChatColor.DARK_GRAY+"[ระดับตำนาน - MYTHIC] แกนเวทมนตร์โบราณต้องห้าม",
                ChatColor.DARK_PURPLE+""+ChatColor.MAGIC+"Forbidden Dragon Heart",
                ChatColor.DARK_GRAY+"Used to craft: "+ChatColor.BLACK+""+ChatColor.BOLD+"Shulker Levitation Wand",
                ChatColor.YELLOW+"Recipe: 8 Netherite Ingots / Nether Stars + this Core",
                ChatColor.RED+"✦ อัตราดรอป 0.5% ใน Evergarden Vault"
            ));
        } else {
            meta.setDisplayName(ChatColor.GOLD+"✦ "+core.title());
            meta.setLore(List.of(
                ChatColor.GRAY+"Ancient Magic Core (แกนเวทมนตร์โบราณ)",
                ChatColor.DARK_GRAY+"Used to craft: "+ChatColor.LIGHT_PURPLE+core.wandTitle()+" Wand",
                ChatColor.YELLOW+"Recipe: 8 Netherite Ingots / Nether Stars + this Core",
                ChatColor.DARK_PURPLE+"Obtained from Evergarden Vault"
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
        meta.displayName(Component.text("✦ คัมภีร์ศิลานิรันดร์ (Scroll of Eternity) [MYTHIC]",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false));
        meta.lore(List.of(
            Component.text("[ระดับตำนานสูงสุด · MYTHIC 0.5%]",NamedTextColor.RED).decoration(TextDecoration.ITALIC,false),
            Component.text("ลากคัมภีร์นี้ไปแตะที่อาวุธ ชุดเกราะ หรือเครื่องมือ",NamedTextColor.WHITE).decoration(TextDecoration.ITALIC,false),
            Component.text("ไอเทมนั้นจะได้รับสถานะ 'ไม่มีวันพังเสียหาย (Unbreakable 100%)'",NamedTextColor.GOLD).decoration(TextDecoration.ITALIC,false),
            Component.text("หลอดเลือดความทนทานจะหายไปถาวร",NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC,false),
            Component.text("วิธีใช้: ลากคัมภีร์ไปแตะทับไอเทมในกระเป๋า (รองรับมือถือ)",NamedTextColor.GRAY).decoration(TextDecoration.ITALIC,false)
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
        if(item==null||!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(plugin.key("key_shard"),PersistentDataType.BYTE)
            || type(item)==Relic.KEY_SHARD;
    }

    public boolean isRepairStone(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(plugin.key("repair_stone"),PersistentDataType.BYTE)
            || type(item)==Relic.REPAIR_STONE;
    }

    public boolean isVoidElixir(ItemStack item) {
        if(item==null||!item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(plugin.key("void_elixir"),PersistentDataType.BYTE)
            || type(item)==Relic.VOID_ELIXIR;
    }

    public boolean migrate(ItemStack item) {
        Relic relic=type(item);if(relic==null)return false;
        var meta=item.getItemMeta();var data=meta.getCustomModelDataComponent();String model="voidscape:"+relic.id();
        if(!meta.hasItemModel()&&data.getStrings().equals(List.of(model)))return false;
        meta.setItemModel(null);data.setStrings(List.of(model));meta.setCustomModelDataComponent(data);item.setItemMeta(meta);return true;
    }
    public void migrate(Inventory inventory){for(int i=0;i<inventory.getSize();i++){var item=inventory.getItem(i);if(migrate(item))inventory.setItem(i,item);}}
    @EventHandler public void join(PlayerJoinEvent e){migrate(e.getPlayer().getInventory());migrate(e.getPlayer().getEnderChest());}
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
            Component.text("§1§lมิติ Evergarden§r\n§8สวนลอยฟ้าแห่งความว่างเปล่า\n\n§0§lวิธีสร้างประตูมิติ:§r\n§0สร้างกรอบ §5Crying Obsidian§0 ขนาดเริ่มต้น 4×5 (ช่องใน 2×3)\n\n§0จุดไฟด้วย §6Flint & Steel§0, §cFire Charge§0 หรือ §bEye of Ender§0 ในกรอบ ประตูสีม่วงจะเปิดออกทันที\n\n§8คำสั่ง: /evergarden leave (กลับ)\n§8คำสั่ง: /evergarden locate (หาวิหาร)"),
            Component.text("§1§l3 มหาวิหารธาตุ§r\n\n§0ใน Evergarden มี 3 มหาวิหาร:\n§5• วิหารแห่งความมืด (Darkness)\n§9• วิหารแห่งดวงดาว (Astral)\n§6• วิหารแห่งกาลเวลา (Time)\n\n§0§lการท้าทาย:§r\n§0คลิกขวาที่แท่น §5Lodestone§0 กลางวิหารเพื่อเริ่มสู้ (เปิดให้ผู้เล่นทุกคน)\n§0ปราบบอส 3 เวฟเพื่อรับ §dEvergarden Key§0 นำไปเปิดกล่องสมบัติ Vault"),
            Component.text("§1§lกุญแจ & วัตถุดิบ§r\n\n§d§lEvergarden Key:§r\n§0ใช้เปิด Evergarden Vault ประจำวิหาร\n\n§5§lEvergarden Key Shard:§r\n§0เศษผลึกกุญแจ รวบรวมครบ §64 ชิ้น§0 วาง 2×2 ที่โต๊ะคราฟต์เพื่อประกอบเป็น §d1 Evergarden Key§0\n\n§b§lAstral Dust (ผงละอองดาว):§r\n§0สสารเวทมนตร์จากมอนสเตอร์ ใช้คราฟต์:\n§0• 4 Dust + 1 Amethyst = ศิลาฟื้นฟู\n§0• 1 Dust + 1 ขวดแก้ว = น้ำยาเดินเวหา"),
            Component.text("§1§lไอเทมฟื้นฟู & น้ำยา§r\n\n§a§lVault Repair Stone:§r\n§0ศิลาฟื้นฟูมิติ (คลิกขวาใช้งาน)\n§0• ซ่อมแซมความทนทาน §a500 หน่วย§0 ให้อาวุธ/เกราะที่ชำรุดมากที่สุดในตัว\n§8สูตร: 4 Astral Dust + 1 Amethyst\n\n§d§lVoid Walker Elixir:§r\n§0น้ำยาเดินเวหา ดื่มเพื่อรับผล 3 นาที:\n§b• Speed II (วิ่งไว)\n§a• Jump Boost II (กระโดดสูง)\n§f• Slow Falling (ตกช้า ไม่ตก Void)\n§8สูตร: 1 Astral Dust + 1 ขวดแก้ว"),
            Component.text("§1§lคัมภีร์เวทมนตร์§r\n\n§0§lวิธีใช้งานคัมภีร์ทุกชนิด:§r\n§0เปิดกระเป๋า หยิบคัมภีร์บนเมาส์ แล้ว§6คลิกทับอุปกรณ์เป้าหมายโดยตรง§0\n\n§4§lScroll of Eternity (0.5%):§r\n§0คัมภีร์ตำนาน ทำให้อุปกรณ์กลายเป็น §6Unbreakable (ไม่มีวันพังถาวร 100%)§0\n\n§3§lLimit Break Scrolls:§r\n§0เพิ่มเลเวลเอนแชนต์เดิม +1 ทลายขีดจำกัด (สูงสุดระดับ X) มี 6 ชนิด: Sharpness, Protection, Power, Efficiency, Fortune, Looting"),
            Component.text("§1§lมนตรา: ธนู & อุปกรณ์ขุด§r\n\n§9§lธนู (Bows):§r\n§0• Colossus Slayer: ยิงแรงตาม %HP บอส\n§0• Ricochet: ศรชิ่ง 3 เป้าหมาย + สายฟ้า\n§0• Kinetic Grapple: ยิงปักแล้วดึงตัวพุ่งไป\n§0• Absolute Zero: แช่แข็งหยุดนิ่ง 3.5 วิ\n§0• Singularity: หลุมดำดูดมอน 3 วินาที\n§0• Meteor Arrow: ศรเรียกอุกกาบาตยักษ์\n\n§6§lเครื่องมือขุด (Tools):§r\n§0• Seismic Slam: ขุดระเบิดโพรง 3×3×1\n§0• Vein Smelter: ขุดทั้งสายแร่ + เผาแท่ง\n§0• Bedrock Resonance: เรดาร์ส่องแร่ 12 บล็อก\n§0• Demeter's Scythe: เก็บ+ปลูกคืน 9×9\n§0• Timber Titan: โค่นต้นไม้ทั้งต้น\n§0• Telepathy: ของที่ขุดวาร์ปเข้าตัว 100%"),
            Component.text("§1§lมนตรา: ดาบ & ชุดเกราะ§r\n\n§c§lดาบ & อาวุธประชิด:§r\n§0• Guillotine: สังหารศัตรูเลือด <15% ทันที\n§0• Echo Strike: โอกาส 35% เงาฟันซ้ำ 100%\n§0• Blade Vortex: ปล่อยคลื่นดาบ 7 บล็อก\n§0• Soul Harvest: วิญญาณเพิ่มวิ่งไว+ดูดเลือด\n§0• Thunderlord: ตี 3 ครั้งผ่าสายฟ้าสวรรค์\n§0• Vampiric: แปลง 15% ดาเมจเป็นเลือด\n\n§2§lชุดเกราะ & อรรถประโยชน์:§r\n§0• Phoenix Rebirth: ฟื้นคืนชีพ 50% HP\n§0• Shadow Step: ย่อ 2 ครั้งวาร์ป 6 บล็อก\n§0• Titan Stance: กันกระเด็น 100% + กันบึ้ม\n§0• Soulbound: ของไม่ตกเมื่อตาย"),
            Component.text("§1§lยุทธภัณฑ์โบราณ (Relics)§r\n\n§0§lRift Pickaxe (อีเต้อแยกพิภพ):§r\n§0ขุด 3×3 บล็อกพร้อมกัน (กดย่อขุด 1 บล็อก)\n\n§0§lSmelter Pickaxe (อีเต้อหลอมเพลิง):§r\n§0หลอมบล็อก/แร่ที่ขุดเป็นแท่งโลหะอัตโนมัติ\n\n§0§lStorm Bow (ธนูพิพากษาสายฟ้า):§r\n§0ยิงผ่าสายฟ้าต่อเนื่องใส่ศัตรู 3 เป้าหมาย\n\n§0§lNova Bow (ธนูสะเก็ดดาว):§r\n§0ชาร์จเต็มยิงระเบิดพลังงานหมู่รุนแรง\n\n§0§lRift Blade (ดาบกรีดมิติ):§r\n§0คลิกขวากะพริบตาวาร์ปไปข้างหน้า 8 บล็อก\n\n§0§lEternal Aegis (โล่แห่งความอมตะ):§r\n§0คลิกขวาอมตะ 3 วิ ป้องกันดาเมจ 100%"),
            Component.text("§1§lคทาเวทมนตร์ (1/3)§r\n\n§0§lสูตรคราฟต์คทา:§r\n§0นำ §6Magic Core§0 วางตรงกลางโต๊ะคราฟต์ ล้อมด้วย §8Netherite Ingot§0 หรือ §eNether Star§0 8 ช่อง\n\n§4§lShulker Levitation [MYTHIC]:§r\n§0คทาต้องห้าม เรียกพายุฟ้าร้อง+มังกรคำราม ดึงศัตรูขึ้นฟ้าดูดเลือด ระเบิด Singularity ดาเมจ 120 + ปล่อยพื้นที่ Sculk Wither III\n\n§e§lLightning Strike:§r\n§0ผ่าสายฟ้า 60 ดาเมจ + สโลว์ พร้อมระเบิดคลื่นไฟฟ้าสถิตซ้ำระลอกสอง\n\n§b§lFrost Nova:§r\n§0แช่แข็งศัตรูรอบตัว 7 บล็อก แล้วแตกกระจายผลักกระเด็น (35 ดาเมจ)"),
            Component.text("§1§lคทาเวทมนตร์ (2/3)§r\n\n§c§lMeteor Strike:§r\n§0เรียกห่าฝนอุกกาบาตยักษ์ 3 ลูกถล่มจากฟ้า ระเบิด 90 ดาเมจ + ไฟลุกไหม้\n\n§5§lDragon's Breath:§r\n§0พ่นเพลิงมังกรโบราณ 30 ดาเมจ/วิ + ทิ้งหมอกพิษ Wither & Weakness\n\n§1§lVoid Pull:§r\n§0ยิงบอลมิติดูดรวบศัตรูเข้าจุดศูนย์กลาง ตรึงขา แล้วระเบิดขอบฟ้าผลักลอยฟ้า\n\n§8§lWither Ray:§r\n§0ยิงหัวกะโหลกวิเธอร์ต่อเนื่อง 6 ลูกรัวๆ ปิดท้ายด้วยหัวชาร์จพลังระเบิดแรง\n\n§a§lPoison Spores:§r\n§0ยิงสปอร์พิษแตกกระจาย ติด Poison II + แตกหน่อสปอร์ย่อย 3 ทิศทาง"),
            Component.text("§1§lคทาเวทมนตร์ (3/3)§r\n\n§6§lEarth Wall:§r\n§0ยกกำแพงหินลึก 2 ชั้น กันลูกธนู/เวท 100% พร้อมคลื่นแผ่นดินไหวผลักศัตรู\n\n§5§lShadow Step:§r\n§0วาร์ปทะลุกำแพง 12 บล็อก ทิ้งควันตาบอด ระเบิดเงา 25 ดาเมจ + ได้ Speed II\n\n§2§lNature's Bloom:§r\n§0ลบล้างดีบัฟ ฮีลทั้งปาร์ตี้ + มอบ Regen IV, Absorption V + หนามแทงศัตรู\n\n§7§lIron Armor:§r\n§0สวมเกราะเหล็ก ผลักศัตรูรอบตัว + Resistance IV, Fire Resis, Strength II\n\n§3§lTime Dilation:§r\n§0โดมเวลา ลูกธนูช้าลง ศัตรูติด Slowness VII ขณะที่เพื่อนได้ Speed & Haste\n\n§3§lSoul Drain:§r\n§0ลำแสงดูดเลือดศัตรูมาฮีลตัวเอง แล้วระเบิด Soul Nova ฮีลเพื่อนรอบข้าง")
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

    @EventHandler(priority=EventPriority.HIGH)
    public void interact(PlayerInteractEvent e) {
        if(!e.getAction().isRightClick())return;
        if(e.useItemInHand()==Event.Result.DENY)return;
        Player p=e.getPlayer();

        // 1. Vault Repair Stone interaction
        if(isRepairStone(e.getItem())) {
            e.setCancelled(true);
            useRepairStone(p,e.getItem());
            return;
        }

        Relic r=type(e.getItem());
        if(r!=Relic.RIFT_BLADE&&r!=Relic.ETERNAL_AEGIS)return;
        e.setCancelled(true);
        if(p.getGameMode()==GameMode.SPECTATOR||!ready(p,r))return;
        if(r==Relic.ETERNAL_AEGIS) {
            int seconds=plugin.integer("relics.shield.duration-seconds",3,1,5);
            p.getPersistentDataContainer().set(shieldUntil,PersistentDataType.LONG,System.currentTimeMillis()+seconds*1000L);
            cooldown(p,r,plugin.integer("relics.shield.cooldown-seconds",75,10,600));
            p.playSound(p.getLocation(),Sound.ITEM_TOTEM_USE,0.6f,0.7f);
            p.sendActionBar(Component.text("โล่แห่งความอมตะ · "+seconds+" วินาที",NamedTextColor.GOLD));
        } else {
            if(immune(p))return;
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
                cooldown(p,r,plugin.integer("relics.blink.cooldown-seconds",8,2,120));
                p.setFallDistance(0);p.playSound(target,Sound.ENTITY_ENDERMAN_TELEPORT,0.7f,0.7f);
                p.spawnParticle(Particle.REVERSE_PORTAL,target.clone().add(0,1,0),12,0.3,0.5,0.3,0.03);
            }
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
