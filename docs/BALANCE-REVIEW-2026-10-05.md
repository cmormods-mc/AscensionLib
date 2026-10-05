# Balance review (2026-10-05): findings for owner approval

Arithmetic only (from `BALANCE-RESULTS.md` and the live reward bands); no gameplay data. Nothing below is approved yet.

## Pace to promote one Pokemon Common -> Mythical (770 dust, 37 facets, 7 cores, 90 attunement)

| Source | Per run / clear | Runs for dust | Runs for facets | Runs for cores | Runs for attunement |
|---|---|---:|---:|---:|---:|
| Tower run to F10 (F5 + F10 bosses) | 34.8 dust, 1.5 facets, 0.15 cores, 6 attunement | 22 | 25 | **47** | 15 |
| Rank-3 Trial (10+ floors) | 16 dust, 2 facets, no core, 3 attunement | 48 | **19** | never | 30 |
| Rank-2 Trial (5-9 floors) | 10 dust, 1 facet, 3 attunement | 77 | 37 | never | 30 |

At about 14 keyed Tower runs a week, dust takes ~1.6 weeks, facets ~1.8 weeks and **cores ~3.3 weeks**: cores are the bottleneck to Mythic, and
the Tower is the only core source (Trials give none). Attunement is not the limit.

## Findings

1. **Rank-3 Trials out-earn a Tower run on facets** (2 per clear against 1.5) and need no key and have no daily budget. Facets for a full
   Mythic arrive in 19 trial clears (about 4 hours at the assumed 12 minutes) against 25 keyed Tower runs. This is the farming risk that the
   spec's 20-per-day budget was meant to bound. Either shrink rank 3 (suggest 1 facet, matching a Tower run's rate per run) or build the budget.
2. **Cores are scarce by design**: 0.15 per run puts Mythic at ~47 runs. If that is too slow, raise the boss core chance (7.5% per boss) rather
   than adding a trial core source.
3. **Mythic odds**: 0.10% per capture, 63% after 1,000 captures (no capture pity). Promotion is the guaranteed route; fine if that is intended.
4. **Damage envelope**: 100/50/50 caps allow 2x outgoing damage and 2x effective HP when stacked; type_mastery alone reaches 12-20% at rank
   I-II and ~51-60% at rank V. The multiplicative stacking inside one channel makes reaching 100% need several rare affixes. No simulator
   playtest has tested a capped build; a live fight with a deliberately stacked team is the right check before approving the caps.
5. **Refine value spreads** are small for most affixes (3-6%), Restorative 5-15% (mean 11 attempts to the max): worth a second look only if
   players find Refine pointless.

## Proposed changes (each needs your yes)

| # | Change | Why |
|---|---|---|
| A | Rank-3 trial pays 1 facet instead of 2 (rank 2 stays 10 dust + 1 facet) | Stops trials beating the keyed tower on facets |
| B | Keep boss core chance at 7.5%, revisit after the live test | Cores are meant to gate Mythic; judge by feel |
| C | Keep 100/50/50 caps until a stacked-team live test | No measurement yet |
| D | Build the 20/day trial budget only if A is rejected | Avoids new store tables for now |
