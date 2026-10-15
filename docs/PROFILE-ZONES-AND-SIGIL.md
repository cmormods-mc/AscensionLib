# Profile zones and the Ascension Sigil

Status: 2026-10-14. Owner decisions are marked **Decided**. The planned in-library "Exiled" dungeon dimension was dropped; dungeons will come from the separate server-side mod **ascensionDungeons**, which hooks in through `ProfileZones.allow()`.

## 1. Owner decisions

| # | Decision |
|---|---|
| 1 | **Decided.** Profiles act only in raid battles, armed tower encounters and registered profile-zone dimensions. In an ordinary wild fight anywhere else, **neither** the player's profile nor a wild rating acts. This supersedes the 2026-10-05 call in `BATTLE-ADAPTER-DESIGN.md` ("random Pokemon have affixes too"). PvP stays native. |
| 2 | **Decided (wallet material confirmed).** Captures outside a profile zone roll no profile. The Sigil adds one to a Pokemon that has none, gated behind rare loot. |
| 3 | **Decided 2026-10-14.** The dungeon dimension is not built in AscensionLib. ascensionDungeons owns it. |

## 2. Battle gate (built)

- `BattleFxPlanner.plan(..., raid, profileZone, armed, ...)`: returns nothing unless `armed != null || raid || profileZone`.
- `battle/ProfileZones`: allow-list of dimension ids (default `cobbletowers:tower`; other mods call `allow`). It is in memory and rebuilt at each start, so a mod must call `allow` on every startup.
- `AscensionBattles` reads the dimension from the **wild actor's entity**, not the player's. No entity (a scripted battle) is not a zone, so it fails closed to native.
- Tests: `BattleFxPlannerTest` (ordinary wild fight is native for both sides; raid and armed encounter act outside a zone; PvP native inside a zone).

## 3. Capture policy (built; client badges still todo)

- A capture inside a profile zone rolls a profile; a capture elsewhere creates none. Hatching gives no profile (so breeding cannot bypass the Sigil; reversible).
- The login sweep never creates a profile, so unprofiled Pokemon stay unprofiled.
- Todo: an "inactive in this battle" badge for ordinary wild fights and an "unprofiled" state in the inspect screen.

## 4. The Ascension Sigil (built)

A wallet material (`ascension_sigil`, never rename), not a physical item, so it has no trade or dupe path. Using it **rolls a rarity** from the ordinary table, is **consumed**, and works only on a Pokemon with no profile.

- **Use:** `/ascend sigil <slot>` (party Pokemon). `ProfileService.useSigil` refuses: not yours, on loan, in a battle, already profiled or quarantined, no Sigil. `ProgressionStore.useSigil` spends the Sigil and creates the profile in **one transaction** (kind `USE_SIGIL`, origin `sigil`).
- **Tower drops:** `AscensionRewards.settleTowerBoss` rolls `SigilDrops.rollTower` per participant (2 percent, +1 per 10 boss floors, at most 8; reward kind `tower_sigil`). A failure never changes the boss payout.
- **Dungeon drops:** none yet. ascensionDungeons should pay the Sigil through the wallet grant path with its own seeded roll; a `SigilDrops` dungeon rule can be added once its floor model exists.
- All numbers are provisional pending balance approval. Not built: a Sigil button in the craft screen, the client wallet display (`/ascend wallet` lists it).
- Open: should a deeper Sigil skew rarer.

## 5. Open for ascensionDungeons

- Wild UUID through capture is unverified (`BUILD-STATUS.md`); wild ratings depend on it.
- A floor-aware wild rating (floor number in the HMAC input, stamped on the spawn) and a scouting flow for roaming wild Pokemon would need an AscensionLib contract. Not designed yet.
- Raids or towers launched inside a zone are recognised by the battle, not the dimension, so they work as before.
