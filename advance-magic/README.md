# advance-magic

Fifteen craftable wands with server-side spell logic, 100 mana, regeneration of 2 mana per second, per-player/per-spell cooldowns, and optional Java/Bedrock resource packs. No client mod, ProtocolLib or NMS dependency is used by the release plugin.

Targets Bukkit APIs available in **Spigot/Paper 1.21.11+**. Compiled to Java 21 bytecode; use the Java runtime required by your server (the workspace's Paper 26.2 runs on Java 25). Geyser translates the native entities, sounds, particles and actionbar. A Bedrock client is still needed to visually acceptance-test the pack and touch controls.

## Install

1. Copy `dist/advance-magic-1.0.0.jar` into the server's `plugins/` directory and restart.
2. Use `/magic list` to see spell IDs, costs and crafting cores. `/magic mana` shows current mana.
3. Give a test wand with `/magic give <online-player> lightning_strike` (operator permission).
4. Right-click/use the wand in either hand. A main-hand wand takes priority if both hands contain wands. On Bedrock use the normal use/interact control; wands use `CARROT_ON_A_STICK` so use input works without a client mod. Wands are unbreakable and recognized by PDC, never by their visible name.

Every wand uses a **tagged Magic Core from an Evergarden Vault** in the center of a 3×3 grid. Fill the eight outer slots with **Netherite Ingots or Nether Stars** (mixing is supported). The core determines the spell. Ordinary Heart of the Sea items and the old vanilla ingredient cores cannot craft wands. Renamed cores, older Voidscape-tagged cores, and Evergarden-tagged cores remain compatible.

```text
Ingot / Star | Ingot / Star | Ingot / Star
Ingot / Star | Magic Core   | Ingot / Star
Ingot / Star | Ingot / Star | Ingot / Star
```

Defeat five shrine waves and the boss, collect an Evergarden Key, and open an Evergarden Vault once per player per shrine. Rewards: 35% two Diamond Blocks, 35% two Netherite Ingots, 10% two matching armor trims, 15% special equipment, 4.9% normal Magic Core (14 types; 0.35% each), and 0.1% Mythic Core of Levitation. Total core chance is 5%, down from 10%. A vanilla Trial Key does not open this vault.

[Thai infographic](dist/advance-magic-guide-th.png) · `/magic list` · `/evergarden guide`

Permissions: `advance-magic.cast` and `advance-magic.craft` default to everyone; `advance-magic.admin` defaults to operators. Failed targeting, blocked casts and unsafe blink destinations refund mana and do not start a cooldown. Mana and cooldown expiry times persist in player PDC across reconnects and normal restarts; death does not refill mana. Regeneration occurs while online, once every 20 server ticks. Spell durations are server ticks, while cooldowns use elapsed wall-clock time.

## Spells

Damage numbers are health points before armor, resistance and vanilla immunity frames (two health points = one heart). One right-click triggers the full sequence automatically, including the added final stage; it spends mana and records mastery only once. Existing wands gain the new effects without recrafting. Mana and cooldown costs are unchanged.

| ID | Mana / cooldown | Current behavior and added stage |
|---|---|---|
| `lightning_strike` | 60 / 8s | 60 lightning damage, then 30 in a wider ring; final 18-damage echo 0.7s after the second strike. |
| `frost_nova` | 50 / 12s | Freeze/Slowness IV, 35 shatter damage at 1s; final 25-damage echo after another 0.7s. |
| `shadow_step` | 45 / 6s | Blink up to 12 blocks through one thin wall, departure smoke and 25 arrival damage; 20-damage echo at the destination after 0.7s. |
| `natures_bloom` | 70 / 25s | Cleanses/heals allies, regeneration and absorption; second bloom heals and roots enemies. New third bloom at 3s heals players for 6, adds Resistance I for 5s, and deals 15 thorn damage. |
| `earth_wall` | 40 / 10s | 7×4×2 visual wall blocks projectiles for 5s, with 25 creation damage. New 25-damage pulse in front when its duration ends. Does not block walking. |
| `dragons_breath` | 75 / 18s | Moving radius-4 cloud, 30 damage/sec for 6s; settled cloud applies Weakness/Wither. New radius-5 burst for 30 after the cloud expires, spaced beyond the last cloud hit. |
| `void_pull` | 65 / 14s | Pull/root field, 45 collapse damage; 20-damage aftershock 0.7s later. |
| `invisibility_shroud` | 50 / 30s | Hide equipment/player for up to 30s, speed and resistance; first ambush adds 50 damage. Successful ambush also triggers a 20-damage echo after 0.7s. Attacking or casting another spell reveals the caster. |
| `poison_spores` | 45 / 10s | Poison cloud and three clusters; now 20 direct impact damage plus a 30-damage pulse after 1s, useful against poison-immune mobs. |
| `wither_ray` | 85 / 12s | Six skulls, 40 each (last skull ×1.5), plus Wither; last skull adds a 15-damage echo after 0.7s. |
| `shulker_levitation` | 95 / 35s | Singularity, 120 explosion damage, and 15s sculk zone; new 20-damage echo after explosion. Corrected damage cap so the configured 120 is no longer truncated to 100. Cancelling the singularity no longer detonates it. |
| `meteor_strike` | 90 / 20s | Three meteors, 90 damage each; each impact adds a 15-damage aftershock 0.7s later. Terrain ignition remains configurable. |
| `iron_armor` | 60 / 35s | 60s Resistance IV, absorption, strength, fire resistance and thorns; new radius-5 bastion pulse for 25 damage after 1s. |
| `time_dilation` | 80 / 25s | 15s radius-6 dome slows projectiles to 10%, debuffs enemies and buffs allies; existing 2.5s pulses now also deal 12 damage each (five pulses). |
| `soul_drain` | 70 / 16s | Three 40-damage drain pulses, then 35 nova damage; new 20-damage echo. Killing the target with a drain pulse now also releases the nova and echo immediately. |

New damage is configurable under `follow-up.damage.<spell_id>` and `damage.poison-impact`. These keys are added to existing configs on startup, preserving customized base damage, PvP and resource-pack settings. Delayed echoes remain at their marked location and stop when the caster dies, leaves, changes worlds or the plugin shuts down. They respect the normal target limits and `MagicAffectEvent`; if the active-effect limit is full, an optional echo is skipped.

Allies are the caster, their own tamed animals, and members of their **main scoreboard team**. Enemy effects exclude allies, armor stands and creative/spectator players. Player combat additionally respects `pvp` in this plugin's config and the world's PvP setting. Otherwise living mobs are valid enemies. AOE status spells can reach through walls; Soul Drain and initial targeted spells require sight.

Shroud uses Bukkit's plugin-scoped `hidePlayer`/`showPlayer`, which sends the hide/show packets including the entity's equipment. It also removes the hidden player from other players' tab lists. It does not remove the caster's real equipment, and revealing does not override a different plugin's hide state. The caster still sees their own first-person hand. Reference: [Spigot Player API](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/entity/Player.html).

## Resource packs

`dist/advance-magic-java.zip` contains fifteen 32x32 wand textures and modern item model definitions, using namespace `advance_magic`. Vanilla items keep their usual appearance. Pack formats 75 through 88 cover the intended Java 1.21.11–26.2 clients; [Minecraft 1.21.11 notes](https://www.minecraft.net/en-us/article/minecraft-java-edition-1-21-11) specify format 75.

Open `dist/wand-preview.html` for a self-contained preview of all wand textures.

For Java, install the ZIP manually or host it at a direct HTTPS download URL, then set `resource-pack.url` and `resource-pack.sha1` in the plugin config using `dist/pack-hashes.json`. Restart after editing config. The plugin adds its pack with a unique UUID so it can coexist with the Evergarden pack. The default URL is blank; no pack has been uploaded or remotely deployed.

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

## Balance verification

`evergarden/test.ps1` enumerates all 10,000 possible vault tickets. `tests/build_balance.ps1` builds `target/balance-checks.jar` against a cached Paper 26.2 server. Install the test JAR with Advance Magic only in a disposable server: it edits terrain, creates a server-backed player, tests all 15 automatic extra stages, single mana/mastery/cooldown accounting, protection cancellation and early Soul Drain kills, writes `balance-result.txt`, and shuts down. It is not included in release JARs.
