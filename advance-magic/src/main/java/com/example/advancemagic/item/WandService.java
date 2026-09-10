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
    private final NamespacedKey wandKey;
    private final NamespacedKey castsKey;
    private final Map<NamespacedKey,Spell> recipes=new HashMap<>();
    private final Plugin plugin;
    public WandService(Plugin plugin) {
        this.plugin=plugin;
        wandKey=new NamespacedKey(plugin,"wand");
        castsKey=new NamespacedKey(plugin,"casts");
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
            recipe.shape("SSS","SCS","SSS");
            recipe.setIngredient('S',new RecipeChoice.ExactChoice(new ItemStack(Material.NETHER_STAR)));
            recipe.setIngredient('C',new RecipeChoice.ExactChoice(new ItemStack(s.core)));
            if(!Bukkit.addRecipe(recipe))throw new IllegalStateException("Duplicate recipe: "+key);
            recipes.put(key,s);
        }
    }
    public void discover(Player p) { if(p.hasPermission("advance-magic.craft"))p.discoverRecipes(recipes.keySet()); }
    public boolean migrate(ItemStack item) {
        Spell spell=spell(item);if(spell==null)return false;
        var meta=item.getItemMeta();var data=meta.getCustomModelDataComponent();
        String key="advance_magic:"+spell.id();
        if(!meta.hasItemModel()&&data.getStrings().equals(List.of(key)))return false;
        meta.setItemModel(null);data.setStrings(List.of(key));meta.setCustomModelDataComponent(data);
        item.setItemMeta(meta);return true;
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
    @EventHandler public void prepare(PrepareItemCraftEvent e) {
        if(ours(e.getRecipe())&&e.getView().getPlayer() instanceof Player p&&!p.hasPermission("advance-magic.craft"))e.getInventory().setResult(null);
    }
    @EventHandler(ignoreCancelled=true) public void craft(CraftItemEvent e) {
        if(ours(e.getRecipe())&&!e.getWhoClicked().hasPermission("advance-magic.craft"))e.setCancelled(true);
    }
    public void close() { recipes.keySet().forEach(Bukkit::removeRecipe);recipes.clear(); }
}
