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
            // Clean up old legacy keys
            Bukkit.removeRecipe(new NamespacedKey(plugin,s.id()));
            Bukkit.removeRecipe(new NamespacedKey(plugin,s.id()+"_bottom"));
            Bukkit.removeRecipe(new NamespacedKey(plugin,s.id()+"_ni_c"));
            Bukkit.removeRecipe(new NamespacedKey(plugin,s.id()+"_ni_b"));
            Bukkit.removeRecipe(new NamespacedKey(plugin,s.id()+"_ns_c"));
            Bukkit.removeRecipe(new NamespacedKey(plugin,s.id()+"_ns_b"));

            // 1. Center Core + Netherite Ingot (NNN / NCN / NNN)
            NamespacedKey keyNiCenter=new NamespacedKey(plugin,s.id()+"_ni_c");
            ShapedRecipe recipeNiCenter=new ShapedRecipe(keyNiCenter,create(s));
            recipeNiCenter.shape("NNN","NCN","NNN");
            recipeNiCenter.setIngredient('N',Material.NETHERITE_INGOT);
            recipeNiCenter.setIngredient('C',CORE_BASE);
            Bukkit.addRecipe(recipeNiCenter);
            recipes.put(keyNiCenter,s);

            // 2. Bottom-Center Core + Netherite Ingot (NNN / NNN / NCN)
            NamespacedKey keyNiBottom=new NamespacedKey(plugin,s.id()+"_ni_b");
            ShapedRecipe recipeNiBottom=new ShapedRecipe(keyNiBottom,create(s));
            recipeNiBottom.shape("NNN","NNN","NCN");
            recipeNiBottom.setIngredient('N',Material.NETHERITE_INGOT);
            recipeNiBottom.setIngredient('C',CORE_BASE);
            Bukkit.addRecipe(recipeNiBottom);
            recipes.put(keyNiBottom,s);

            // 3. Center Core + Nether Star (NNN / NCN / NNN)
            NamespacedKey keyNsCenter=new NamespacedKey(plugin,s.id()+"_ns_c");
            ShapedRecipe recipeNsCenter=new ShapedRecipe(keyNsCenter,create(s));
            recipeNsCenter.shape("NNN","NCN","NNN");
            recipeNsCenter.setIngredient('N',Material.NETHER_STAR);
            recipeNsCenter.setIngredient('C',CORE_BASE);
            Bukkit.addRecipe(recipeNsCenter);
            recipes.put(keyNsCenter,s);

            // 4. Bottom-Center Core + Nether Star (NNN / NNN / NCN)
            NamespacedKey keyNsBottom=new NamespacedKey(plugin,s.id()+"_ns_b");
            ShapedRecipe recipeNsBottom=new ShapedRecipe(keyNsBottom,create(s));
            recipeNsBottom.shape("NNN","NNN","NCN");
            recipeNsBottom.setIngredient('N',Material.NETHER_STAR);
            recipeNsBottom.setIngredient('C',CORE_BASE);
            Bukkit.addRecipe(recipeNsBottom);
            recipes.put(keyNsBottom,s);
        }
        plugin.getLogger().info("[advance-magic] Registered " + recipes.size() + " wand crafting recipes.");
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

        Spell foundCore = null;
        int coreCount = 0;
        int ingredientCount = 0;
        int coreSlot = -1;

        for(int i=0;i<9;i++) {
            ItemStack item = matrix[i];
            if(item==null||item.getType().isAir()) return null;
            Spell s = coreSpell(item);
            if(s != null) {
                foundCore = s;
                coreCount++;
                coreSlot = i;
            } else if(item.getType() == Material.NETHERITE_INGOT || item.getType() == Material.NETHER_STAR) {
                if(item.getAmount() >= 1) {
                    ingredientCount++;
                }
            } else {
                return null;
            }
        }

        if(coreCount == 1 && ingredientCount == 8) {
            // Support Core in Center (Slot 4) or Bottom-Center (Slot 7)
            if(coreSlot == 4 || coreSlot == 7) {
                return foundCore;
            }
        }
        return null;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void prepare(PrepareItemCraftEvent e) {
        CraftingInventory inv=e.getInventory();
        ItemStack[] matrix=inv.getMatrix();
        Spell s=craftingSpell(matrix);
        HumanEntity viewer = e.getView().getPlayer();

        if(s!=null) {
            if(canCraft(viewer)) {
                ItemStack wand = create(s);
                inv.setResult(wand);
                plugin.getLogger().info("[Craft-Prepare] Set result to " + s.title + " Wand for " + viewer.getName());
            } else {
                inv.setResult(null);
                plugin.getLogger().warning("[Craft-Prepare] Denied wand craft for " + viewer.getName() + " (no permission)");
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
        HumanEntity who = e.getWhoClicked();

        // If not a wand craft, prevent vanilla from consuming wand ingredients
        if(expected==null) {
            if(ours(e.getRecipe())||containsCoreOrHeart(inv.getMatrix())) {
                e.setCancelled(true);
            }
            return;
        }

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
        plugin.getLogger().info("[Craft-Success] " + who.getName() + " crafted " + expected.title + " Wand!");

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
        plugin.getLogger().info("[ResultClick-Success] " + p.getName() + " clicked result for " + expected.title + " Wand!");
        Bukkit.getScheduler().runTask(plugin, p::updateInventory);
    }

    @EventHandler(priority=EventPriority.HIGH)
    public void onCraftingTableInteract(PlayerInteractEvent e) {
        if(e.getAction()!=Action.RIGHT_CLICK_BLOCK) return;
        if(e.getClickedBlock()==null||e.getClickedBlock().getType()!=Material.CRAFTING_TABLE) return;
        Player p=e.getPlayer();
        ItemStack hand=p.getInventory().getItemInMainHand();
        Spell coreSpell=coreSpell(hand);
        if(coreSpell==null) return;

        if(!canCraft(p)) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED+"คุณไม่มีสิทธิ์ในการสร้างคทาเวทมนตร์");
            return;
        }

        // Check if player has 8 Netherite Ingots or 8 Nether Stars in inventory
        int netheriteCount=0;
        int netherStarCount=0;
        for(ItemStack it:p.getInventory().getStorageContents()) {
            if(it==null) continue;
            if(it.getType()==Material.NETHERITE_INGOT) netheriteCount+=it.getAmount();
            else if(it.getType()==Material.NETHER_STAR) netherStarCount+=it.getAmount();
        }

        Material chosenMaterial=null;
        if(netheriteCount>=8) {
            chosenMaterial=Material.NETHERITE_INGOT;
        } else if(netherStarCount>=8) {
            chosenMaterial=Material.NETHER_STAR;
        }

        if(chosenMaterial==null) {
            p.sendMessage(ChatColor.RED+"ต้องการ Netherite Ingot หรือ Nether Star อย่างน้อย 8 อันในการประกอบคทา");
            return;
        }

        // Cancel opening the crafting table UI
        e.setCancelled(true);

        // Consume 8 materials from inventory
        int needed=8;
        ItemStack[] storage=p.getInventory().getStorageContents();
        for(int slot=0;slot<storage.length;slot++) {
            ItemStack it=storage[slot];
            if(it!=null&&it.getType()==chosenMaterial) {
                if(it.getAmount()<=needed) {
                    needed-=it.getAmount();
                    storage[slot]=null;
                } else {
                    it.setAmount(it.getAmount()-needed);
                    needed=0;
                }
                if(needed<=0) break;
            }
        }
        p.getInventory().setStorageContents(storage);

        // Consume 1 core from main hand
        hand.setAmount(hand.getAmount()-1);
        p.getInventory().setItemInMainHand(hand.getAmount()>0?hand:null);

        // Create and give wand
        ItemStack wand=create(coreSpell);
        var leftover=p.getInventory().addItem(wand);
        if(!leftover.isEmpty()) {
            leftover.values().forEach(drop -> p.getWorld().dropItemNaturally(p.getLocation(),drop));
        }

        p.playSound(p.getLocation(),Sound.BLOCK_BEACON_POWER_SELECT,1.0f,1.2f);
        p.playSound(p.getLocation(),Sound.UI_TOAST_CHALLENGE_COMPLETE,0.7f,1.4f);
        p.spawnParticle(Particle.TOTEM_OF_UNDYING,p.getLocation().add(0,1.2,0),30,0.4,0.4,0.4,0.1);
        p.sendMessage(ChatColor.GOLD+"✦ ประกอบ "+ChatColor.LIGHT_PURPLE+coreSpell.title+" Wand"+ChatColor.GOLD+" สำเร็จผ่านโต๊ะคราฟต์!");
        p.sendActionBar(net.kyori.adventure.text.Component.text("✦ ประกอบ "+coreSpell.title+" Wand สำเร็จ!",net.kyori.adventure.text.format.NamedTextColor.GOLD));
        plugin.getLogger().info("[DirectCraft-Success] "+p.getName()+" assembled "+coreSpell.title+" Wand via Crafting Table right-click!");
        p.updateInventory();
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

    public void close() {
        recipes.keySet().forEach(Bukkit::removeRecipe);
        recipes.clear();
    }
}
