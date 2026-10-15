# Tower completion rewards: the "huge reward" payout (proposal)

Status: **numbers approved 2026-10-15; build steps 1 to 3 are built (2026-10-15, below), step 4 (tuning) waits for live counters and a live run.** Follows the owner's answers to `TOWER-LOOT-DESIGN.md` and the new brief: a Tower completion takes about an hour, so it must end in a
big, memorable reward, and we must establish the right amount and quality. Every number comes from `tools/tower_loot_model.py` (a Monte Carlo model, arithmetic only, no live data);
change the numbers there and re-run.

## Decisions so far (owner, 2026-10-16)

| # | Decision |
|---|---|
| 1 | **Tower Marks** (bound, never tradable) are the proof of a personal clear; they gate the top-end crafts (Overreach, Fusion, Catalyst install). **Yes.** |
| 2 | **No Region Crests / themed reforging.** Attunement already makes players grind the Tower; that is incentive enough. |
| 3 | Core pity **later** (not enough affixes yet for a proper implementation). |
| 4 | Reward follows difficulty, with the daily full-reward budget. **Yes.** |
| 5 | A per-material `tradable` flag, a 3 percent withdrawal fee, and a listing-fee request to the auction mod. **Yes.** |
| 6 | Dungeons keep paying fragments, at a lower rate. **Yes.** |
| 7 | **Every Tower completion should feel very, very rewarding** (about an hour invested): establish the amount and the quality. |

## What "a completion" is

Clearing the **F10 champion** of a tower cycle (and each later Ascension cycle's champion). The F5 boss is a **mini chest** about half way. A run that cashes out before F10 keeps the F5
chest; a forfeited run keeps nothing (Towers' existing rule).

## The shape: a guaranteed pile, a quality tier, and a crown

The dopamine comes from three things, in this order of reveal:

1. **The pile (guaranteed):** dust, facets, fragments, scouters and Tower Marks, counted up on the reveal screen. Always substantial, never zero.
2. **The quality tier (earned, not rolled):** **Bronze, Silver, Gold or Mythic**, decided by how hard and how clean the run was. It multiplies the pile and adds crown slots.
3. **The crown (a headline item on every completion):** one to three rolls from a weighted table of things players actually want. **A completion never ends without a notable item**
   (an Ascension Core, a Sigil, a Catalyst, an armor piece, a ticket, or a big fragment bundle): there are no empty hours, which is what a pity timer was meant to prevent, built in
   without a timer.

### Quality tier (skill, not speed)
`score = challenge cards taken + 2 x Ascension cycle + 2 if nobody was knocked out`

| Tier | Score | Multiplier on the pile | Crown slots |
|---|---|---:|---:|
| Bronze | 0 to 1 | 0.7 | 1 |
| Silver | 2 to 4 | 1.0 | 1 |
| Gold | 5 to 7 | 1.5 | 2 |
| Mythic | 8 or more | 2.25 | 3 |

The mix is assumed to be 15 / 45 / 30 / 10 percent; **read the real mix from Towers' counters and re-tune the thresholds** so a normal good run is Silver to Gold and Mythic is a feat.
Speed never counts (Towers' "skill over grind" rule): harder and cleaner pays more, faster does not.

### The pile (Silver values; the tier multiplies them)
| Material | F10 champion | F5 boss (mini chest) |
|---|---:|---:|
| Resonance Dust | 800 | 200 |
| Facets | 6 | 1 |
| Unique Fragments | 3 | 1 |
| Scouters | 4 | 1 |
| Tower Marks (bound) | 3 (+1 Gold, +1 Mythic) | 1 |
| Ascension Core | crown only | 5 percent |

### The crown table (weights per tier)
| Crown item | Bronze | Silver | Gold | Mythic |
|---|---:|---:|---:|---:|
| Ascension Core (1) | 30 | 36 | 36 | 36 |
| Unique Fragment bundle (12) | 40 | 30 | 22 | 14 |
| Ascension Sigil (1) | 10 | 12 | 14 | 14 |
| Unique Catalyst (1) | 0 | 2 | 4 | 8 |
| Tower armor piece (Towers' own table) | 14 | 14 | 16 | 18 |
| CobbleCards ticket (Towers' own table) | 6 | 6 | 8 | 10 |

Higher tiers get both more slots and a better table: a Mythic completion is three rolls with a Catalyst chance of 8 percent each.

## What it pays (model output, mean over 200,000 completions)

| | Bronze | Silver | Gold | Mythic | **Mix** |
|---|---:|---:|---:|---:|---:|
| Dust | 760 | 1,000 | 1,400 | 2,000 | **1,184** |
| Facets | 5 | 7 | 10 | 15 | **8.4** |
| Fragments | 7.8 | 7.6 | 10.3 | 13.0 | **9.0** |
| Tower Marks | 4 | 4 | 5 | 6 | **4.5** |
| Ascension Cores | 0.35 | 0.41 | 0.77 | 1.13 | **0.59** |
| Sigils | 0.10 | 0.12 | 0.28 | 0.42 | **0.19** |

- **At least one core on 49 percent of completions** (two or more on 9 percent); a notable crown item on **100 percent**. A core comes about every other hour; the longest drought in
  200,000 simulated completions was 17 (99th percentile 6). Core pity can still be added later as a safety net.
- **Goals in hours of Tower play** (one completion = one hour): Mythic promotion **15 h** (attunement is the limit, 90 at +6 per run, and the cores keep pace at 7 in about 12 h);
  Fusion **25 h** (attunement 150; cores 10 in about 17 h); a Catalyst from fragments **11 h**, sooner with a crown Catalyst; one Overreach attempt **0.2 h**; perfecting a rank V slot
  **0.4 h** each, which keeps dust a real sink: a fully polished team is tens of hours of dust.
- **Attunement stays the true gate** (decision 2): cores and fragments are paced just behind it, so nothing else becomes the wall.

## The Tower must out-earn everything else per hour
| Source | Dust / hour | Facets / hour | Cores / hour |
|---|---:|---:|---:|
| **Tower completion** | **1,184** | **8.4** | **0.59** |
| Trial rank 3 (12 min) | 80 | 5 | 0 |
| Dungeon, today's bands, tier 5 (15 min runs) | 957 | n/a | 0 |
| Dungeon, today's bands, tier 8 | 1,470 | n/a | 0 |
| Dungeon, **proposed** bands, tier 5 | 552 | n/a | 0 |
| Dungeon, **proposed** bands, tier 8 | 840 | n/a | 0 |

The dungeon bands as built (`DungeonRewardBands`: boss 30 dust per tier, arena 14) **out-earn a Tower hour on dust at tier 8**, so they must come down: arena 14 to 10 and boss 30 to 12
per tier (and the Sigil rule can stay). Dungeons keep paying facets and a fragment at a lower rate than the Tower (decision 6): 1 fragment, 35 percent, from a boss.

## How the reveal should feel (the other half of the hit)
The numbers only matter if the moment lands. Proposed sequence, all server-driven so it needs no client mod beyond WebGUI:
1. A tier banner ("MYTHIC COMPLETION") tinted with the tower's colours and the rarity bezel.
2. The pile counts up line by line with the item icons, a sound per line (a tick that rises in pitch).
3. The crown rolls one at a time, each with a pause, a flash and a sound by item value; a Core or Catalyst gets the big one.
4. A party chat line for anyone's Core, Catalyst or Mythic tier; a run report card (meters, Marks, fragments toward the next Catalyst, attunement gained) that can be shared.
AscensionLib can show this itself (a `completion.html` page: it knows exactly what it paid), independent of Towers' own item reveal, which keeps showing the Tower's items.

## Marks, trading and the contract
- `tower_mark` as a wallet material that **cannot be withdrawn** (a per-material `tradable: false` in `design/currency-items.json`); 1 Mark for Overreach and for installing a Unique
  with a Catalyst, 5 for a Fusion. At about 4.5 per hour, Marks gate access without ever becoming the wall.
- A **3 percent withdrawal fee** in the same material, rounded up; a listing fee requested from the auction mod.
- One new contract method, additive (reflection signature pinned by `ContractSignaturesTest`):
  `settleTowerCompletion(UUID encounterId, String outcome, int bossFloor, int cycle, int challengeCards, boolean flawless, Collection<UUID> players)`
  returning per-player statuses, exactly once per (encounter, player), seeded by (encounter, player), so a retry pays the same chest. CobbleTowers passes its run's challenge count,
  Ascension cycle and whether anyone was knocked out; the existing `settleTowerBoss` stays for F5 and older callers.

## Built (AscensionLib side, not yet seen live)
- `TowerCompletionBands` (domain): tier from score, the pile, the crown table, all seeded by (encounter, player). `AscensionRewards.settleTowerCompletion` pays it (reward kind `tower_completion`),
  gives +3 attunement, opens `completion.html` for each newly paid player (chat lines without WebGUI) and announces a Core, a Catalyst or a Mythic tier to the party.
  `AscensionRewards.towerCompletionExtras(encounter, player, cycle, cards, flawless)` returns the crown items CobbleTowers hands over itself (`armor`, `tickets`) and the `tier` (0 to 3).
- `MaterialId.TOWER_MARK` with a `tradable: false` flag in `design/currency-items.json` (never withdrawn as a voucher); a 3 percent withdrawal fee, rounded up, paid on top by the wallet
  (the voucher holds the amount asked for). Marks are costs: Overreach 1, a Unique install or replace 1, a Fusion 5 (craft, fusion and wallet pages show them).
- The F5 boss pays the mini chest (200 dust, 1 facet, 1 fragment, 1 scouter, 1 Mark, 5 percent core) through `settleTowerBoss`; the F10 champion through `settleTowerBoss` still pays the old
  small formula until CobbleTowers calls `settleTowerCompletion` instead.
- Dungeon bands lowered as proposed (arena 10, boss 12 per tier) and the boss pays one fragment 35 percent of the time. The listing-fee request to the auction mod is written up in
  `AUCTION-LISTING-FEE-REQUEST.md` (nothing to build here).
- **Daily full-reward budget** (decision 4 of `TOWER-LOOT-DESIGN.md`, built 2026-10-15): `DailyRewardBudget.DEFAULTS` = 20 full chests per UTC day per player, then the chest's dust is paid at 25 percent (never below 1; cores, fragments,
  Marks and everything else are never cut). It counts the main chests only: `settleTowerCompletion`, `settleTowerBoss`, `settleTrial` and `settleDungeonRun` (a Scouter or Sigil roll does not use it). Applied inside the store transaction
  (`ProgressionStore.grantBudgeted`, store schema 4, table `daily_budget`), so a retried settlement replays what it first paid and uses no more budget; the player is told in chat when a chest was cut. Both numbers are the constants in
  `DailyRewardBudget`. With hour-long completions the limit mostly bites dungeons and trials (a Tower run is two chests, the F5 mini chest and the completion).
- Test seam: `/ascend admin completion <bronze|silver|gold|mythic>` shows the reveal with nothing paid; add `pay` to settle a real chest for yourself.

## Built in CobbleTowers (2026-10-15, uncommitted, not seen live)
- `FloorPayout`: the champion floor of a cycle (`contentFloor == floorCount`, every Ascension cycle) now calls `AscensionLibRewards.settleCompletion` when the installed library has
  `settleTowerCompletion` (an older library keeps the milestone formula). The inputs: challenge cards = the run's accumulated challenges, cycle = `tower.ascensionOf(floor)`,
  flawless = no player Pokemon fainted in the cycle (the mastery stats, reset at the cycle's first floor). F5 stays on `settleTowerBoss`.
- `PendingLibSettlement` gained `Kind.COMPLETION` plus `cycle` and `towerId` (older saved tags still load), so a completion is written to disk before the call and retried like the others.
- `CompletionExtras` picks the armor pieces and CobbleCards booster packs the crown rolled from the tower's own boss reward table, and `AscensionLibRewards` queues them (idempotently,
  `addIfAbsent`) for each GRANTED or ALREADY_GRANTED player before the settlement is trimmed, then delivers them. Tests: `CompletionExtrasTest`, `PendingLibSettlementTest`.

## Build plan (after you approve the numbers)
1. **Domain:** `TowerCompletionBands` (tier from score, the pile, the crown table, all seeded), `TowerMark` material, `tradable` flag, fee in `mintVoucher`; dungeon bands lowered.
   Tests: tier thresholds, seeded replay, every completion has a crown, mean payouts match this document, the fee rounds up, Marks cannot be withdrawn.
2. **Mod:** `AscensionRewards.settleTowerCompletion`, Marks as costs on Overreach / Fusion / Catalyst install, the `completion.html` reveal, wallet page rows for Marks.
3. **CobbleTowers (its own repo, its own review):** call the new method on the F10 champion with the run's challenge count, cycle and flawless flag; keep F5 on `settleTowerBoss`.
4. **Tune** from real counters and a live run, using `tools/tower_loot_model.py` as the calculator.

## Decisions (owner, 2026-10-15)
All five questions below are settled: the proposal stands as written.
1. Tier thresholds and the 15 / 45 / 30 / 10 mix: accepted as a starting point, to be corrected from live counters.
2. Pace of 0.59 cores and 9 fragments an hour: accepted.
3. About 1,200 dust an hour: accepted.
4. A Mythic completion stays a very good roll; no guaranteed Core.
5. No signature completion item outside the crown table (no title, banner or cosmetic).

## Questions as asked
1. Tier thresholds and the 15 / 45 / 30 / 10 assumption: acceptable as a starting point, to be set from live counters?
2. Is **0.59 cores and 9 fragments per hour** the right pace (Mythic in 15 hours, bounded by attunement), or should the Tower be even faster / slower?
3. Dust at about **1,200 per hour**: it keeps polishing and refining a real sink, but it makes promotion dust trivial. Acceptable?
4. Should a **Mythic-tier completion** also guarantee a Core (a visible reward for a feat), or stay a very good roll (1.13 cores on average)?
5. Anything you want as a **signature completion item** that is not in the crown table (a title, a banner, a cosmetic, a unique Tower-only item)?
