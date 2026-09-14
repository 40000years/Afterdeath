package com.example.advancemagic.item;

import com.example.advancemagic.spell.Spell;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.entity.HumanEntity;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.inventory.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
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
        if(spell==Spell.SHULKER_LEVITATION) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE+"✦ "+ChatColor.GOLD+"Core of Levitation "+ChatColor.RED+"[MYTHIC]");
            meta.setLore(List.of(
                ChatColor.GOLD+"[ระดับตำนานสูงสุด · MYTHIC 0.5%]",
                ChatColor.DARK_PURPLE+"§k||§r "+ChatColor.LIGHT_PURPLE+"Forbidden Dragon Heart "+ChatColor.DARK_PURPLE+"§k||",
                ChatColor.GRAY+"ใช้คราฟต์: "+ChatColor.LIGHT_PURPLE+"Shulker Levitation Wand",
                ChatColor.YELLOW+"สูตร: 8 Netherite Ingots หรือ Nether Stars + แกนนี้",
                ChatColor.RED+"✦ อัตราดรอป 0.5% ใน Evergarden Vault [สุดยอดของแรร์]"
            ));
        } else {
            meta.setDisplayName(ChatColor.GOLD+"✦ Core of "+coreTitle(spell));
            meta.setLore(List.of(
                ChatColor.AQUA+"Ancient Magic Core (แกนเวทมนตร์โบราณ)",
                ChatColor.GRAY+"ใช้คราฟต์: "+ChatColor.LIGHT_PURPLE+spell.title+" Wand",
                ChatColor.YELLOW+"สูตร: 8 Netherite Ingots หรือ Nether Stars + แกนนี้",
                ChatColor.DARK_AQUA+"หาได้จาก: Evergarden Vault"
            ));
        }
        var modelData=meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("advance_magic:core_"+spell.id()));
        meta.setCustomModelDataComponent(modelData);
        meta.getPersistentDataContainer().set(coreKey,PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","core"),PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","magic_core"),PersistentDataType.STRING,spell.id());
        item.setItemMeta(meta);
        return item;
    }
    public Spell coreSpell(ItemStack item) {
        if(item==null||item.getType()!=CORE_BASE||!item.hasItemMeta())return null;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        String id=pdc.get(coreKey,PersistentDataType.STRING);
        String underscore=pdc.get(new NamespacedKey("advance_magic","core"),PersistentDataType.STRING);
        String legacy=pdc.get(new NamespacedKey("voidscape","magic_core"),PersistentDataType.STRING);
        String evergarden=pdc.get(new NamespacedKey("evergarden","magic_core"),PersistentDataType.STRING);
        if(id==null) id=underscore;
        if(id==null) id=evergarden;
        if(id==null) id=legacy;
        if(id!=null) {
            Spell s=Spell.parse(id);
            if(s!=null) return s;
        }
        // Fallback: Check display name (supports Bedrock clients or items without PDC)
        if(item.getItemMeta().hasDisplayName()) {
            String name=ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            for(Spell s:Spell.values()) {
                if(name.contains("core of " + coreTitle(s).toLowerCase(Locale.ROOT))
                    || name.contains(s.title.toLowerCase(Locale.ROOT))
                    || name.contains(s.id().replace('_',' '))) {
                    return s;
                }
            }
            if(name.contains("dragon heart")||name.contains("shulker")||name.contains("levitation")) {
                return Spell.SHULKER_LEVITATION;
            }
        }
        return null;
    }
    public ItemStack create(Spell spell) {
        ItemStack item=new ItemStack(BASE);
        var meta=item.getItemMeta();
        if(spell==Spell.SHULKER_LEVITATION) {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE+"✦ "+ChatColor.GOLD+"Shulker Levitation Wand "+ChatColor.RED+"[MYTHIC]");
            meta.setLore(List.of(
                ChatColor.GOLD+"[ระดับตำนานสูงสุด · MYTHIC 0.5%]",
                ChatColor.DARK_PURPLE+"§k||§r "+ChatColor.LIGHT_PURPLE+"Ancient Dragon Singularity "+ChatColor.DARK_PURPLE+"§k||",
                ChatColor.GRAY+"คลิกขวาเพื่อปลดปล่อยหายนะมิติบรรพกาล",
                ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+spell.cooldown+"s",
                ChatColor.YELLOW+"⚡ พายุฟ้าผ่า · มังกรจุติ · มหาหลุมดำกลืนมิติ · ดินแดน Sculk Wither III"
            ));
        } else {
            meta.setDisplayName(ChatColor.LIGHT_PURPLE+"✦ "+spell.title+" Wand");
            meta.setLore(List.of(ChatColor.GRAY+"คลิกขวาเพื่อร่ายเวทมนตร์",ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+spell.cooldown+"s"));
        }
        var lore=new ArrayList<>(meta.getLore());
        lore.add(ChatColor.GREEN+"เอฟเฟกต์ต่อเนื่องอัตโนมัติ · ไม่เสียมานาเพิ่ม");
        meta.setLore(lore);
        meta.setUnbreakable(true);
        meta.addItemFlags(ItemFlag.HIDE_UNBREAKABLE);
        // The vanilla model is a safe fallback when a client has no resource pack.
        var modelData=meta.getCustomModelDataComponent();
        modelData.setStrings(List.of("advance_magic:"+spell.id()));meta.setCustomModelDataComponent(modelData);
        meta.getPersistentDataContainer().set(wandKey,PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(new NamespacedKey("advance_magic","wand"),PersistentDataType.STRING,spell.id());
        meta.getPersistentDataContainer().set(castsKey,PersistentDataType.INTEGER,0);
        item.setItemMeta(meta);return item;
    }
    public Spell spell(ItemStack item) {
        if(item==null||item.getType()!=BASE||!item.hasItemMeta())return null;
        var pdc=item.getItemMeta().getPersistentDataContainer();
        String id=pdc.get(wandKey,PersistentDataType.STRING);
        if(id==null) id=pdc.get(new NamespacedKey("advance_magic","wand"),PersistentDataType.STRING);
        if(id!=null) {
            Spell s=Spell.parse(id);
            if(s!=null) return s;
        }
        if(item.getItemMeta().hasDisplayName()) {
            String name=ChatColor.stripColor(item.getItemMeta().getDisplayName()).toLowerCase(Locale.ROOT);
            for(Spell s:Spell.values()) {
                if(name.contains(s.title.toLowerCase(Locale.ROOT))||name.contains(s.id().replace('_',' '))) {
                    return s;
                }
            }
            if(name.contains("shulker")||name.contains("levitation")) {
                return Spell.SHULKER_LEVITATION;
            }
        }
        return null;
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
        if(spell==Spell.SHULKER_LEVITATION) {
            meta.setLore(List.of(
                ChatColor.DARK_GRAY+"[ระดับตำนาน - MYTHIC]",
                ChatColor.DARK_PURPLE+""+ChatColor.MAGIC+"Ancient Dragon Singularity",
                ChatColor.GRAY+"คลิกขวาเพื่อปลดปล่อยหายนะมิติบรรพกาล",
                ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+String.format(Locale.ROOT,"%.1f",effectiveCd)+"s"+ChatColor.DARK_GRAY+" (Base: "+spell.cooldown+"s)",
                ChatColor.LIGHT_PURPLE+"Mastery: "+ChatColor.WHITE+count+" casts"+(reduction>0?ChatColor.YELLOW+" [-"+String.format(Locale.ROOT,"%.0f",reduction)+"s CD]":""),
                ChatColor.RED+"⚡ พายุฟ้าผ่า · มังกรจุติ · มหาหลุมดำกลืนมิติ · ดินแดน Sculk Wither III"
            ));
        } else {
            meta.setLore(List.of(
                ChatColor.GRAY+"Right-click to cast",
                ChatColor.AQUA+"Mana: "+spell.mana+" / Cooldown: "+String.format(Locale.ROOT,"%.1f",effectiveCd)+"s"+ChatColor.DARK_GRAY+" (Base: "+spell.cooldown+"s)",
                ChatColor.LIGHT_PURPLE+"Mastery: "+ChatColor.WHITE+count+" casts"+(reduction>0?ChatColor.YELLOW+" [-"+String.format(Locale.ROOT,"%.0f",reduction)+"s CD]":"")
            ));
        }
        var lore=new ArrayList<>(meta.getLore());
        lore.add(ChatColor.GREEN+"เอฟเฟกต์ต่อเนื่องอัตโนมัติ · ไม่เสียมานาเพิ่ม");
        meta.setLore(lore);
        item.setItemMeta(meta);
        return count;
    }
    public void register() {
        for(Spell s:Spell.values()) {
            NamespacedKey keyCenter=new NamespacedKey(plugin,s.id());
            NamespacedKey keyBottom=new NamespacedKey(plugin,s.id()+"_bottom");
            Bukkit.removeRecipe(keyCenter);
            Bukkit.removeRecipe(keyBottom);

            // 1. Center Core (NNN / NCN / NNN)
            ShapedRecipe recipeCenter=new ShapedRecipe(keyCenter,create(s));
            recipeCenter.shape("NNN","NCN","NNN");
            recipeCenter.setIngredient('N',new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT,Material.NETHER_STAR));
            recipeCenter.setIngredient('C',new RecipeChoice.MaterialChoice(CORE_BASE));
            if(!Bukkit.addRecipe(recipeCenter))throw new IllegalStateException("Duplicate recipe: "+keyCenter);
            recipes.put(keyCenter,s);

            // 2. Bottom-Center Core (NNN / NNN / NCN)
            ShapedRecipe recipeBottom=new ShapedRecipe(keyBottom,create(s));
            recipeBottom.shape("NNN","NNN","NCN");
            recipeBottom.setIngredient('N',new RecipeChoice.MaterialChoice(Material.NETHERITE_INGOT,Material.NETHER_STAR));
            recipeBottom.setIngredient('C',new RecipeChoice.MaterialChoice(CORE_BASE));
            if(!Bukkit.addRecipe(recipeBottom))throw new IllegalStateException("Duplicate recipe: "+keyBottom);
            recipes.put(keyBottom,s);
        }
    }
    public void discover(Player p) { if(canCraft(p))p.discoverRecipes(recipes.keySet()); }
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

    public boolean containsCoreOrHeart(ItemStack[] matrix) {
        if(matrix==null) return false;
        for(ItemStack it : matrix) {
            if(it!=null && (it.getType()==CORE_BASE || coreSpell(it)!=null)) return true;
        }
        return false;
    }

    public boolean canCraft(HumanEntity player) {
        if(player==null) return true;
        if(player.isOp()) return true;
        return !player.isPermissionSet("advance-magic.craft") || player.hasPermission("advance-magic.craft");
    }

    public Spell craftingSpell(ItemStack[] matrix) {
        if(matrix==null||matrix.length!=9)return null;

        // Support Core in Center (Slot 4) or Bottom-Center (Slot 7)
        int coreSlot = -1;
        Spell core = coreSpell(matrix[4]);
        if(core != null) {
            coreSlot = 4;
        } else {
            core = coreSpell(matrix[7]);
            if(core != null) {
                coreSlot = 7;
            }
        }
        if(core == null) return null;

        // All other 8 slots must be Netherite Ingot or Nether Star
        for(int i=0;i<9;i++) {
            if(i == coreSlot) continue;
            ItemStack ing=matrix[i];
            if(ing==null||ing.getAmount()<1||(ing.getType()!=Material.NETHERITE_INGOT&&ing.getType()!=Material.NETHER_STAR)){
                return null;
            }
        }
        return core;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        CraftingInventory inv=e.getInventory();
        ItemStack[] matrix=inv.getMatrix();
        Spell s=craftingSpell(matrix);
        if(s!=null) {
            if(canCraft(e.getView().getPlayer())) {
                inv.setResult(create(s));
            } else {
                inv.setResult(null);
            }
            return;
        }

        // If the recipe was matched by vanilla as an advance-magic recipe or matrix contains a core/heart-of-the-sea
        // but it's not a valid wand recipe: clear the result!
        if(ours(e.getRecipe())||containsCoreOrHeart(matrix)) {
            inv.setResult(null);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void craft(CraftItemEvent e) {
        CraftingInventory inv=e.getInventory();
        Spell expected=craftingSpell(inv.getMatrix());

        // If not a wand craft, prevent vanilla from consuming wand ingredients
        if(expected==null) {
            if(ours(e.getRecipe())||containsCoreOrHeart(inv.getMatrix())) {
                e.setCancelled(true);
            }
            return;
        }

        HumanEntity who = e.getWhoClicked();
        if(!canCraft(who)) {
            e.setCancelled(true);
            if(who instanceof Player p) {
                p.sendMessage(ChatColor.RED+"คุณไม่มีสิทธิ์ในการสร้างคทาเวทมนตร์");
            }
            return;
        }

        // Authoritative output override:
        // Solves Bedrock Edition client recipe collision where Bedrock client matched another spell's recipe ID
        ItemStack wand=create(expected);
        e.setCurrentItem(wand);
        inv.setResult(wand);

        if(who instanceof Player p) {
            p.playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,1.0f,1.2f);
            p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.7f,1.4f);
            p.spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1.2,0),25,0.35,0.35,0.35,0.1);
            p.sendActionBar(net.kyori.adventure.text.Component.text("✦ ประกอบ " + expected.title + " Wand สำเร็จ!", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            Bukkit.getScheduler().runTask(plugin, p::updateInventory);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onResultClick(InventoryClickEvent e) {
        if(e instanceof CraftItemEvent) return; // Handled authoritatively by craft()
        if(!(e.getWhoClicked() instanceof Player p)) return;
        if(!(e.getInventory() instanceof CraftingInventory inv)) return;
        if(e.getSlotType()!=InventoryType.SlotType.RESULT) return;

        Spell expected=craftingSpell(inv.getMatrix());
        if(expected==null) return;

        if(!canCraft(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED+"คุณไม่มีสิทธิ์ในการสร้างคทาเวทมนตร์");
            return;
        }

        ItemStack wand=create(expected);
        e.setCurrentItem(wand);
        inv.setResult(wand);
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void automatic(org.bukkit.event.block.CrafterCraftEvent e) {
        if(!(e.getBlock().getState() instanceof org.bukkit.block.Crafter crafter)) return;
        Spell s=craftingSpell(crafter.getInventory().getContents());
        if(s!=null) {
            e.setResult(create(s));
        } else if(ours(e.getRecipe())||containsCoreOrHeart(crafter.getInventory().getContents())) {
            e.setCancelled(true);
        }
    }
    public void close() { recipes.keySet().forEach(Bukkit::removeRecipe);recipes.clear(); }
}
