# Bedrock item investigation ? 2026-09-14

Tested the current root advance-magic.jar and evergarden.jar on isolated Paper 26.2-121, Java 25. The actor had a dot-prefixed name and operator permissions. It was a synthetic server-side actor, NOT a connected Bedrock client. No Geyser/Floodgate session was exercised. Production configuration and production error logs were unavailable (workspace logs/latest.log was empty).

## Results

- All 15 `/magic give <spell>` self-give commands delivered correctly tagged wands.
- All 45 server crafting cases passed: 15 Evergarden cores with netherite, nether stars, or mixed rings. Each consumed one item in every slot.
- `/magic givecore frost_nova` and `/evergarden give key` delivered valid items.
- Four tagged Evergarden key shards crafted a key.
- BUG: four ordinary prismarine shards also crafted an Evergarden key. Material-only registration is not rejected by the prepare listener when no custom tags are present.
- BUG: ordinary conduit crafting returned AIR. WandService clears every recipe containing any Heart of the Sea, including the vanilla conduit recipe.
- Repair stone plus damaged pickaxe has no registered Bukkit recipe, but Bukkit.craftItem returns a repaired pickaxe through the prepare event. This is not proof of a working Bedrock transaction or ingredient consumption.

## Unconfirmed Bedrock-specific causes

Wands register 15 identical material recipes for each supported core position and dynamically replace the result after checking core metadata. Geyser recipe translation needs direct testing; successful Bukkit crafting alone does not verify the Bedrock recipe book or output-click transaction.

Both give commands require admin permissions. Magic supports self-give, so `/magic give frost_nova` avoids player-name resolution. The plugin item names are not separately registered vanilla `/give` item IDs. Mapping files and Bedrock packs control presentation; server item creation tests do not validate their deployment.

Official mapping reference: https://geysermc.org/wiki/geyser/custom-items/

## Needed to identify the reported failure

Exact failing command and returned message; whether crafting produces no result or a result that cannot be picked up; production Paper/Geyser versions; server log around the failure. Test `/magic give frost_nova`, `/magic givecore frost_nova`, and `/evergarden give key` from the affected admin account.

No release source or JAR was changed. The accompanying probe is TEST ONLY and automatically shuts down its disposable server. Do not install it on a live server.
