# Main item fixes — 2026-09-15

## Changes
- Vanilla Heart of the Sea / Carrot on a Stick creative and interaction events are no longer intercepted as custom items.
- Conduit crafting is allowed again; custom cores remain protected from vanilla recipes.
- Two registered recipes per wand support mixed Netherite Ingots / Nether Stars, with the core at center or bottom-center. Removed generic vanilla-core recipes.
- Opening a crafting table and moving a core no longer auto-consume materials. Use the result slot or `/magic craft`.
- Core/wand identity requires plugin PDC tags; legacy namespaces and renamed genuine items work, conflicting IDs fail closed.
- Evergarden shards, dust, repair stones and elixirs require their existing plugin tags instead of names.
- Explicit crafting permission denial applies to operators too.
- Bedrock creative packet grants recheck Creative mode on the server thread.

## Validation
- Built both JARs with Java release 21 using cached dependencies, matching the existing PowerShell build approach. Maven's existing dependency graph selects an incompatible old Gson and did not build; no Maven-success claim is made.
- Disposable Paper 26.2 build 121 server: 118/118 checks passed; see `results.txt` and `ItemFixChecks.java`.
- Existing AccountingChecks: 24 passed; PackHttpChecks: 19 passed; GeyserCleanupChecks: 7 passed.
- Pack assets are unchanged and embedded in the release JARs.
- No connected Bedrock/Geyser client acceptance test was performed. The Evergarden repair/scroll table recipe issue from the review is outside this main-fix batch and still needs work.

## Install
Stop the server, replace `advance-magic.jar` and `evergarden.jar`, start and reconnect Bedrock. Do not use `/reload`. Root and versioned dist JARs have been updated; prior JARs are retained under `backups/`. No live server was changed.
