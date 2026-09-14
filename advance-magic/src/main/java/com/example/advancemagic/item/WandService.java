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

    public boolean containsCoreOrHeart(ItemStack[] matrix) {
        if(matrix==null) return false;
        for(ItemStack it : matrix) {
            if(it!=null && (it.getType()==CORE_BASE || coreSpell(it)!=null)) return true;
        }
        return false;
    }

    public Spell craftingSpell(ItemStack[] matrix) {
        if(matrix==null||matrix.length!=9)return null;

        // 1. Standard shaped 3x3 check (slot 4 center)
        Spell center=coreSpell(matrix[4]);
        if(center!=null) {
            boolean valid=true;
            for(int i=0;i<9;i++)if(i!=4) {
                ItemStack ing=matrix[i];
                if(ing==null||ing.getAmount()<1||(ing.getType()!=Material.NETHERITE_INGOT&&ing.getType()!=Material.NETHER_STAR)){valid=false;break;}
            }
            if(valid) return center;
        }

        // 2. Shapeless in 3x3 crafting grid (1 core + 8 netherite/stars in any arrangement)
        int netheriteCount=0;
        Spell foundCore=null;
        for(ItemStack ing:matrix) {
            if(ing==null||ing.getType().isAir()) continue;
            Spell s=coreSpell(ing);
            if(s!=null) {
                if(foundCore!=null) return null; // More than 1 core
                foundCore=s;
            } else if(ing.getType()==Material.NETHERITE_INGOT||ing.getType()==Material.NETHER_STAR) {
                netheriteCount++;
            } else {
                return null; // Unknown ingredient
            }
        }
        if(foundCore!=null&&netheriteCount==8) {
            return foundCore;
        }
        return null;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        CraftingInventory inv=e.getInventory();
        ItemStack[] matrix=inv.getMatrix();
        Spell s=craftingSpell(matrix);
        if(s!=null) {
            if(e.getView().getPlayer().hasPermission("advance-magic.craft")) {
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
        ItemStack clicked=e.getCurrentItem();
        ItemStack result=inv.getResult();
        Spell clickedSpell=spell(clicked);
        if(clickedSpell==null) clickedSpell=spell(result);
        if(clickedSpell==null&&!ours(e.getRecipe())) return;

        if(!e.getWhoClicked().hasPermission("advance-magic.craft")) {
            e.setCancelled(true);
            return;
        }

        Spell expected=craftingSpell(inv.getMatrix());
        if(expected==null||(clickedSpell!=null&&clickedSpell!=expected)) {
            e.setCancelled(true);
            return;
        }

        if(e.getWhoClicked() instanceof Player p) {
            p.playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,1.0f,1.2f);
            p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.7f,1.4f);
            p.spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1.2,0),25,0.35,0.35,0.35,0.1);
            p.sendActionBar(net.kyori.adventure.text.Component.text("✦ ประกอบ " + expected.title + " Wand สำเร็จ!", net.kyori.adventure.text.format.NamedTextColor.GOLD));
            Bukkit.getScheduler().runTask(plugin, p::updateInventory);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST,ignoreCancelled=true)
    public void onCraftClick(InventoryClickEvent e) {
        if(!(e.getWhoClicked() instanceof Player p)) return;
        if(!(e.getInventory() instanceof CraftingInventory inv)) return;
        if(e.getSlotType()!=InventoryType.SlotType.RESULT) return;

        ItemStack result=inv.getResult();
        if(result==null||result.getType().isAir()) return;
        Spell s=spell(result);
        if(s==null) return;

        if(!p.hasPermission("advance-magic.craft")) {
            e.setCancelled(true);
            return;
        }

        Spell expected=craftingSpell(inv.getMatrix());
        if(expected==null||expected!=s) {
            e.setCancelled(true);
            return;
        }
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onCoreInteract(PlayerInteractEvent e) {
        if(e.getAction()!=Action.RIGHT_CLICK_AIR&&e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        Player p=e.getPlayer();
        ItemStack handItem=e.getItem();
        if(handItem==null||handItem.getType()!=CORE_BASE) return;
        Spell spell=coreSpell(handItem);
        if(spell==null) return;

        if(!p.hasPermission("advance-magic.craft")) {
            p.sendMessage(ChatColor.RED+"คุณไม่มีสิทธิ์ในการสร้างคทาเวทมนตร์");
            return;
        }

        // Count Netherite Ingots and Nether Stars in inventory
        int netheriteCount=0;
        for(ItemStack it : p.getInventory().getContents()) {
            if(it!=null&&(it.getType()==Material.NETHERITE_INGOT||it.getType()==Material.NETHER_STAR)) {
                netheriteCount+=it.getAmount();
            }
        }

        if(netheriteCount<8) {
            p.sendActionBar(net.kyori.adventure.text.Component.text(
                "✦ ต้องการ Netherite Ingot หรือ Nether Star 8 ชิ้น (มี: "+netheriteCount+"/8)",
                net.kyori.adventure.text.format.NamedTextColor.YELLOW
            ));
            return;
        }

        // Consume 8 Netherite / Nether Stars
        int remainingToConsume=8;
        ItemStack[] contents=p.getInventory().getContents();
        for(int i=0;i<contents.length;i++) {
            ItemStack it=contents[i];
            if(it!=null&&(it.getType()==Material.NETHERITE_INGOT||it.getType()==Material.NETHER_STAR)) {
                if(it.getAmount()<=remainingToConsume) {
                    remainingToConsume-=it.getAmount();
                    contents[i]=null;
                } else {
                    it.setAmount(it.getAmount()-remainingToConsume);
                    remainingToConsume=0;
                    break;
                }
            }
        }
        p.getInventory().setContents(contents);

        // Consume 1 Core from hand
        if(handItem.getAmount()>1) {
            handItem.setAmount(handItem.getAmount()-1);
        } else {
            if(e.getHand()==EquipmentSlot.HAND) p.getInventory().setItemInMainHand(null);
            else p.getInventory().setItemInOffHand(null);
        }

        // Give wand
        ItemStack wand=create(spell);
        var leftover=p.getInventory().addItem(wand);
        if(!leftover.isEmpty()) {
            leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(),drop));
        }

        p.playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,1.0f,1.2f);
        p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.8f,1.3f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1.2,0),30,0.4,0.4,0.4,0.1);
        p.sendActionBar(net.kyori.adventure.text.Component.text(
            "✦ หลอมรวมแกนเวทมนตร์สำเร็จ! ได้รับ " + spell.title + " Wand",
            net.kyori.adventure.text.format.NamedTextColor.GOLD
        ));
        p.sendMessage(ChatColor.GOLD+"✦ [Advance Magic] หลอมรวมแกนเวทมนตร์ด้วย Netherite 8 ชิ้น สำเร็จ! ได้รับ "+ChatColor.LIGHT_PURPLE+spell.title+" Wand");
        p.updateInventory();
        e.setCancelled(true);
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
