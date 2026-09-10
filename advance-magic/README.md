# advance-magic

Fifteen craftable wands with server-side spell logic, 100 mana, regeneration of 2 mana per second, per-player/per-spell cooldowns, and optional Java/Bedrock resource packs. No client mod, ProtocolLib or NMS dependency is used by the release plugin.

Targets Bukkit APIs available in **Spigot/Paper 1.21.11+**. Compiled to Java 21 bytecode; use the Java runtime required by your server (the workspace's Paper 26.2 runs on Java 25). Geyser translates the native entities, sounds, particles and actionbar. A Bedrock client is still needed to visually acceptance-test the pack and touch controls.

## Install

1. Copy `dist/advance-magic-1.0.0.jar` into the server's `plugins/` directory and restart.
2. Use `/magic list` to see spell IDs, costs and crafting cores. `/magic mana` shows current mana.
3. Give a test wand with `/magic give <online-player> lightning_strike` (operator permission).
4. Right-click/use the wand in either hand. A main-hand wand takes priority if both hands contain wands. On Bedrock use the normal use/interact control; wands use `CARROT_ON_A_STICK` so use input works without a client mod. Wands are unbreakable and recognized by PDC, never by their visible name.

Every wand uses a **tagged Magic Core from a Voidscape Void Vault** in the center of a 3×3 grid. Fill the eight outer slots with **Netherite Ingots or Nether Stars** (mixing is supported). The core determines the spell. Ordinary Heart of the Sea items and the old vanilla ingredient cores cannot craft wands. Renamed cores and older Voidscape-tagged cores remain compatible.

```text
Ingot / Star | Ingot / Star | Ingot / Star
Ingot / Star | Magic Core   | Ingot / Star
Ingot / Star | Ingot / Star | Ingot / Star
```

Defeat two shrine waves and the boss, collect a Void Key, and open a Void Vault once per player per shrine. Rewards: 30% Diamond Block, 30% Netherite Ingot, 20% armor trim, 10% special equipment, 10% random Magic Core. The 15 cores are equally likely within the core category (about 0.67% per specific core per vault). A vanilla Trial Key does not open this vault.

[Thai infographic](dist/advance-magic-guide-th.png) · `/magic list` · `/void guide`

Permissions: `advance-magic.cast` and `advance-magic.craft` default to everyone; `advance-magic.admin` defaults to operators. Failed targeting, blocked casts and unsafe blink destinations refund mana and do not start a cooldown. Mana and cooldown expiry times persist in player PDC across reconnects and normal restarts; death does not refill mana. Regeneration occurs while online, once every 20 server ticks. Spell durations are server ticks, while cooldowns use elapsed wall-clock time.

## Spells

Damage numbers below are health points (two health points = one heart). Costs and cooldowns exactly match the requested definitions. Damage, cloud duration and unspecified ranges have explicit defaults here.

| ID | Core | Mana / cooldown | Behavior |
|---|---|---|---|
| `lightning_strike` | Core of Lightning | 60 / 8s | Target block/entity within 30 blocks. Native lightning visual and 12 lightning damage in a 5-block sphere. No incidental vanilla lightning fire. |
| `frost_nova` | Core of Frost | 50 / 12s | 7-block wave, Slowness IV for 6s, sustained visual freeze ticks below the freeze-damage threshold. |
| `shadow_step` | Core of Shadows | 45 / 6s | Blink up to 12 blocks facing forward; may cross one thin wall. Stops before thick/second walls, checks body space, loaded chunks and world border. No pearl or teleport fall damage. Cannot cast while riding. |
| `natures_bloom` | Core of Nature | 70 / 25s | Caster and allies within 5 blocks receive Regeneration II and Absorption II for 8s. |
| `earth_wall` | Core of Earth | 40 / 10s | 5-wide, 3-high barrier 3 blocks ahead for 5s. Non-dropping FallingBlocks provide the image; server-side segment collision intercepts incoming/outgoing projectiles. Does not obstruct walking or replace terrain. Requires free space. |
| `dragons_breath` | Core of Dragon | 75 / 18s | Launch a native purple AreaEffectCloud, radius 4. Travels up to 14 blocks then lingers; 6s total lifetime, 6 magic damage each second. |
| `void_pull` | Core of the Void | 65 / 14s | Straight gravity orb; impact creates an 8-block pull field for 2s. Targets reaching the center or the final pull phase are rooted for 1.5s from the last application. |
| `invisibility_shroud` | Core of Invisibility | 50 / 30s | Full player hide for other players, including armor/held items, plus invisibility and Speed II for 10s. Attacks, projectile launches or another successful spell reveal the caster. Existing mob targets are cleared and new targeting is blocked. |
| `poison_spores` | Core of Poison | 45 / 10s | Straight projectile bursts in a 4-block sphere; Poison II and Nausea for 6s. |
| `wither_ray` | Core of Wither | 85 / 12s | Three native Wither Skulls, 6 ticks apart; each impact has a 3-block splash, 8 explosion damage and Wither II for 5s. Vanilla damage immunity frames still apply. No block destruction. |
| `shulker_levitation` | Core of Levitation | 55 / 15s | Target within 30 blocks; homing ShulkerBullet applies 4 magic impact damage and Levitation II for 4s to the actual enemy it hits. Normal falling damage remains. |
| `meteor_strike` | Core of Meteor | 90 / 20s | Ground target within 30 blocks, 1.5s warning, native LargeFireball descends from 18 blocks above. Flame particles give it a large silhouette; explosion radius 6, damage 18, 4s entity ignition and terrain fire. Does not destroy blocks. |
| `iron_armor` | Core of Iron | 60 / 35s | Resistance III and Slowness I for 8s. Reflects 30% of final, uncancelled incoming melee damage as thorns damage; cannot recursively reflect. |
| `time_dilation` | Core of Time | 80 / 25s | Fixed 6-block dome for 4s. Projectiles inside move at 20% speed; overlapping domes do not multiply the reduction. Restores motion on exit/cleanup. Enemies inside receive Slowness VI and Mining Fatigue V, refreshed while inside. |
| `soul_drain` | Core of Souls | 70 / 16s | Visible enemy within 20 blocks; 3s tether, one 8-health magic pulse each second. Heals only actual health removed, capped at maximum health. Breaks on blocked sight, range, death, disconnect or world change. |

Allies are the caster, their own tamed animals, and members of their **main scoreboard team**. Enemy effects exclude allies, armor stands and creative/spectator players. Player combat additionally respects `pvp` in this plugin's config and the world's PvP setting. Otherwise living mobs are valid enemies. AOE status spells can reach through walls; Soul Drain and initial targeted spells require sight.

Shroud uses Bukkit's plugin-scoped `hidePlayer`/`showPlayer`, which sends the hide/show packets including the entity's equipment. It also removes the hidden player from other players' tab lists. It does not remove the caster's real equipment, and revealing does not override a different plugin's hide state. The caster still sees their own first-person hand. Reference: [Spigot Player API](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/Player.html).

## Resource packs

`dist/advance-magic-java.zip` contains fifteen 32x32 wand textures and modern item model definitions, using namespace `advance_magic`. Vanilla items keep their usual appearance. Pack formats 75 through 88 cover the intended Java 1.21.11–26.2 clients; [Minecraft 1.21.11 notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11) specify format 75.

Open `dist/wand-preview.html` for a self-contained preview of all wand textures.

For Java, install the ZIP manually or host it at a direct HTTPS download URL, then set `resource-pack.url` and `resource-pack.sha1` in the plugin config using `dist/pack-hashes.json`. Restart after editing config. The plugin adds its pack with a unique UUID so it can coexist with the Voidscape pack. The default URL is blank; no pack has been uploaded or remotely deployed.

For Bedrock through Geyser:

1. Put `dist/advance-magic-bedrock.mcpack` in Geyser's `packs/` directory.
2. Put `dist/geyser-mappings.json` in Geyser's `custom_mappings/` directory as `advance-magic.json` (keep other plugins' mapping files).
3. Use a Geyser build supporting **custom item mapping format v2** and restart Geyser/the server. Mappings must register before the client joins.

The Bedrock atlas and mappings contain all fifteen `minecraft:carrot_on_a_stick` variants. Geyser needs the separate Bedrock pack; it does not convert the Java pack automatically. See the [official Geyser custom item guide](https://geysermc.org/wiki/geyser/custom-items/). Gameplay works without either pack, with vanilla wand appearance. No Bedrock behavior pack is required.

## Configuration and integrations

`config.yml` exposes PvP, the active-effect and target limits, damage defaults and meteor terrain ignition. Defaults cap active effects at 128 and targets per AOE operation at 32. Temporary entities are nonpersistent. Effects clean up on expiry, death, logout, world changes and plugin disable. Casts avoid loading new chunks. Meteor fire is a deliberate world change and follows normal fire behavior after ignition; disable `meteor.ignite-terrain` if it is unwanted.

Damage uses Bukkit's damage pipeline with the caster as the causing entity. Healing fires `EntityRegainHealthEvent`; terrain/entity ignition fires the corresponding Bukkit events. Region integrations can cancel `MagicCastEvent` before a cast or `MagicAffectEvent` before an individual target receives damage/status/movement. There is no built-in WorldGuard integration: protection plugins must handle these custom events to block non-damage spell effects.

## Source and builds

| Location | Responsibility |
|---|---|
| `AdvanceMagicPlugin.java` | Lifecycle, listeners, ticker, optional pack offer |
| `CastListener.java` | Use input, mana reservation/refund, actionbar |
| `mana/` | Testable accounting and PDC persistence |
| `item/WandService.java` | PDC wands, permissions and recipe registration |
| `spell/Spell.java`, `SpellRegistry.java` | Spell metadata and dispatch |
| `spell/AreaSpells.java` | Lightning, frost, wall and time dome |
| `spell/ProjectileSpells.java` | Projectile spells, dragon cloud, meteor |
| `spell/MobilitySpells.java`, `ChannelSpells.java` | Blink, buffs, shroud and Soul Drain |
| `effect/` | Owned effects, statuses and collision geometry |
| `api/` | Cancellable integration events |
| `tools/build_packs.py` | Reproducible, standard-library pack generator |

From the workspace root:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File advance-magic/build.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File advance-magic/test.ps1
```

The build uses the workspace's cached Maven dependencies, produces `advance-magic.jar` at the workspace root and the versioned JAR in `dist/`, and generates both packs. It does not copy anything to a running server. Maven can also compile the module with `mvn -pl advance-magic -am package`; then run `python advance-magic/tools/build_packs.py` to build packs.

`tests/AccountingChecks.java` verifies mana, cooldown boundaries, persistence reconstruction, refunds and swept collision. `tests/check_packs.py` verifies every model, atlas entry, mapping, PNG, archive and hash. `tests/IntegrationChecks.java` is a separate **test-only Paper 26.2 plugin** using a server-backed test actor; `tests/build_integration.ps1` builds it from a local Paper test distribution. Install it only in a disposable test server: it edits the test world, exercises the spells and shuts that server down. The release JAR contains none of the test actor/NMS code.
