# Cobble Exiled: profile zones and the underground dungeon dimension

Status: design, 2026-10-10. Owner decisions are marked **Decided**. Nothing in sections 3-8 is built yet.

## 1. Owner decisions

| # | Decision |
|---|---|
| 1 | **Decided.** Profiles act only in three places: raid battles, armed tower encounters, and the Exiled dimension. In an ordinary wild fight anywhere else, **neither** the player's profile nor a wild rating acts. This supersedes the 2026-10-05 call in `BATTLE-ADAPTER-DESIGN.md` ("random Pokemon have affixes too"). PvP stays native. |
| 2 | **Decided (wallet material confirmed).** Captures outside Exiled no longer roll a profile. A new item can add a profile to a Pokemon that has none. It is gated behind rare loot in towers and in Exiled. |
| 3 | **Decided.** Exiled lives in AscensionLib, not a companion mod. Entry is an admin command for now; no mirrored surface. |
| 4 | **Decided.** Exiled is an underground tunnel dungeon with progressive levels (not a mirrored surface world). |

## 2. Built (Phase 1a, battle gate)

- `BattleFxPlanner.plan(..., raid, profileZone, armed, ...)`: returns nothing unless `armed != null || raid || profileZone`.
- `battle/ProfileZones`: allow-list of dimension ids (defaults `ascensionlib:exiled`, `cobbletowers:tower`; other mods call `allow`).
- `AscensionBattles` reads the dimension from the **wild actor's entity**, not the player's. No entity (a scripted battle) is not a zone, so it fails closed to native.
- Tests: 12 in `BattleFxPlannerTest` (3 new: ordinary wild fight is native for both sides; raid and armed encounter act outside a zone; PvP native inside a zone).

## 3. Capture policy (Phase 1b; built 2026-10-10 except the client badges)

1. **Capture policy (done).** Hatching also gives no profile now (my call, so breeding cannot bypass the item; reverse it if you disagree); the login sweep never creates a profile, so unprofiled Pokemon stay unprofiled. Original note: `AscendWiring` queues `Origin.of("wild_capture")` with `rollRarity=true` for every capture. Change it so a capture outside a profile zone creates **no** profile, and one inside Exiled rolls the wild rating (with the zone, section 5). Read the dimension from the captured Pokemon's entity at capture time. Check first: the login sweep (`reconcile`) and "legacy owned Pokemon are lazily initialised as Common" (`DESIGN.md:75`) must not quietly create a profile for an unprofiled Pokemon, or the item has nothing to do.
2. **Client messaging.** The battle HUD and inspect screens need the existing "inactive in this battle" badge (`UX.md:90`) for ordinary wild fights, and an "unprofiled" state in the inspect screen.
3. **Docs/tests.** Update `BATTLE-ADAPTER-DESIGN.md` row 12, `DESIGN.md:68`, `ECONOMY.md` (daily research counts "ordinary wild captures").

## 4. The profile item: Ascension Sigil (built 2026-10-10)

**Decided:** a wallet material (`ascension_sigil`), not a physical item, so it follows the "materials live in the wallet" rule and has no trade or dupe path. Using it **rolls a rarity** from the normal table, is **consumed**, and works only on a Pokemon with no profile. It is rare loot from **tower bosses and Exiled**.

- **Use:** `/ascend sigil <slot>` (party Pokemon). `ProfileService.useSigil` refuses: not yours, on loan, in a battle, already profiled or quarantined, no Sigil. `ProgressionStore.useSigil` spends the Sigil and creates the profile in **one transaction** (kind `USE_SIGIL`, origin `sigil`), so a refusal costs nothing and a crash retry never charges twice. The capture-reveal card is shown on success.
- **Tower drops:** `AscensionRewards.settleTowerBoss` now also rolls `SigilDrops.rollTower` per participant (2 percent, +1 per 10 boss floors, at most 8; reward kind `tower_sigil`; seeded by encounter and player). CobbleTowers needs no change. A failure there never changes the boss payout.
- **Exiled drops:** `SigilDrops.rollExiled` exists (2 percent on floor 1, +1 per floor, at most 10) but nothing calls it until the dimension exists.
- All numbers are provisional pending balance approval. Not built: a Sigil button in the craft screen (only the command), the client wallet display of the Sigil (`/ascend wallet` lists it).
- Open: the rarity table for a Sigil is the ordinary table, not floor-aware; decide whether a deeper Sigil should skew rarer.

## 5. Zones and the wild rating

Wild rarity is `HMAC(secret, catalogVersion, UUID)` + types + level. To make deeper levels rarer, add the **level (floor) number** to the HMAC input and **stamp it on the wild Pokemon when it spawns** (persistent data on the entity). The Scouter preview and the capture both read the stamp, not the player's position, so they still agree. Catalog and secret stay untouched, so existing ratings do not move; only Exiled wilds gain the extra input.

Prerequisite (unverified in `BUILD-STATUS.md:110`): a wild Pokemon keeps its UUID through capture. If that is false, nothing in the preview contract works, so test it first.

Free-roaming wild Pokemon have no scouting flow today (Scouter works on declared encounters only). Exiled needs a "scout the Pokemon in front of me" request.

## 6. The dimension

### Shape
A bounded stack of **floors** in one persistent shared dimension (`ascensionlib:exiled`; the id is permanent once a world uses it). Each floor is a tunnel network generated deterministically from `(worldSeed, floor)`; stairs and shafts join floors. Difficulty tier, level range, wild rarity band and loot rise with the floor. "Continuous" means one world with no loading hub or instance swap.

### Why this is the cheapest option
A mirrored overworld would cost the most worldgen of any choice. A tunnel network is mostly solid or empty space, has a small footprint, and has a hard edge. The footprint is a fixed horizontal radius times N floors, so pre-generation, disk, and the radius limit are all trivial because the tunnels simply end.

### Generator
- **Preferred:** a custom `ChunkGenerator` registered in Java. It writes only the carved tunnel and its walls, with no noise, cave carvers or structures, so a chunk costs little to generate. Deterministic from the seed, so a reset regenerates identical layout.
- Datapack-only alternative (`minecraft:noise` with custom noise settings plus structures) is easier to ship but harder to bound and tune.
- `dimension_type`: same height as the overworld or smaller, `natural=false`, `has_raids=false`, no vanilla monsters, `bed_works=false`, fixed time. Players respawn in the overworld.

### Performance plan (server)
- Hard footprint (tunnels end at a radius) instead of relying on the world border, which I believe is shared across dimensions in 1.21.1. Verify.
- Spawns: Exiled-only Cobblemon spawn pools (spawn `dimensions` condition, verify in 1.8.1) with per-player caps, tight despawn, no vanilla mobs. Cobblemon's overworld pools must not apply here by accident.
- Chunk loading and ticking: keep only chunks near players loaded; tunnel chunks are cheap, but entity counts are not.
- No flying, riding or ender-pearl shortcuts in Exiled (they outrun chunk generation and skip floors).
- Showdown: many concurrent wild battles each build an `ascensionFx` payload. Already bounded; measure with `Profiler` and spark.
- Reset: scheduled per-floor reset of regions with no player nearby; terrain regenerates identically.
- Define budgets before building: MSPT per active player, entities per player, region-file growth per day.

### Entry and exit
Open: how a player enters (an item, an NPC, a portal structure, a command first). Record a return point (the `CobbleTowers` `ReturnPoint` pattern) and recover players whose floor was reset while they were offline. Death returns to the overworld; decide item loss.

## 7. Gaps and risks

1. The profile item changes the economy: it makes towers and Exiled the only source of profiles, so loot tables, drop rates and research/Dust rules must be redone together.
2. Unverified base: wild UUID through capture; fusion never run live; the whole battle adapter was untested live at the last status.
3. Players in Exiled with unprofiled Pokemon are native fighters against rated wilds. Decide whether Exiled is open to unprofiled parties, and what they see.
4. Grief and exploit: block breaking and placing in Exiled, AFK farming at spawners, entry camping, combat logging, teleport commands from other mods, chunk loaders.
5. Raids or towers launched inside Exiled are recognised by the battle, not the dimension, so they work as before.
6. Operations: backup size, reset tooling, admin status, fail-closed when the dimension is missing, uninstall (a used dimension does not unwind cleanly).
7. Does "mirrors the overworld" still apply? If Exiled entrances should sit at overworld coordinates (a surface rift above each tunnel head), that adds a surface structure to the overworld. Not assumed here.

## 8. Phases

| Phase | Work | State |
|---|---|---|
| 0 | Verify: wild UUID through capture; Cobblemon spawn `dimensions` condition; per-dimension border | todo |
| 1a | Battle gate, zone allow-list, tests | **done** |
| 1b | Capture policy, doc updates | **done** (captures roll only in a zone; hatching gives no profile). Client badges still todo |
| 2 | Ascension Sigil (wallet material, command, tower drops) | **done**; Exiled drops wait for the dimension |
| 3 | Dimension skeleton: custom generator, one floor, admin entry, return point | todo |
| 4 | Floors, zones, spawn pools, zone-aware rating, scouting a roaming wild | todo |
| 5 | Performance budgets, reset tooling, anti-exploit | todo |
| 6 | Live soak with several players | todo |
