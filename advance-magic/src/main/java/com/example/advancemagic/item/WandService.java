package com.example.advancemagic.item;

import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import java.util.*;

public final class WandService implements Listener {
    public static final Material BASE=Material.CARROT_ON_A_STICK;
    private final NamespacedKey wandKey;
    private final Map<NamespacedKey,Spell> recipes=new HashMap<>();
    private final Plugin plugin;
    public WandService(Plugin plugin) { this.plugin=plugin;wandKey=new NamespacedKey(plugin,"wand"); }
    public ItemStack create(Spell spell) {
        ItemStack item=new ItemStack(BASE);
        var meta=item.getItemMeta();
        meta.setDisplayName(ChatColor.LIGHT_PURPLE+spell.title+" Wand");
        meta.setLore(List.of(ChatColor.GRAY+"Right-click to cast",ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+spell.cooldown+"s"));
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        meta.setItemModel(new NamespacedKey("advance_magic",spell.id()));
        meta.getPersistentDataContainer().set(wandKey,PersistentDataType.STRING,spell.id());
        item.setItemMeta(meta);return item;
    }
    public Spell spell(ItemStack item) {
        if(item==null||item.getType()!=BASE||!item.hasItemMeta())return null;
        String id=item.getItemMeta().getPersistentDataContainer().get(wandKey,PersistentDataType.STRING);
        return id==null?null:Spell.parse(id);
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
    private boolean ours(Recipe recipe) { return recipe instanceof Keyed k&&recipes.containsKey(k.getKey()); }
    @EventHandler public void prepare(PrepareItemCraftEvent e) {
        if(ours(e.getRecipe())&&e.getView().getPlayer() instanceof Player p&&!p.hasPermission("advance-magic.craft"))e.getInventory().setResult(null);
    }
    @EventHandler(ignoreCancelled=true) public void craft(CraftItemEvent e) {
        if(ours(e.getRecipe())&&!e.getWhoClicked().hasPermission("advance-magic.craft"))e.setCancelled(true);
    }
    public void close() { recipes.keySet().forEach(Bukkit::removeRecipe);recipes.clear(); }
}
