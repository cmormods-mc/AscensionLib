# Trials: design proposal (2026-10-05, draft for owner decisions)

Status: **option A built, simple first (2026-10-05)**, not run live. Owner chose A (Towers trial runs) and "simple first": rank payouts
(`TrialRewardBands`, no Cores or Fragments), +3 attunement, all ranks open, no daily budget, no Core pity. Budget, pity and unlock
counters (items 4-5 of the proposal) are deferred until a live test shows farming. Contract: `AscensionRewards.settleTrial`; caller:
CobbleTowers `TowerEncounters.payAscensionLib`. Original proposal text follows.

## What exists today

- **Towers "trials"**: a CobbleTowers run with `floorLimit > 0` (a capped practice run). Towers skips lib payouts for them
  on purpose, so a practice run cannot bypass the Tower key (`TowerEncounters.payAscensionLib`, decided 2026-10-05: "Trials
  are skipped for now; they will get difficulty-tied rewards later"). They already use scouting tiers `trial_rank_1..3` by floor.
- **Lib reward path**: `AscensionRewards.settleTowerBoss` (materials by floor band) and `settleRaidAttunement` (+3 attunement),
  both once per encounter and player; `ScoutingService`, `EnemyGenerator` tiers `trial_rank_1..3` already exist in the lib.
- **Original spec** (`ECONOMY.md`, `DESIGN.md`, `UX.md`): three free solo Trial ranks run as standalone NPC battles, with a
  battle copy of the player's team, rank unlock by victories, daily full-reward budget (20), Core pity on rank 3, attunement +3,
  three trainer archetypes. That design needs a way to launch and own an NPC battle without CobbleRaids; `BATTLE-ADAPTER-DESIGN.md`
  and the headless work have not proven one.

## The decision: what is a Trial now?

| Option | What it is | Cost | Risk |
|---|---|---|---|
| **A. Towers trial runs (recommended)** | A Trial is a floor-limited Towers run. The lib pays by a *difficulty band* (`trial_rank_N` = floors reached). No new battle code. | Small: a `settleTrial` contract + bands + budget, Towers calls it | Depends on Towers being installed; reuses Towers' arenas |
| B. Standalone NPC Trials in the lib | The original spec: lib launches NPC battles itself, battle copies, archetype trainers | Large: NPC launch, ownership, copy isolation, recovery | Unproven hooks (spec's own release gate) |
| C. Skip Trials | Towers and Raids are the only earning paths | None | Nothing to do without Towers/Raids; promotion pace depends on them |

## Proposal for option A (if chosen)

1. **Contract** `AscensionRewards.settleTrial(UUID encounterId, String outcome, int rank, Collection<UUID> players)`: victory only,
   once per (encounter, player), reward kind `trial_rank_N`. `rank` is 1-3.
2. **Payout** from the spec table (provisional, same as `ECONOMY.md`): rank 1 = 6 dust; rank 2 = 10 dust + 1 facet; rank 3 = 16 dust,
   2 facets, 25% Core with pity (4th consecutive rank-3 win without a Core grants one; per player, persisted, shared with raids
   mapped to that band). No Scouters or Catalysts, matching the Tower rule. Unique Fragment: 1 per rank-3 full-reward win (spec 226).
3. **Attunement**: +3 per victory (same as raids/towers), once per encounter and Pokemon.
4. **Daily full-reward budget**: 20 per player per UTC day shared by trials and raids; extra wins pay 25% dust (min 1), no facets, cores or
   pity, still +3 attunement. Reservation at admission, released on defeat; this is new store state (`daily_budget` table) and the
   riskiest part of this proposal.
5. **Unlocks**: rank 2 after five rank-1 wins, rank 3 after five rank-2 wins (store counter per player). Enforced by Towers when
   offering a trial rank, with the lib answering `canEnter(player, rank)`.
6. **No key**: trials cost no Tower key (already true in Towers); the daily budget is what bounds farming.

## Open questions for the owner

1. Option A, B or C?
2. Should Trials also pay when the run was a **rental-draft** run, or only owned-team trials?
3. Daily budget: keep 20 and 25% overflow, or something simpler for the first version (no budget, just trial rewards smaller than
   Tower rewards)?
4. Core pity: ship in the first version, or later?
5. Unlock counters (5 and 5 wins): keep, or all ranks open from the start?
6. Do trial floors keep Towers' own tier mapping (floors 1-4 rank 1, 5-9 rank 2, 10+ rank 3), or should a trial have a declared rank?
