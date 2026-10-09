# Depth proposal: mechanic affixes, theme resonance and endgame (2026-10-09)

Status: **approved by the owner 2026-10-09 (seven affixes, resonance multipliers, Awakening as a fusion; no stacked-team test required first). Parts A and B are built (simulator module, catalog, domain; none seen in a live battle or client yet); Part C is not.** Every number is provisional, like the rest of the catalog, and none of it has been seen in a live battle. Written against the catalog as of commit `31a50dd` (27 affixes, 7 Uniques, channels `outgoingDamage` / `incomingReduction` / `healing` / `residual`).

## Why

Twenty-four of the 27 affixes are a percentage on one of three channels; Smoldering, Venomous and Rending are the only damage-over-time ones, and Rupture the only Unique that adds a mechanic. A build is mostly "which numbers are biggest", so two Pokemon of the same rarity feel alike. Rank V and Mythical are also final: once a Pokemon is maxed, materials have no use. This proposal adds (A) affixes that change how a fight plays, (B) a theme system that rewards building around an idea, and (C) a sink for maxed Pokemon.

## A. Mechanic affixes (7 new, catalog 27 -> 34) — BUILT 2026-10-09, simulator-tested only

Handlers are in `ascension-fx.js` (`applyMechanics`), catalog entries in `design/affixes.json` and `design/ranked-catalog.json`, and a new combat channel `mechanic` (cap 50) in `design/balance.json` and `Rules`. `validation/showdown/ascension_fx_test.js` has 8 new tests (45 in all, all passing against the real unbundled simulator). Findings from the spike: the module installs after `Battle.start`, so leads have already entered the field and Bracing Entry handles them explicitly; battle log idents use the Pokemon uuid, not its name; a status that fails through the `SetStatus` event is silent, so Wardstone adds a `-message` line. Bracing Entry was reduced from 10-20 to 8-16 so rank V (41-48) stays under the 50 percent proc ceiling. Wild rating draws now include the new affixes (prefix weights total 1021 instead of 881), so an unrescued wild Pokemon's derived rating can differ from before; stored profiles and the catalog version (1) are unchanged. Not built: icons or special text in the client for the new affixes, and the resonance bonus in the inspect and Scouter screens.

Same rules as today: Java sends affix ids and percents, the simulator decides everything from native state (`ascension-fx.js`); rank bands follow the existing rule (rank I range given, later bands span `ceil(rankIMax / 2)`); multiplicative inside a channel; PvP never. The "Roll" column is the rank I range in percent; rank V is about 3x.

| Affix | Slot | Weight | Family | Roll (rank I) | Behaviour |
|---|---|---:|---|---|---|
| Keen Edge | prefix | 40 | `crit_offense` | 4-8 | Each damaging move you use has this chance to be a critical hit on top of the native crit roll. |
| Ensnaring | prefix | 40 | `slow_offense` | 5-10 | Each damaging move that hits has this chance to lower the target's Speed by one stage. |
| Momentum | prefix | 30 | `momentum_offense` | 3-6 | Each KO you score adds this much to your outgoing damage for the rest of the battle, up to 3 stacks. Resets if you leave the field. |
| Swift Strike | prefix | 30 | `speed_offense` | 4-8 | Your Speed is raised by this percent. |
| Bracing Entry | suffix | 40 | `entry_defense` | 8-16 | When you enter the field (including battle start), this chance to raise Defense and Sp. Def by one stage. |
| Wardstone | suffix | 40 | `status_ward` | 5-10 | Each time a status condition would be applied to you, this chance it fails. |
| Stubborn | suffix | 30 | `endure_defense` | 3-6 | Once per battle, a hit that would faint you has this chance to leave you at 1 HP. |

Design notes:
- **Distinct from what exists.** Wardstone prevents status; Status Guard only reduces damage while statused. Stubborn is a chance, once per battle, while the Unique Last Breath is a guarantee with a healing drawback; Stubborn's bands stay low so it never replaces the Unique. Momentum's bonus joins the `outgoingDamage` channel, so it is capped with the rest.
- **Caps.** Damage-shaped effects (Momentum) use the existing 100 cap. Proc chances (Keen Edge, Ensnaring, Bracing Entry, Wardstone, Stubborn) get a per-affix ceiling of 50 percent applied in the planner, so no roll is clipped silently and none becomes a guarantee.
- **Rolling.** Procs use the battle's own random source (`battle.prng`), so Showdown replays stay consistent, and each proc rolls once per move use and target.
- **Native failure paths stay in charge.** Ensnaring goes through the engine's boost path, so Clear Body, Mist and Substitute behave natively. Wardstone makes `setStatus` fail the way an immunity does. Keen Edge uses the move's native crit handling, so Battle Armor and Shell Armor still block it.

**Simulator spike required before building** (same method as the status-damage work): confirm in the bundled simulator that (1) a move's crit can be forced for one use without bypassing Battle Armor, (2) `Pokemon.getActionSpeed` or the speed modifier event can scale Speed and is honoured by turn ordering, (3) `switchIn` events fire for the starting Pokemon and for replacements, (4) wrapping `setStatus` to fail leaves the engine's own `-fail` message and messaging intact (the Cobblemon client misreads unknown `[from]` effects, as recorded for Triumphant), (5) boosts applied from a damage hit respect Sheer Force and Shield Dust. Each gets harness tests in `validation/showdown/ascension_fx_test.js` before any Java is written.

## B. Theme resonance (no new simulator code) — BUILT 2026-10-09, domain only

`Resonance` (domain) and `BattleFx.effectsOf` apply it; `ResonanceTest` has 8 tests and `:domain:test` passes (134). Only the affixes that exist today are themed (Tempo has two pieces until Swift Strike, Bracing Entry and Ensnaring arrive with Part A, Predator gains Keen Edge and Momentum, Bulwark gains Stubborn, Vitality gains Wardstone: add each to `Resonance` with its affix). It applies to players and to enemy snapshots alike, so an enemy's fighting values can exceed what a Scouter reveals for a two-piece theme; the reveal and the inspect screen do not show the bonus yet (client work, not done). Not run in a live battle.

Every affix gets one **theme** tag. A Pokemon whose affixes include two from one theme gets a **Resonance** bonus: those affixes' rolled values are multiplied by 1.15 for two pieces and 1.30 for three or more. The planner applies it before sending, so the simulator, the caps and the Scouter reveal need no change; the inspect screen shows the theme and the bonus.

| Theme | Affixes |
|---|---|
| Tempo | `opening_strike`, `opening_guard`, `swift_strike`, `bracing_entry`, `ensnaring` |
| Toxin | `smoldering`, `venomous`, `rending`, `defiant_force`, `status_guard` |
| Predator | `executioner`, `last_stand`, `super_effective_force`, `keen_edge`, `momentum` |
| Bulwark | `iron_resolve`, `type_bulwark`, `physical_bulwark`, `special_bulwark`, `resilient_hide`, `stubborn` |
| Vitality | `restorative`, `triumphant`, `healthy_force`, `healthy_guard`, `steadfast_guard`, `wardstone` |

Affixes without a theme (`type_focus`, `type_mastery`, `physical_force`, `special_force`, `overwhelming_force`, `kindred_force`, `type_ward`) stay neutral on purpose: they are the flexible filler, so theming costs a build something. Only a Pokemon with three or more slots can reach the 1.30 tier. Resonance multiplies the rolled value, then the channel cap applies, so it cannot exceed 100/50/50.

Risks: 1.30 on a rank V rare affix is large (Type Mastery stays neutral for that reason), and themes of five to six affixes make a two-piece set common on a Mythical (six slots). The multipliers are the first thing to tune. `BattleFxPlanner` already holds the rules and has 15 tests; the resonance maths belongs there with its own tests.

## C. Endgame

A **maxed** Pokemon is Mythical with every ordinary slot at rank V.

1. **Perfecting (dust sink, no extra power ceiling).** Refine rerolls a value inside its band at random. Perfecting raises a slot's **minimum roll** by 1 point per use, up to the band maximum, so effort converts to a guaranteed floor. Cost per use: 30 dust at the first step, +10 per step already bought on that slot. No facets or cores. It never exceeds the rank's band, so it adds depth without raising the damage ceiling.
2. **Transcendence (Awakening, as a fusion).** Owner decision 2026-10-09: two Pokemon that each hold a Unique are fused, and their two Uniques become one **Transcendent** power shaped by both Uniques and by both Pokemon.
   - **Inputs.** A *host* (Mythical, every ordinary slot rank V, holds a Unique) and a *donor* (Epic or higher, holds a different Unique). Both must be the player's own, not craft-locked, out of battle. Proposed: the donor is consumed (permanently, behind an explicit confirmation); the host keeps its identity and ordinary slots, and its Unique slot now holds the Transcendent. A Pokemon can transcend once. **Decided 2026-10-09: the left slot (host) is saved and the right slot (donor) is consumed.** The researched recipe book is in [FUSION-DESIGN.md](FUSION-DESIGN.md).
   - **Cost (starting point).** 600 dust, 30 facets, 10 cores, 150 lifetime attunement on the host, and one Catalyst. About 67 keyed Tower runs of cores, roughly five weeks at the assumed pace.
   - **Result is deterministic and previewed.** No hidden roll: the preview shows the resulting name, benefit and drawback, so the fusion screen doubles as a recipe book. Known recipes are discovered by previewing them.
   - **How the outcome is shaped.** (1) *Base power by Unique pair.* Seven Uniques give 21 unordered pairs, and each pair has one Transcendent made by **composition**: both Uniques' existing simulator handlers run at 75 percent of their benefit, and both drawbacks apply at 60 percent. So the simulator needs a scaling factor on the existing handlers, not 21 new scripts. (2) *Element from the two Pokemon.* The host's and donor's primary types give the Transcendent an element (for example a type-matched bonus on moves of the host's type), so the same pair differs by who fused. (3) *Species recipes.* A table of named recipes keyed by the two species (and optionally the pair of Uniques) overrides the name and adds a recipe bonus, so famous pairings are worth discovering. The first batch is the 21 base pairs plus about 30 species recipes, chosen by the owner.
   - **Data.** New `design/transcendents.json`: `{ pairs: [{uniques:[a,b], id, name, benefit, drawback}], recipes: [{species:[x,y], uniques:[a,b]?, id, name, bonus}] }`, validated at load like the catalog (every Unique id exists, every pair unique, no recipe unreachable). Resolution order: exact recipe, then the pair's base, then refuse with a clear message.
   - **Budget.** The principle: a Transcendent is stronger than either source Unique alone and weaker than both at full strength, because 75 percent of two benefits is more than one but less than two, and the drawbacks still bite.
   - **Schema.** The profile gets a Transcendent in the Unique slot (a `kind`), so this is the schema 2 migration; the store adds a fusion craft kind with the donor's removal in the same transaction as the host's change and the material debit, with the usual operation ID replay.
3. **Not proposed:** a rank VI (it raises the ceiling the stacked-team test has not yet validated) and any new material (the five existing ones suffice).

## Build order and sizing

1. **Spike A** (simulator checks above, no Java). 2. **Part B** resonance in `domain` (`BattleFxPlanner`, tests, inspect row) because it needs no simulator work and tests the idea cheaply. 3. **Part A** affixes in batches: Keen Edge, Ensnaring, Momentum first (they reuse the outgoing path), then Wardstone, Bracing Entry, Stubborn, Swift Strike. 4. **Part C**: Perfecting (store column for the per-slot floor and a craft kind), then Transcendence (profile schema 2, the transcendent catalog and the fusion screen, the largest item; it also needs the recipe list from the owner).

Schema impact: A and B need only catalog changes (`design/affixes.json`, `design/ranked-catalog.json`, plus a catalog version bump, which deliberately changes wild ratings per the existing rule). C needs a profile field per slot (floor) and a second Unique, so `ProfileV1` is not enough for it. Do C last.

## Open questions for the owner

1. Approve the seven affixes, or trim the list? Swift Strike is the riskiest because Speed is a single stat that decides move order.
2. Resonance multipliers 1.15 / 1.30: too strong, too weak, or fine to start?
3. Should themed affixes carry any visible marker in the catalog, or stay implicit?
4. Transcendence: the donor is consumed (decided). Recipes for all 1025 species now exist (see FUSION-DESIGN.md); which further curated pairs matter to you?
5. Should Perfecting be limited per slot, or unlimited up to the band maximum?
6. Is a stacked-team live test (already open in `BALANCE-REVIEW-2026-10-05.md`, item C) a precondition for any of this?
