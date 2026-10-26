# Halloween event: Haunted Zones and the Haunting modifier (proposal)

Status: **proposal, 2026-10-25, nothing built.** Event window **2026-10-31 to 2026-11-02**. Every number is provisional.

## 1. Owner decisions so far

1. The exclusive is a **spooky modifier** ("Haunting") applied to a Pokemon with a new material.
2. Players earn the material by **completing a World Event Zone**: a random, temporary Halloween spawn with a **raid boss** (CobbleRaids) at the end.
3. The boss is a **raid**, not a solo fight.
4. **No cap** on how much material a player can earn.
5. The Haunting a Pokemon gets is **random**.
6. After the event the modifier **keeps working and stays tradable**, and can never be obtained again.

## 2. Consequences of those decisions

- **No cap plus tradable plus permanent** means the supply of Hauntings is set only by how many zone completions happen in three days. The tuning knobs are the zone spawn rate and
  the essence per completion, not a per-player limit. If it is too generous the event cheapens; too stingy and it feels bad. Section 6 proposes a way to measure it live.
- **Random Haunting** means the same material can give a good or a mediocre result, which makes essence and Hauntings worth trading. It also means the power of any one Haunting must
  be small (section 4), because a permanent, tradable, random bonus is the worst case for fairness.
- **Raid** means group content, so the reward goes to every participant on completion, not only the killer. Use the existing Raids contract; do not edit the sibling repo.

## 3. The event loop

| Step | What happens |
|---|---|
| Spawn | While the event is active, a Haunted Zone opens at a random loaded location every N minutes (config). It is announced in chat and on the HUD overlay, with coordinates or a direction. |
| Zone | A themed area (fog, ghost/dark Pokemon at boosted tiers, using the existing zone rarity table in config `zones`). Entering it starts a short objective (defeat a number of haunted wild Pokemon). |
| Boss | Finishing the objective starts a Ghost/Dark raid boss through the CobbleRaids API. |
| Reward | Every raid participant gets `spooky_essence` as a currency item (loot form, tradable as a voucher like the other materials). Size scales with how many bosses phases were cleared, not with raw damage. |
| Use | Spend essence on a Pokemon to roll one random Haunting onto it. |
| End | Zones stop spawning at the end of the window. An open zone may finish. Essence and Hauntings stay. |

## 4. The Haunting modifier

- **Its own slot.** It does not use one of the 1-6 affix slots, so players never trade a real affix for a cosmetic reward.
- **One per Pokemon.** Re-rolling replaces the old one and costs more essence (provisional: 1 for the first roll, then 3, 5, 8).
- **Roll odds (owner brief 2026-10-25: more variety, two very rare and very strong).** A roll first checks a 4 percent chance for a Rare Haunting; otherwise it picks from the
  Common table by weight. Provisional pool of ten:

| Haunting | Odds | Effect (provisional) |
|---|---:|---|
| Wisp | 18% of commons | 20% chance on hit that the target flinches next turn it moves. |
| Hex | 16% | At end of turn, a foe with a stat drop or a status loses 1/16 max HP. |
| Spectral Veil | 16% | Takes 10% less damage from Normal and Fighting moves. |
| Pumpkin Lantern | 14% | Heals 1/8 max HP when it switches in. |
| Trick or Treat | 12% | The first time it is hit each battle, it restores 1/8 max HP. |
| Cobweb | 10% | On switch-in, lowers the foe's Speed by one stage. |
| Graveyard Chill | 8% | A foe that makes contact with it loses 1/16 max HP. |
| Midnight Howl | 6% | Its first attack each battle deals 15% more damage. |
| **Headless Rider** (Rare) | 2.5% overall | Once per battle, a hit that would faint it leaves 1 HP, and it gains +1 Attack and +1 Special Attack. |
| **Reaper's Toll** (Rare) | 1.5% overall | When it faints a foe it heals 1/3 max HP and gains +1 in its higher attacking stat. |

  The commons are deliberately small. The two Rares are the "very strong" ones, so they are the only Hauntings that could change a battle; at 4 percent a roll they should be a few
  percent of everything minted. All ten need `domain` unit tests like the existing affixes, and the Rare effects need a check that they cannot loop (Reaper's Toll chaining KOs).
  Open point: whether Hauntings are active in player-versus-player battles. PvP is native today (no profile effects), so I'd keep Hauntings out of PvP as well unless you say otherwise.

  Exact numbers come from the effect budget in `design/affixes.json`; each effect needs a unit test in `domain`, as the existing affixes have.
- **Visible.** A themed nameplate tag and a battle-start effect line, so the exclusive is seen by other players.
- **Trading.** The Pokemon carries the Haunting with it through the existing profile and auction round trip (see the trade and auction test notes).

## 5. Where it lives in the code (to verify before building)

- `design/currency-items.json`: add `spooky_essence` (name, color, lore, rarity `epic`, kind `Currency`), plus its custom model data and pack art (32px).
- New `design/hauntings.json`: the pool and effect parameters, so tuning needs no code change.
- `domain`: Haunting rules (roll, re-roll cost) kept pure, as with `RankedRules`.
- `store`: Haunting stored on the profile; a new profile field means a migration and a versioned codec change.
- `ascensionlib`: the event window and zone spawner (config: start, end, interval, boss, essence per completion), the apply action on the Pokemon page, and the Raids call.
- The Raids question is answered in section 5b.

## 5b. CobbleRaids API check (2026-10-25, read from `L:\SnobblemonRaids`)

**Yes, it can do this.** `com.cobbleraids.api.encounter.CobbleRaidsEncounters` (API_VERSION 1, experimental) has `start(EncounterRequest, EncounterListener)`, `abort(UUID)` and
`isActive(UUID)`, all on the server thread. A request takes an owner id, our own encounter UUID, **1 to 4 players**, a raid definition id, a level and position, a boss level, an
optional shared max health, a policy and rules. The listener is told the outcome (`VICTORY`, `DEFEAT` or `ABORTED`).

What that means for the event:

- **Our event owns the raid.** Every side effect is off unless we ask for it. We set `catchable=false` (nobody can catch the Halloween boss), `raidRewards=false`, `raidHistory=false`,
  and decide separately about `progression`. We pay `spooky_essence` ourselves from the `VICTORY` callback, which is how we keep the reward exclusive.
- **Group size is 1 to 4.** The earlier "raid" idea is fine, but the zone must pick at most four players when it starts the fight. Recruiting should mirror the usual raid recruitment
  (the definitions use a 20 second window, 10 block radius, 4 players).
- **Boss strength.** `bossLevel` is applied as given, and `maxHealth` can be left empty so the Raids mod scales it by player count, or set explicitly.
- **Pays only on victory.** Participants who fought a `DEFEAT` get nothing. Crash recovery: the Raids doc warns that a crash between victory and our payout can lose it, so the payout
  needs our usual operation-ID transaction in the store so a retry replays.
- **Definitions are data.** A boss is a JSON in the Raids mod's `data/cobbleraids/raids/`. Ghost/Dark definitions that already exist: `darkrai`, `giratina`, `marshadow`, `lunala`,
  `tyranitar`, `hydreigon`, `dragapult`, `decidueye`, `calyrex`, `urshifu`. There is no Gengar, Mimikyu or Gholdengo today. Those need a new definition file in the Raids mod, which
  is a sibling repo, so **you** would have to add them (or approve me doing it).
- **Zones are ours.** The Raids mod spawns raids from biome tags on its own timer, which is not what we want. We use the encounter API directly from our zone logic, so the spawn
  location and timing are under our control.
- **Hauntings in this fight.** The doc `COBBLERAIDS-INTEGRATION.md` says boss affixes are not supported; that does not matter here, because the Haunting belongs to the players' own
  Pokemon and only needs the existing player-side battle effects.
- **Caution:** that integration doc was written against Cobblemon 1.7.3 and Raids 0.8.114; the Raids source in `L:\SnobblemonRaids` is the current truth. I read the API source, but
  did not start a raid from our side, so the call itself is untested.

## 6. Risks and how to measure them

- **Too much supply:** log essence awarded per hour with the existing telemetry and balance log, and keep the essence-per-completion value in config so it can change mid-event.
- **Boss too easy or too hard for a group:** a rehearsal run with bots, as in the earlier trade and auction tests, before the event.
- **Clock and time zones:** use one server-side clock and one configured zone for the window, not the player's clock.
- **Late joiners:** nothing about the Hauntings is account-bound, so players who miss it can buy from others.

## 5d. Theory: solo duels feeding one shared zone pool (owner idea, 2026-10-25)

**Idea:** every player fights their own 1-player raid against a copy of the boss, and the damage each duel deals is subtracted from one zone-wide health pool. The zone is won when the
pool reaches zero. This removes the player cap (a 1-player encounter is the best-tested size) and the 12-in-one-battle problem.

**What the code gives us.** A Raids pool belongs to one `RaidSession` in one battle (`RaidRegistry.get(battle)`), so Raids cannot share a pool across battles itself. Our event owns the
zone pool instead, as a number in our store. The encounter API tells the owner nothing until the end: `EncounterListener.onEnded` returns the `contribution` map (damage dealt per
player); `onParticipantLeft` is the only mid-fight callback. So there are two ways to feed the pool:

| | A: report at the end of each duel | B: add a live damage callback to the Raids API |
|---|---|---|
| Change in Raids | none | small: an `onDamage(encounterId, playerId, amount)` default method on `EncounterListener`, called where `-raiddamage` moves the pool |
| Feel | the zone bar drops when a duel finishes; players re-enter for another round | the zone bar drops with every hit, so players see the whole group fighting |
| Fairness | a player who loses still reports damage dealt | same, and it also survives a crash mid-duel only if we write the pool as damage arrives |
| Risk | lowest | low, but it edits the Raids repo and needs a live test |

I'd start with A (it works for the event with no Raids change) and add B only if the end-of-duel bar feels dead in a bot test.

**Rules worth deciding up front**
1. **Pool size** scales with the players currently in the zone (for example base pool times the number of joined players, as `health_per_extra_player` does inside Raids), re-evaluated at each
   duel start, so a quiet zone is not unbeatable and a busy one is not trivial.
2. **Each duel has its own short boss copy** with a modest private health value and a time limit (a few minutes), so it is easy and fun. Damage dealt is capped at the duel's boss health
   so one strong player cannot empty the pool in one duel.
3. **Rounds:** after a duel the player can queue another one while the pool is not empty. This is also where `no limit` on essence is bounded in practice: the pool.
4. **Rewards** go to everyone with contribution above a small threshold when the pool empties (and not only to the last hitter). A pure contribution-weighted essence amount would be a
   second, optional tier.
5. **Pool empties while duels are running:** let those duels finish and credit them. Do not abort players mid-fight (aborting is a bad moment for the Raids API).
6. **Arena:** each duel needs a position. Spread them in a ring around the zone centre, or use the player's own position, so 20 simultaneous boss entities do not stack.
7. **Idempotence:** the pool update and the reward both use the store's operation-ID rule so a retried listener call cannot double-count, the same way the Tower payout does.

**Risks to test with bots** (the existing `validation/smoke` harness has `raidbot.js` and `load_test.py`): many simultaneous 1-player encounters (server tick cost, boss entity count),
the end-of-duel listener being called on a crash/restart, and whether the Raids mod allows one player to have two duels at once (it should be refused).

**What stays the same:** Hauntings work normally, because every duel is a plain 1-player battle with the player's own Pokemon.

## 7a. Owner answers, 2026-10-25 (second round)

- Boss: Darkrai, **Gengar and other Ghost types must be possible.** (New definitions are needed, see 5b.)
- Fight difficulty: fairly easy and fun.
- Hauntings: ten, two of them very rare and very strong (section 4). Essence amounts and the zone spawn rate (every 20 minutes, **no limit** on open zones) are approved.
- New request: **temporarily raise the 4-player raid cap.** See section 5c.

## 5c. Raising the player cap

The cap is not a setting we can change from AscensionLib. It is a hard-coded constant in the Raids mod, `VALIDATED_MAX_HUMAN_PLAYERS = 4` (config validation and every definition)
and `EncounterRequest.MAX_PLAYERS = 4` (the encounter API), described there as "the largest raid the Showdown integration is validated for". So:

- It needs a change in the Raids repo, which I have not touched. It is a one-line constant per place, but the number 4 reflects what was tested, not just a limit.
- Larger raids put more players into one shared-health Showdown battle. Whether that works at 6 or 8 is unknown until it is tested with bots, as with the earlier trade tests.
- A fallback that needs no Raids change: **several 4-player raids at once** against copies of the boss in the same zone, with a shared goal count for the zone.

### Analysis of a 12-player cap (2026-10-25, read-only; the Raids repo is untouched)

The constant is not the real limit. Every human player in a raid shares one Cobblemon `BattleSide`, and each gets a position letter on it:

| Layer | Limit found | Evidence |
|---|---|---|
| Raids config and API | 4 | `VALIDATED_MAX_HUMAN_PLAYERS`, `EncounterRequest.MAX_PLAYERS` (just constants) |
| Raids Showdown patch | 6 | `raid-patch.js` `'abcdef'.charAt(side.n)` throws past 6 human sides |
| Raids PNX mixin | 6 | `RaidBattleMessagePnxMixin` widens Cobblemon's regex `p\d[a-c]` to `p\d[a-f]` |
| **Cobblemon itself (compiled)** | **6** | `Targetable.getLetter()` is a `tableswitch` over positions 0 to 5 only (checked in the 1.8.2 jar) |

So **6 is the most the stack supports without patching Cobblemon's own code.** Reaching 12 would need coordinated changes in all four layers, including a mixin on an interface's
default method in Cobblemon, plus any client-side use of the same letters and a battle screen that has never shown 12 allies. That is a rewrite of untested ground three days
before an event, not a configuration change. Raids also notes that 4 players is the only size ever seen live in a real raid log.

Practical points beyond the letters: a turn waits on every player, so 12 players in one battle makes turns slow; and health scaling for 12 players needs a decision.

**Recommendation:**

1. Raise the cap to **6** in the Raids repo: change the two constants, and extend `showdown_fuzz.js` (it fuzzes 1 to 4 players) and `showdown_raid_patch_test.js` to 5 and 6. All the other layers already allow 6, so this is low risk, and the fuzz harness gives a simulator answer without a live client. Then a bot test of a 6-player raid on a real server.
2. Get to **12 participants per zone** with two or three raids against the zone's boss running side by side, as in the no-change fallback. Each fight stays inside the tested size, and a zone completes when its shared goal is met.
3. Treat true 12-in-one-battle as a later project, not part of this event.

## 7. Decisions still needed

1. Which four Hauntings (section 4)? Keep my pool or swap in your own?
2. Essence per completion (provisional: 2 per participant) and the re-roll costs.
3. Zone spawn interval (provisional: one zone every 20 minutes, up to 2 open at once).
4. Boss: pick an existing definition (Darkrai is the natural Halloween fit: Dark, 3500 base health, `mythical` tier, 900s limit), or ask for a new Ghost one in the Raids mod.
5. Is the level-100 Darkrai definition's strength right for a three-day event, and should its time limit stay at 15 minutes?

## 8. Built (2026-10-25, uncommitted)

The AscensionLib half of the event is built and tested; the CobbleRaids half is in `docs/WORLD-BOSS-DESIGN.md` on the Raids `world-boss` branch.

| Piece | Where |
|---|---|
| Material `spooky_essence` (epic, base item `phantom_membrane`, model 8113, pumpkin art, loot table, voucher form) | `MaterialId`, `design/currency-items.json`, `tools/pixel_art.py` |
| The ten Hauntings: ids, odds (Rare 4% = Headless Rider 2.5%, Reaper's Toll 1.5%; eight Commons by weight), roll costs 1, 3, 5, 8, 8... | `domain/.../Hauntings.java` |
| Storage: table `hauntings` (store schema **5**, older stores gain it on open with the usual backup copy), `ProgressionStore.haunt`: charge, seeded draw and profile revision in one transaction, a retry replays | `store/.../ProgressionStore.java`, `HauntingRecord` |
| Effects in battle: eight mechanics and two channel effects in the Showdown module, listed first so the effect limit (raised 8 to 10) never cuts them | `ascension-fx.js`, `BattleFxPlanner.withHauntings`, `AscensionBattles` |
| Player command `/ascend haunt` (balance, each party Pokemon's Haunting and next price) and `/ascend haunt <slot>` (roll or re-roll; a Rare is announced to the server) | `AscendWiring`, `ProfileService.haunt` |
| Reward: when the World Boss falls, 2 Spooky Essence to each participant, once per (boss, player) | `WorldBossEssence`, `event/WorldBossLink` (reflective, optional) |

**Rules as built.** A Haunting acts wherever ranked effects act: raids (so every World Boss duel), armed encounters and profile zones, and **never in PvP**. It sits in its own slot, follows the Pokemon through trades (it is keyed by the Pokemon), and a Pokemon on loan cannot be haunted. After the event nothing is removed: Hauntings keep working and essence stays spendable and tradable (as a voucher).

**Changed from the proposal.**
- **Wisp** is King's Rock-style: a hit has a 20% chance to make the target flinch, which only matters if the target has not moved yet that turn.
- **Reward threshold** is relative: a player takes part if they dealt at least 5% of the *average* damage per player, in both mods. The first draft used a fixed 1% of the pool, which pays nobody once more than 100 players split the damage evenly (found by a test).
- Heals and chip damage from Trick or Treat, Graveyard Chill and Headless Rider are queued and run after the engine has logged the hit, so the battle log reads in the right order.

**Tests.** Domain 262, store 99 (9 new), ascensionlib 110, Showdown simulator suite 92 (11 new, run against the real simulator). `node tools/page_harness.mjs` reports 3 failing HUD checks about the overlay closing itself; they are in code and tests this work did not touch (the last commit made the overlay persistent), so they look stale, but I did not confirm that on a clean checkout.

**Not built yet.** No page or button for Hauntings (command only; the Dex does not show them), no live run on a server, and the art of the Hauntings themselves (the nameplate tag from section 4 is not built).

## Live bot test (2026-10-25, throwaway server on 25601, Cobblemon 1.8.2, never the owner's server)

Rig: `L:\claude-cobbleraids-work\testserver-integration` with the new CobbleRaids jar (0.8.189 + World Boss) and AscensionLib jar, natural raids off, `world_boss.json` enabled with test-scale numbers (pool scaled to 0.25 minute per player, duel boss 1500 HP, breathers 8/12 s). Bots are `raidbot.js` players, each with one level-100 Glaceon, healed by `pokeheal` every 10 s to stand in for a Pokemon Center. The stock bot never answers the server's Polymer resource pack, so it hangs in the configuration phase; a patched copy answers it (kept outside the repo).

| Run | Result |
|---|---|
| **12 bots** | All 12 in. 12 duels ran at once. Boss (Gengar, then Darkrai on a later start) **defeated once**, in 460 s with 50 battles and 1132 moves sent. 12 players paid 50 Raid Points (config value) and **2 Spooky Essence** each, once. Tick time 19.6 ms average (18.5 ms with the same 12 bots idle, 0.4 ms with nobody). |
| **20 bots** (30 requested; the test server's `max-players` is 20, the rest were refused) | 20 duels at once, 183 battles, defeated in 762 s, 20 players paid, **no errors**. Tick time 23.5 ms average, 39.8 ms worst. Every bot had a Haunting rolled first; the Showdown module logged 248 "Applied ascension effects" lines (50 for two Pokemon) in the duels, three Rare announcements fired, no errors. |
| **Restart mid-event** | 6 bots, boss at 2148/10800 with 6 duels running, graceful `stop` with only bots online. On restart the log said `Resuming World Boss ... pool 2148/10800`, `/cr worldboss status` showed the **same pool and the same 6 damage records**, the showpiece boss was recreated (1 entity), and 4 bots finished it: 6 players paid, 4 online told. |

**What the first runs showed.** With one Pokemon each, the bots lost most duels (12 of 15 in the first run) and fainted Pokemon stay fainted (health carries over), so the chain correctly paused with "none of your Pokemon can fight" and the pool stalled at 66% until the test healed them. That is the designed behaviour; it means players need a second Pokemon or a heal between duels. The breather messages were not checked from the player's side.

**Noise in the log, not from this code.** Cobblemon's own `BattleSelectActionsHandler` throws a `NullPointerException` when a bot answers a battle prompt just after its duel ended (12 of them in the 12-bot run). The stack has no CobbleRaids or AscensionLib frames. A real client would not answer a finished battle.

**Still not checked.** Numbers at event scale (a real player's damage per minute, so `pool.damage_per_player_minute` is still a guess; the 6000 default was not used in these runs), more than 20 bots (raise `max-players`), a real client (right-click on the showpiece, the boss bar, the action-bar countdown, chat text), the Spooky Essence item in an inventory, and whether the Haunting effects feel right to a player.
