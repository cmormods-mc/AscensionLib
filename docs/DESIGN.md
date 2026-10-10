# CobbleAscend — complete design proposal

**Design update:** [Ten-level milestone upgrades](MILESTONE-UPGRADES.md) now supersede the fixed-affix-range/no-rank launch rules below. Rarity controls slot count; Pokémon level milestones grant player-selected slot-rank upgrades. Rank tables and shared caps require a new balance pass.

Status: proposed design for owner review, 2026-10-03. Working title and namespace: CobbleAscend / `cobbleascend`. Target: Minecraft 1.21.1, Fabric, Cobblemon 1.8.1, Java 21. Separate mod. Owner update: CobbleRaids integration is deferred until the standalone mod is ready; references below describe future integration, not current implementation scope. This document specifies intended behavior, not already implemented capabilities.

## Product promise

Make catching and developing Pokémon feel like finding and crafting equipment in an ARPG, while preserving the decisions that make turn-based Pokémon battles interesting. Species, typing, moves, ability, held item, nature, IVs and EVs remain important. Ascension adds a second development layer, rather than replacing those systems.

Players should recognize a useful capture immediately, understand why an affix helps their team, and have a bounded path to improve a favorite Pokémon. A bad random roll should create a crafting project, not force abandonment of a beloved companion.

Borrow principles from Path of Exile—affix combinations, crafting decisions, repeatable encounter modifiers and aspirational builds. Use original names, text, UI and assets. Do not reproduce its passive tree or currencies wholesale.

## Decisions made for the first release

- Preserve turn-based battles, ordinary type effectiveness, four move slots and existing ability/held-item rules.
- Add six ascension rarities, visible on every eligible owned Pokémon.
- All rarities can be promoted to Mythical; rarity cannot fall through normal gameplay.
- Higher rarity adds affix slots, not a hidden multiplier to every stat.
- Each affix has a transparent, modest numerical range. No affix ranks, item-level gates or random corruption at launch.
- Preserve normal IV/EV caps. No base-species rewrites and no permanent edits to native combat stats.
- Support PvE initially. Affixes are disabled for every Pokémon in PvP, including mixed encounters involving opposing players. Unsupported battle types also use vanilla rules for all participants.
- No player Strength/Speed/Luck effects, passive party auras, real-time combat or overworld damage bonuses at launch.
- No breeding inheritance of rarity. Eggs hatch Common with a normal Common affix; breeding still produces useful Pokémon through ordinary mechanics.
- No Pokémon destruction for currency. Players improve companions through battles, research and crafting.
- No paid power, random paid rolls or monetization dependencies.
- A matching client mod is required for the intended release experience. A future command-only server variant is a separate product decision, not a promised compatibility mode.

## Who this serves

Collectors get visible rarity, discovery and provenance. Build-focused players get conditional affixes and team composition decisions. Casual players get a daily research stipend and guaranteed crafting progress. Server operators get tunable tables, transparent logs and independent operation without CobbleRaids.

The first release favors small cooperative servers. Competitive balance, global cross-server markets and very large public economies need separate evaluation.

## Core loop

1. Explore and capture a Pokémon normally.
2. On confirmed acquisition, see its ascension rarity and affixes in a non-blocking reveal.
3. Inspect exact effect ranges and choose whether it fits a team role.
4. Earn research materials and complete solo Trials; installed raid integration adds cooperative raid rewards.
5. Reforge or refine a selected affix, or accumulate materials and attunement for promotion.
6. Attempt harder Trials or raids with a better coordinated team.

Example: a Common Growlithe rolls Fire Focus. A player keeps it, evolves it and promotes it, adding defensive suffixes along the way. It can reach the same slot ceiling as a freshly caught Mythical Growlithe. The rare capture saves development time but does not unlock an exclusive species or an exclusive move.

## Capture rarity

| Ascension rarity | Probability | Weight / 10,000 | Prefix slots | Suffix slots | Total |
|---|---:|---:|---:|---:|---:|
| Common | 55% | 5,500 | 1 | 0 | 1 |
| Uncommon | 28% | 2,800 | 1 | 1 | 2 |
| Rare | 12% | 1,200 | 2 | 1 | 3 |
| Epic | 4% | 400 | 2 | 2 | 4 |
| Legendary | 0.9% | 90 | 3 | 2 | 5 |
| Mythical | 0.1% | 10 | 3 | 3 | 6 |

These are starting tuning values, not playtested balance. Weighted selection uses server randomness and integer weights; every valid owned acquisition gets one persisted result. Repeated callbacks, reconnects and trading cannot reroll it. Rarity is independent of shiny status, species classification, nature and normal IV rolls.

No hidden catch pity initially: deterministic promotion is the bad-luck exit. Display exact odds in help and configuration. Do not sell the roughly 1-in-1,000 Mythical rate as a guarantee within 1,000 catches.

All unlocked slots are filled on capture. Promotion fills exactly the newly unlocked slot. No empty-slot crafting, sockets or affix ranks in release one.

## Acquisition and lifecycle policy

| Situation | Behavior |
|---|---|
| Successful wild capture | **Revised 2026-10-10:** inside a profile zone (Exiled) roll once after successful ownership/storage, normal table. Anywhere else: no profile; a Sigil (wallet material, to be built) adds one. |
| Failed catch / full-storage failure | No ownership reward, no currency, no rolled owned record. |
| Existing ascension data | Preserve it; validate schema and revision. |
| Evolution, form change, nickname, PC move | Preserve rarity, values and provenance. |
| Trading | Preserve all progression; transfer authority to current owner. |
| Hatching | **Revised 2026-10-10:** no profile (a Sigil adds one). Was: Common, one random eligible prefix. |
| Admin give / imported Pokémon | Common unless explicit privileged initialization specifies otherwise. Record source. |
| Legacy owned Pokémon | Lazy initialization as Common; an explicit logged operator migration can opt into a one-time wild-table roll. |
| Raid boss becomes a reward Pokémon | Clear encounter-only effects; allocate a new owned identity, roll from normal capture table by default. Requires explicit integration hook. |
| Duplicate callback | No change and no duplicate stipend. |
| Release | Pokémon is removed by normal rules; no currency awarded. |
| Faint or loss | No affix destruction, rarity loss or permanent injury. |
| Pokémon missing a known affix definition | Preserve record, show disabled affix, allow free replacement after operator-approved migration. |

A temporary transformed/copied combat identity does not copy permanent ascension ownership. Initial rule: original battler retains its own affixes through Transform; effects evaluate the actual move and current battle state. Ditto, forms and illusion must pass integration tests before this behavior ships.

Capture eligibility is about how the Pokémon was acquired, not merely observing it in a party. If third-party acquisition provenance cannot be identified, initialize Common and log the unrecognized source; never infer a high-value raid capture from boss tags alone.

## Affix structure and balance

The launch catalog contains twelve affix templates in `design/affixes.json`; typed templates instantiate one of the eighteen ordinary types. Prefixes affect direct move damage. Suffixes affect direct damage taken or eligible healing. Each template belongs to a mutually exclusive family, preventing duplicate copies and multiple typed variants of the same family.

Choose affixes without replacement among eligible families, then roll integer percentages uniformly within the template range. All template weights are equal initially. For typed outgoing damage, select uniformly from the Pokémon's current species/form types at acquisition. Other templates are broadly available. Existing typed affixes remain fixed through evolution and can be reforged normally. Do not try to infer optimal move sets during generation.

Effects activate only while the owning Pokémon is active. No effects from fainted or benched party members. A conditional bonus describes an observable condition; no secret scaling by trainer level or account age.

Overhaul bonuses within the same channel add together, then clamp: outgoing damage +100%, direct incoming damage reduction 50%, eligible healing +50% (raised 2026-10-05 from 25/15/15 to make room for rank V and powerhouse affixes; values live in `design/balance.json` and remain provisional). Prefixes never grant accuracy, speed, priority or extra turns. Suffixes never grant immunity. This protects turn ordering and avoids making status-based strategies irrelevant. More affixes increase consistency and coverage; they do not bypass channel caps.

These caps do not claim an upper bound on complete Pokémon battle strength. Native abilities, held items, weather, STAB and type multipliers still operate normally. Stress-test compounded native interactions before publishing.

## Combat semantics

Damage bonuses apply once per ordinary damaging hit after native damage calculation and before HP removal, subject to an exact simulator hook chosen in the spike. Preserve existing immunity, failure, protection, substitute and rounding behavior. For design calculations use `floor(nativeDamage * (100 + outgoingPct) / 100 * (100 - incomingPct) / 100)`, minimum one only where native damage was already positive and the native engine permits damage. Production must use the simulator's integer modification conventions at its chosen hook; this illustrative formula does not authorize replacing its pipeline.

Exclude fixed damage, OHKO moves, recoil, confusion self-hit, poison, burn, weather and other residual damage from both direct-damage channels. Drain moves use actual damage inflicted according to the native engine. Native abilities and items remain authoritative. No extra healing event may recursively trigger another affix.

Conditions based on health, status or turn order snapshot immediately before each move begins and remain fixed for all hits of that move. First-move guard consumes on the first incoming damaging move that actually deals direct damage, including a move hitting a substitute; a fully blocked or immune move does not consume it. Every hit of that first move shares the reduction. Its charge resets once at battle start, never on switching or revival. A fainted Pokémon cannot heal. Healing amplification applies only to move-sourced self-recovery and drain, not items, residual abilities, Regenerator, revival, ally healing or external world healing. A heal caused by a move already consumes its normal PP and turn; affixes add no new action.

For raids, effects modify ordinary battle damage before the raid adapter commits its single shared-health change. Never apply an affix again when mirroring shared damage to other raid battles. Raid bosses are not guaranteed to have player-style maximum HP; HP-relative boss triggers and residual effects require separate support and are out of launch scope.

Battle snapshots freeze each Pokémon's affixes, rarity and definition version at entry. Crafting and trading of those Pokémon are locked while the battle owns them. Reloads affect subsequent battles. A simulator mismatch must refuse enhanced Trials/raids before payment or admission; it must not quietly charge players for an encounter whose mechanics are disabled.

## Progression and crafting

See `ECONOMY.md` for costs and reward sources. The launch bench has three actions:

- Reforge: choose one slot and replace its affix identity and value. Existing other slots remain fixed. The current identity can roll again; its displayed probability is included in the preview.
- Refine: choose one slot and reroll its value uniformly in its current range. A worse or identical value is possible and explicitly stated before confirmation.
- Promote: pay the fixed cost and meet attunement; advance one rarity and fill the new slot. No chance of failure.

All crafting previews show the exact Pokémon, selected slot, material cost, eligible pool and possible range. A single explicit confirmation performs one transaction. No paid reroll animation delaying the result. In-progress requests are idempotent and server-authoritative.

Attunement requires actual participation in a qualifying victory. Simply sitting in a party is insufficient. All Pokémon that took a legal turn or received an opposing action in a winning qualifying encounter receive the same fixed attunement credit once. Repeated multi-battle raid callbacks count as one encounter. Defeat grants no currency or attunement; aborts and technical failures return entry tokens when applicable.

## Standalone and raid endgame

The core must be useful without CobbleRaids: normal captures, inspections, research stipend and crafting work independently. Release-one solo Trials provide Facets, Cores and attunement through ordinary turn-based NPC battles. Exact NPC battle launch/ownership hooks are a spike requirement; if unavailable, Trials need a separately scoped implementation before release. Do not silently make raids mandatory for maximum progression.

Launch with three Trial ranks and three original trainer archetypes: Ember Circuit (offensive pressure), Verdant Bastion (recovery/defense) and Storm Relay (switching and coverage). Use only legal native teams and moves initially. Rank is declared before entry; opponent level and team are fixed per rank. No automatic rubber-banding to equipped affixes. Boss encounter affixes are deferred until player affixes are proven.

CobbleRaids offers an alternative source of the same materials and progression. It owns raid health, targeting, boss AI, catch policy and its existing rewards. CobbleAscend owns only additional reward entitlements, ascension data and its approved damage contributions. Ordinary public raids and overhaul-owned encounters are distinct integration paths.

Post-launch Expeditions may chain three to five Trials with a choice of encounter modifiers and rewards. No portals, atlas tree, six-act campaign or procedural dimensions in release one.

## Interface and accessibility

Use an optional non-blocking toast on acquisition, an inspection screen, a crafting screen and a Trial entry screen. Screen designs and copy are in `UX.md`. Every rarity has text and a distinct symbol as well as color. Provide reduced motion, adjustable reveal verbosity and localization keys. Preserve existing nicknames and Cobblemon summary screens; integrate a badge only after a supported client hook is verified.

The default reveal never interrupts a capture chain or exposes all other players' captures. Mythical global broadcasts are opt-in server configuration, with per-player opt-out. No resource pack is required for core behavior; original icons can be added during client implementation.

## Release scope and explicit exclusions

First technical slice: reliable capture initialization, persistence, inspect command and one Fire Focus battle modifier. Playable alpha: all rarities, eight simplest affixes, one solo Trial, one reforge action, optional owned-raid smoke test. First release: twelve affixes, all three crafting actions, three Trial ranks/archetypes, full persistence/recovery testing and client screens.

Not in release one: passive skill trees, skill gems, move sockets, raised EV caps, new species/forms, new shiny variants, real-time combat, player potion buffs, ascension breeding, legendary-only affixes, random item destruction, trading marketplace, seasonal wipes or competitive PvP bonuses.

## Evaluation criteria

Technical release gates are in `IMPLEMENTATION.md`. Product playtests should measure whether players understand an affix without external documentation, keep and improve at least one lower-rarity catch, can identify a meaningful craft after the first session, and see an advantage from team choices rather than only maximum rarity.

Starting targets: inspection comprehension by four of five testers; first meaningful reforge within 30–60 minutes under the stated earning assumptions; no single affix selected by over 60% of diverse tested builds without a clear explanation; no tested team trivializes the highest Trial purely through stacked percentage bonuses. These are acceptance targets to investigate, not results from player testing.
