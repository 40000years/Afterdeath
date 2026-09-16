from pathlib import Path
import shutil,subprocess,os
root=Path.cwd();out=root/'output/recheck-20260915';old=root/'output/item-fixes-20260915/server';server=out/'server';server.mkdir(exist_ok=True)
for name in ['libraries','versions','cache']:
 if not (server/name).exists():(server/name).symlink_to(old/name,target_is_directory=True)
plugins=server/'plugins';plugins.mkdir(exist_ok=True)
for mod in ['evergarden','advance-magic']:shutil.copy2(root/(mod+'.jar'),plugins/(mod+'.jar'))
for name in ['Evergarden','advance-magic']:
 d=plugins/name;d.mkdir(exist_ok=True);(d/'config.yml').write_text('resource-pack:\n  enabled: false\n  geyser:\n    auto-install: false\n')
(server/'eula.txt').write_text('eula=true\n')
(server/'server.properties').write_text('server-ip=127.0.0.1\nserver-port=25590\nonline-mode=false\nview-distance=2\nsimulation-distance=2\ngenerate-structures=false\nspawn-protection=0\n')
s=(root/'output/item-fixes-20260915/ItemFixChecks.java').read_text().replace('ItemFixChecks','RecheckChecks').replace('item-fix-result.txt','recheck-result.txt')
s=s.replace('if(protect){breakEvents++;e.setCancelled(true);}', 'if(protect && e.getBlock().getX()==2){breakEvents++;e.setCancelled(true);}')
a=s.index('        for(Spell spell:Spell.values())');b=s.index('\n    void finish()')
s=s[:a]+'''
        Bukkit.getPluginManager().registerEvents(this,this);
        actor.setOp(false);
        result("normal_cast_allowed",magic.casts().canCast(actor));
        result("normal_craft_allowed",magic.wands().canCraft(actor));
        Bukkit.dispatchCommand(actor,"evergarden crops");
        result("normal_crop_menu_opened",actor.getOpenInventory().getTopInventory().getHolder() instanceof com.example.voidscape.crop.CropShowcaseGui);
        actor.closeInventory();
        garden.cropGui().open(actor);
        var click=new InventoryClickEvent(actor.getOpenInventory(),InventoryType.SlotType.CONTAINER,45,ClickType.LEFT,InventoryAction.PICKUP_ALL);
        Bukkit.getPluginManager().callEvent(click);
        result("normal_crop_menu_no_free_seeds",actor.getInventory().isEmpty());actor.closeInventory();
        actor.getInventory().setItem(0,magic.wands().createCore(Spell.FROST_NOVA));actor.getInventory().setItem(1,new ItemStack(Material.NETHER_STAR,8));
        result("normal_menu_craft_success",magic.itemMenu().craft(actor,Spell.FROST_NOVA)==null);
        actor.getInventory().clear();
        // Neighbouring block protection denies x=2, but allows the origin at x=1.
        var listener=new UniqueAbilityListener(garden);
        var pick=new ItemStack(Material.NETHERITE_PICKAXE);var meta=pick.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","ue_"+UniqueEnchant.VEIN_SMELTER.id()),PersistentDataType.BYTE,(byte)1);pick.setItemMeta(meta);
        actor.getInventory().setItemInMainHand(pick);
        var origin=world.getBlockAt(1,100,0);var neighbour=world.getBlockAt(2,100,0);
        origin.setType(Material.DIAMOND_ORE);neighbour.setType(Material.DIAMOND_ORE);protect=true;breakEvents=0;
        listener.onBlockBreak(new BlockBreakEvent(origin,actor));
        result("BUG_vein_smelt_removes_protected_neighbour",neighbour.getType()==Material.AIR);
        result("vein_smelt_protection_events",breakEvents);
        var hoe=new ItemStack(Material.NETHERITE_HOE);meta=hoe.getItemMeta();
        meta.getPersistentDataContainer().set(new NamespacedKey("evergarden","ue_"+UniqueEnchant.DEMETER_SCYTHE.id()),PersistentDataType.BYTE,(byte)1);hoe.setItemMeta(meta);actor.getInventory().setItemInMainHand(hoe);
        for(var b:List.of(origin,neighbour)){b.getRelative(0,-1,0).setType(Material.FARMLAND);b.setType(Material.WHEAT);var age=(org.bukkit.block.data.Ageable)b.getBlockData();age.setAge(age.getMaximumAge());b.setBlockData(age);}
        breakEvents=0;listener.onBlockBreak(new BlockBreakEvent(origin,actor));
        result("BUG_scythe_harvests_protected_neighbour",((org.bukkit.block.data.Ageable)neighbour.getBlockData()).getAge()==0);
        result("scythe_protection_events",breakEvents);protect=false;
        // One tree break consumes every charge through recursive synthetic events.
        var field=garden.cropBuffs().getClass().getDeclaredField("treeFellerCharges");field.setAccessible(true);
        var charges=(Map<UUID,Integer>)field.get(garden.cropBuffs());charges.put(actor.getUniqueId(),5);
        origin.setType(Material.OAK_LOG);neighbour.setType(Material.OAK_LOG);actor.getInventory().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
        garden.cropBuffs().onOreBreak(new BlockBreakEvent(origin,actor));
        result("tree_charges_after_one_break_expected_4",charges.get(actor.getUniqueId()));
        // Two worlds with different day counters allow alternating daily drinks.
        world.setFullTime(24000L*10);garden.world().setFullTime(24000L*20);
        actor.getInventory().setItemInMainHand(new ItemStack(Material.DRAGON_BREATH,10));
        boolean d1=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        actor.teleport(garden.world().getSpawnLocation());
        boolean d2=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        actor.teleport(new Location(world,0.5,100,0.5));
        boolean d3=magic.mana().drinkDragonBreath(actor,actor.getInventory().getItemInMainHand(),EquipmentSlot.HAND);
        result("BUG_daily_drink_A_B_A_all_succeed",d1&&d2&&d3);
        // Failed casts still trigger the food buff's explosion before the mana check.
        var buffs=garden.cropBuffs();var f=buffs.getClass().getDeclaredField("omniReboundUntil");f.setAccessible(true);
        ((Map<UUID,Long>)f.get(buffs)).put(actor.getUniqueId(),System.currentTimeMillis()+60000);
        var monster=world.spawn(actor.getLocation().add(0,0,2),org.bukkit.entity.Zombie.class);monster.setAI(false);
        magic.mana().account(actor).setMana(0);double hp=monster.getHealth();
        boolean cast=magic.casts().cast(actor,Spell.FROST_NOVA,magic.wands().create(Spell.FROST_NOVA));
        result("BUG_failed_cast_damages_monster",!cast&&monster.getHealth()<hp);monster.remove();
    }
'''+s[b:]
(out/'RecheckChecks.java').write_text(s)
cp=os.pathsep.join([str(plugins/'advance-magic.jar'),str(plugins/'evergarden.jar'),*map(str,(server/'versions').rglob('*.jar')),*map(str,(server/'libraries').rglob('*.jar'))])
classes=out/'test-classes';classes.mkdir(exist_ok=True)
subprocess.run(['javac','-proc:none','-encoding','UTF-8','-cp',cp,'-d',str(classes),str(out/'RecheckChecks.java')],check=True)
(classes/'plugin.yml').write_text("name: RecheckChecks\nversion: '1.0'\nmain: RecheckChecks\napi-version: '26.2'\ndepend: [advance-magic, Evergarden]\n")
subprocess.run(['jar','--create','--file',str(plugins/'recheck-checks.jar'),'-C',str(classes),'.'],check=True)
(out/'server-classpath.txt').write_text(os.pathsep.join([*map(str,(server/'versions').rglob('*.jar')),*map(str,(server/'libraries').rglob('*.jar'))]))
(out/'.gitignore').write_text('server/\ntest-classes/\n*/classes/\nserver-classpath.txt\n')
