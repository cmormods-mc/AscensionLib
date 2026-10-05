# Review guide

The chosen direction is a standalone turn-based Pokémon ARPG: capture rarity creates excitement, affixes create builds, and guaranteed promotion lets a favorite Pokémon remain useful.

## Decisions ready for owner evaluation

| Decision | Chosen default | Why |
|---|---|---|
| Rarity | Six grades, 55/28/12/4/0.9/0.1 percent | Frequent useful upgrades and rare aspirational captures. |
| Long-term value | Every Pokémon can reach Mythical | Low rarity does not permanently invalidate a favorite. |
| Rarity power | One to six affix slots; no universal stat multiplier | More build coverage without replacing native stats. |
| Crafting | Selected-slot reforge/refine and guaranteed promotion | Players choose what to improve and cannot brick a companion. |
| Materials | Three account-bound wallet balances | Atomic craft accounting; no dropped-currency inventory edge cases. |
| Standalone progression | Research plus free ranked solo Trials | The overhaul remains complete without CobbleRaids. |
| Battle balance | PvE-only bonuses, +100% direct damage / 50% reduction / +50% move healing caps (raised from 25/15/15 on 2026-10-05) | Provisional; sized so rank V and powerhouse affixes are not clipped. Re-validate against native interactions. |
| Pacing | 20 full encounter rewards/day; reduced Dust afterward | Visible bounded farming; configurable after playtests. |
| Bad luck | Guaranteed promotion and fourth eligible top-rank win Core guarantee | Long-term progress does not require winning an extreme random tail. |
| Integration | Separate optional adapter later | Current work stays focused on the standalone codebase. |

The most consequential tuning choices are slot growth, daily reward budget, wallet binding and how much conditional percentage bonuses alone change battles. These are intentionally easy to review and adjust before building the full progression service.

## Why the plan does not simply edit IVs

The desired effects need to respect the authoritative turn-based battle simulator. Mutating arbitrary Pokémon tags does not establish a working damage rule, and raising native IV/EV limits would alter existing progression semantics. Persistent metadata, domain rules and simulator application are separate responsibilities in the plan.

## Numerical implications

At the proposed Mythical rate, 1,000 captures have about a 63% chance to yield at least one Mythical. The guaranteed promotion path prevents that random tail from becoming the only route. Full Common-to-Mythical materials cost 770 Dust, 37 Facets and 7 Cores; this is a tuning proposal, not measured playtime.

The [calculated results](BALANCE-RESULTS.md) spell out assumptions. The [build status](BUILD-STATUS.md) distinguishes tested domain code from live gameplay that still needs proof.

## Recommended next implementation order

1. Prove capture → PC → restart → evolution → trade on a development world.
2. Implement one real simulator affix with correct immunity, multihit, substitute and drain behavior.
3. Implement canonical profile/wallet transactions and recovery before exposing crafting.
4. Add standalone Trials, research progression and player screens.
5. Integrate the separately maintained CobbleRaids build when requested.
