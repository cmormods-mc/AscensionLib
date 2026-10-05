# Ten-level affix upgrades

Owner direction: each ten levels grants a pending screen where the player selects an affix to advance into its next roll range. This supersedes the earlier fixed-ranges/no-ranks first-release proposal. The current prototype and its tests still implement base ranges only; no runtime rank or upgrade-screen implementation is claimed.

## Proposed rules

- Level milestones are 10, 20, 30, 40, 50, 60, 70, 80, 90 and 100: ten choices per Pokémon under the normal level cap.
- Each milestone grants one persistent upgrade credit belonging to that Pokémon. Spend one credit to advance one occupied affix slot by one rank.
- Proposed launch ceiling is rank V, starting at rank I. Multiple milestones can improve the same slot until its cap. Credits can remain pending for future slots unlocked by rarity promotion.
- Level jumps grant every crossed milestone. A level-19 Pokémon advancing to 31 receives two credits.
- On first legitimate acquisition, catch up all milestones already reached: a level-37 catch begins with three pending credits. Its affixes are not silently assigned random ranks. A level-100 capture receives ten choices, so it is not locked out of the system.
- Evolution, trade, PC storage, disconnect and restart preserve earned/spent/pending upgrades. Ownership changes never re-award them. Track awarded milestone identities/highest awarded milestone; reducing and regaining levels cannot generate more credits.
- The upgrade retains the affix identity and selected type, then rolls its value within the next band. Bands must not overlap: the new minimum exceeds the previous maximum, so an upgrade always improves its raw value.
- Rank investment belongs to the slot. Reforge changes its affix but keeps that rank and rolls in the replacement affix's corresponding band. Refine stays within the current band. Promotion adds a rank-I slot; it never downgrades existing slots or spends milestone credits.
- Upgrades cost the earned credit only, with no material fee or chance to fail. No respec/refund mechanism is assumed for launch; allocation remains with the slot when reforged.

## Example, pending balance review

Fire Focus bands: I = 4–8%; II = 9–12%; III = 13–16%; IV = 17–20%; V = 21–24%. These illustrate the interaction, not final combat numbers. Every affix requires its own rank table; do not apply this damage table to healing or mitigation automatically.

The original +25% damage / 15% mitigation / +15% healing channel caps were designed for base-rank affixes (raised to 100 / 50 / 50 on 2026-10-05; still provisional). They must be revisited alongside rank tables. Do not present a raw-value increase as an effective upgrade if clipping at a cap eliminates the benefit. Preview the effective change under applicable conditions, and clearly disclose shared caps. Numerical balancing is outstanding.

## Screen flow

After the battle and any move-learning/evolution/capture screens finish, present `Growlithe — Level 30 — 1 upgrade available`. List occupied slots with current rank/value, next rank/range, effect description and capped status. Selecting a slot opens one explicit confirmation, for example `Fire Focus I +7% → Fire Focus II 9–12%`.

The player can select `Decide later`; the summary keeps a pending-upgrade badge and an action to reopen the screen. Several available credits are allocated one at a time. Max-rank slots are visibly unavailable; pending credits are retained if every current slot is capped. The screen never interrupts an active turn or displaces required native screens.

## Reforge uses the same selection flow

Owner follow-up: this selection experience must also apply when using reforge currency. Interpretation for the design: use the same affix-selection screen in Reforge mode, rather than letting currency use silently choose a random slot. This is a reforge interaction, not an additional level-milestone credit grant.

Using reforge currency opens the selected Pokémon's affix screen without spending yet. The player selects one occupied slot, reviews its current affix/rank, the eligible replacement pool, corresponding rank ranges and the cost, then confirms. Confirmation replaces that affix and rolls its value in the replacement definition's range at the slot's existing rank. Other slots, milestone credits and invested ranks remain unchanged. Closing or cancelling costs nothing.

After the result reveal, return to the updated selection screen. Any existing pending milestone credits remain accessible in Upgrade mode. Repeating a reforge requires a fresh confirmed paid operation; it does not advance the slot's rank or generate a free upgrade choice. Upgrade mode advances rank using a milestone credit; Reforge mode changes identity/value using currency. Preserve the same operation-ID, revision and atomic-commit requirements for both.

## Persistence and implementation requirements

Add slot identities/ranks, an earned milestone ledger and spent-credit count to the production schema. A unique operation ID plus expected profile revision guards confirmation. Rank/value update, credit consumption and result are committed atomically; duplicate requests and crash recovery return the same result. Client animation/selection never awards milestones or decides the random result.

Acceptance cases: 9→10, 19→31, first level-37/100 capture, repeated acquisition callback, level reduction and regaining, trade/restart, pending upgrade plus evolution, promotion while credits are pending, reforge/refine preserving slot rank, maximum-rank selection, concurrent confirmation, and crash before/after commit. Preserve the existing schema-zero data until an explicit migration is available.
