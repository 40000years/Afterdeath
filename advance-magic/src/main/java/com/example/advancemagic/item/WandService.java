package com.example.advancemagic.item;

import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class WandService implements Listener {
    public static final Material BASE=Material.CARROT_ON_A_STICK;
    public static final Material CORE_BASE=Material.HEART_OF_THE_SEA;
    private final NamespacedKey wandKey;
    private final NamespacedKey castsKey;
    private final NamespacedKey coreKey;
    private final Map<NamespacedKey,Spell> recipes=new HashMap<>();
    private final Plugin plugin;
    public WandService(Plugin plugin) {
        this.plugin=plugin;
        wandKey=new NamespacedKey(plugin,"wand");
        castsKey=new NamespacedKey(plugin,"casts");
        coreKey=new NamespacedKey(plugin,"core");
    }
    public static String coreTitle(Spell spell) {
        return switch(spell) {
            case LIGHTNING_STRIKE -> "Lightning";
            case FROST_NOVA -> "Frost";
            case SHADOW_STEP -> "Shadows";
            case NATURES_BLOOM -> "Nature";
            case EARTH_WALL -> "Earth";
            case DRAGONS_BREATH -> "Dragon";
            case VOID_PULL -> "the Void";
            case INVISIBILITY_SHROUD -> "Invisibility";
            case POISON_SPORES -> "Poison";
            case WITHER_RAY -> "Wither";
            case SHULKER_LEVITATION -> "Levitation";
            case METEOR_STRIKE -> "Meteor";
            case IRON_ARMOR -> "Iron";
            case TIME_DILATION -> "Time";
            case SOUL_DRAIN -> "Souls";
        };
    }
    public ItemStack createCore(Spell spell) {
        ItemStack item=new ItemStack(CORE_BASE);
        var meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.GOLD+"✦ Core of "+coreTitle(spell));
        meta.setLore(List.of(
            ChatColor.GRAY+"Ancient Magic Core (แกนเวทมนตร์โบราณ)",
            ChatColor.DARK_GRAY+"Used to craft: "+ChatColor.LIGHT_PURPLE+spell.title+" Wand",
            ChatColor.YELLOW+"Recipe: 8 Netherite Ingots / Nether Stars + this Core",
            ChatColor.DARK_PURPLE+"Obtained from Evergarden Vault"
        ));
        var modelData=meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("advance_magic:core_"+spell.id()));
        meta.setCustomModelDataComponent(modelData);
        meta.getPersistentDataContainer().set(coreKey,PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","magic_core"),PersistentDataType.STRING,spell.id());
        item.setItemMeta(meta);
        return item;
    }
    public Spell coreSpell(ItemStack item) {
        if(item==null||item.getType()!=CORE_BASE||!item.hasItemMeta())return null;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        String id=pdc.get(coreKey,PersistentDataType.STRING);
        String legacy=pdc.get(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING);
        String evergarden=pdc.get(new NamespacedKey("evergarden","magic_core"),PersistentDataType.STRING);
        if(id!=null&&legacy!=null&&!id.equals(legacy))return null;
        if(id!=null&&evergarden!=null&&!id.equals(evergarden))return null;
        if(id==null) id=evergarden!=null?evergarden:legacy;
        return id==null?null:Spell.parse(id);
    }
    public ItemStack create(Spell spell) {
        ItemStack item=new ItemStack(BASE);
        var meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+spell.title+" Wand");
        meta.setLore(List.of(ChatColor.GRAY+"Right-click to cast",ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+spell.cooldown+"s"));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        // The vanilla model is a safe fallback when a client has no resource pack.
        var modelData=meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("advance_magic:"+spell.id()));meta.setCustomModelDataComponent(modelData);
        meta.getPersistentDataContainer().set(wandKey,PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(castsKey,PersistentDataType.INTEGER,0);
        item.setItemMeta(meta);return item;
    }
    public Spell spell(ItemStack item) {
        if(item==null||item.getType()!=BASE||!item.hasItemMeta())return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(wandKey,PersistentDataType.STRING);
        return id==null?null:Spell.parse(id);
    }
    public int casts(ItemStack item) {
        if(item==null||!item.hasItemMeta())return 0;
        return item.getItemMeta().getPersistentDataContainer().getOrDefault(castsKey,PersistentDataType.INTEGER,0);
    }
    public double getEffectiveCooldown(ItemStack item,Spell spell) {
        if(spell==null)return 1.0;
        int count=casts(item);
        double reduction=(count/5)*5.0;
        double minCooldown=Math.max(1.0,spell.cooldown*0.2);
        return Math.max(minCooldown,(double)spell.cooldown-reduction);
    }
    public int recordCast(ItemStack item,Spell spell) {
        if(item==null||!item.hasItemMeta()||spell==null)return 0;
        int count=casts(item)+1;
        var meta=item.getItemMeta();
        meta.getPersistentDataContainer().set(castsKey,PersistentDataType.INTEGER,count);
        double effectiveCd=Math.max(Math.max(1.0,spell.cooldown*0.2),(double)spell.cooldown-(count/5)*5.0);
        double reduction=(count/5)*5.0;
        meta.setLore(List.of(
            ChatColor.GRAY+"Right-click to cast",
            ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+String.format(Locale.ROOT,"%.1f",effectiveCd)+"s"+ChatColor.DARK_GRAY+" (Base: "+spell.cooldown+"s)",
            ChatColor.LIGHT_PURPLE+"Mastery: "+ChatColor.WHITE+count+" casts"+(reduction>0?ChatColor.YELLOW+" [-"+String.format(Locale.ROOT,"%.0f",reduction)+"s CD]":"")
        ));
        item.setItemMeta(meta);
        return count;
    }
    public void register() {
        for(Spell s:Spell.values()) {
            NamespacedKey key=new NamespacedKey(plugin,s.id());
            Bukkit.removeRecipe(key);
            ShapedRecipe recipe=new ShapedRecipe(key,create(s));
            recipe.shape("NNN","NCN","NNN");
            recipe.setIngredient('N',new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT,Material.NETHER_STAR));
            // Match material first; authoritative PDC validation below supports renamed/old vault cores.
            recipe.setIngredient('C',new RecipeChoice.MaterialChoice(CORE_BASE));
            if(!Bukkit.addRecipe(recipe))throw new IllegalStateException("Duplicate recipe: "+key);
            recipes.put(key,s);
        }
    }
    public void discover(Player p) { if(p.hasPermission("advance-magic.craft"))p.discoverRecipes(recipes.keySet()); }
    public boolean migrate(ItemStack item) {
        Spell spell=spell(item);
        if(spell!=null) {
            var meta=item.getItemMeta();var data=meta.getCustomModelDataComponent();
            String key="advance_magic:"+spell.id();
            if(!meta.hasItemModel()&&data.getStrings().equals(List.of(key)))return false;
            meta.setItemModel(null);data.setStrings(List.of(key));meta.setCustomModelDataComponent(data);
            item.setItemMeta(meta);return true;
        }
        Spell core=coreSpell(item);
        if(core!=null) {
            var meta=item.getItemMeta();var data=meta.getCustomModelDataComponent();
            String key="advance_magic:core_"+core.id();
            if(!meta.hasItemModel()&&data.getStrings().equals(List.of(key)))return false;
            meta.setItemModel(null);data.setStrings(List.of(key));meta.setCustomModelDataComponent(data);
            item.setItemMeta(meta);return true;
        }
        return false;
    }

    public void migrate(Inventory inventory) {
        for(int slot=0;slot<inventory.getSize();slot++) {
            ItemStack item=inventory.getItem(slot);if(migrate(item))inventory.setItem(slot,item);
        }
    }
    public void migrateEntity(org.bukkit.entity.Entity entity) {
        if(entity instanceof org.bukkit.entity.Item dropped) {ItemStack item=dropped.getItemStack();if(migrate(item))dropped.setItemStack(item);}
        if(entity instanceof org.bukkit.entity.ItemFrame frame) {ItemStack item=frame.getItem();if(migrate(item))frame.setItem(item);}
    }
    @EventHandler(priority=EventPriority.LOWEST) public void open(InventoryOpenEvent event){migrate(event.getInventory());migrate(event.getPlayer().getInventory());}
    @EventHandler(priority=EventPriority.LOWEST) public void pickup(EntityPickupItemEvent event){migrateEntity(event.getItem());}
    @EventHandler(priority=EventPriority.LOWEST) public void spawn(ItemSpawnEvent event){migrateEntity(event.getEntity());}
    @EventHandler public void load(EntitiesLoadEvent event){event.getEntities().forEach(this::migrateEntity);}
    private boolean ours(Recipe recipe) { return recipe instanceof Keyed k&&recipes.containsKey(k.getKey()); }
    public Spell craftingSpell(ItemStack[] matrix) {
        if(matrix==null||matrix.length!=9)return null;
        Spell s=coreSpell(matrix[4]);
        if(s==null)return null;
        for(int i=0;i<9;i++)if(i!=4) {
            ItemStack ing=matrix[i];
            if(ing==null||ing.getAmount()<1||(ing.getType()!=Material.NETHERITE_INGOT&&ing.getType()!=Material.NETHER_STAR))return null;
        }
        return s;
    }
    @EventHandler(priority=EventPriority.HIGHEST) public void prepare(PrepareItemCraftEvent e) {
        if(!ours(e.getRecipe()))return;
        Spell s=craftingSpell(e.getInventory().getMatrix());
        e.getInventory().setResult(s!=null&&e.getView().getPlayer().hasPermission("advance-magic.craft")?create(s):null);
    }
    @EventHandler(ignoreCancelled=true) public void craft(CraftItemEvent e) {
        if(!ours(e.getRecipe())&&spell(e.getCurrentItem())==null)return;
        Spell expected=craftingSpell(e.getInventory().getMatrix());
        if(!e.getWhoClicked().hasPermission("advance-magic.craft")||expected==null||spell(e.getCurrentItem())!=expected)e.setCancelled(true);
    }
    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void automatic(org.bukkit.event.block.CrafterCraftEvent e) {
        if(!ours(e.getRecipe()))return;
        if(!(e.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)){e.setCancelled(true);return;}
        Spell s=craftingSpell(crafter.getInventory().getContents());
        if(s==null)e.setCancelled(true);else e.setResult(create(s));
    }
    public void close() { recipes.keySet().forEach(Bukkit::removeRecipe);recipes.clear(); }
}
