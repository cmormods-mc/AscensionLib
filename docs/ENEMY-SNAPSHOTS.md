# Enemy rarity and modifiers

Status: domain generator implemented and unit-tested (2026-10-05). It is not connected to any battle, Trial or raid yet.

## Contract

Battles consume an immutable `CombatSnapshot` per combatant (rarity, ordinary slots with ranks and values, optional Unique ID, catalog version). A player's snapshot is frozen from the committed profile (`CombatSnapshot.ofProfile`). An enemy's snapshot is generated from the encounter's declaration (`EnemyGenerator`). The battle adapter never needs to know which kind it holds. `contentHash()` lets a battle prove its modifiers stayed frozen.

## Enemy generation rules (decided with the owner)

- **Same rules as players.** Same slot counts per rarity, affix weights, family exclusions, rank bands and channel caps. Difficulty comes only from the tier.
- **Tier = rarity table + rank credit budget** (`design/enemy-tiers.json`, provisional). Credits are spent one at a time on random slots below rank V, exactly like a player's milestone credits (maximum 10). Credits with no legal slot are dropped.
- **Deterministic.** Seeded from (catalog version, encounter ID, enemy index), so reconnects, retries and shared raids see the same enemy, and generation order does not matter.
- **Never a profile.** Enemies are not stored, owned, captured, traded or rewarded through this path.
- **Uniques are bosses only and fixed.** An `EnemySpec` may declare one Unique for a boss; it is never randomly drawn and non-bosses are rejected.

## Open decisions

- **Which enemies get this** (owner deferred): Trials, tower and raids are the natural fit. Wild battles stay native unless decided otherwise. Wild rarity must not be visible or inheritable on capture (it would spoil the reveal and allow flee-and-reroll).
- Tier values, boss tiers and per-encounter overrides need balance approval.
- Battle adapter (spec P4/P5): applying a snapshot in the simulator, unsupported-format detection and PvP exclusion are not built.
