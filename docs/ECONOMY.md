# Economy and numerical proposal

Status: untested starting balance. Values here are mirrored in `design/balance.json` and evaluated by `tools/analyze_balance.py`. Human playtime and retention cannot be proven by a numerical model.

## Materials and ownership

Store crafting materials in a server-authoritative wallet keyed by player UUID in release one. This avoids dropped-item loss and inventory-full ambiguity. Materials are account-bound and cannot be traded; Pokémon remain tradable with their affixes intact. Physical, tradable currency items can be reconsidered only with a duplication and transaction design. Display the wallet in the bench and inspect/help commands.

Do not add a second balance to CobbleRaids White Gold or spend its existing currency. Additional raid materials are explicit integration rewards so the existing economy remains independent.

| Material | Typical source | Use |
|---|---|---|
| Resonance Dust | Daily research, every qualifying Trial/raid | All crafts |
| Facet | Trials rank 2–3; eligible raid reward band | Reforge and higher promotions |
| Ascension Core | Trial rank 3; top supported raid reward band | Legendary/Mythical promotions |

Daily research: award 4 Dust for each of the first five distinct species captured per player per UTC day, at most 20 Dust/day. Count species identity, not form, shiny state or rarity. Only confirmed ordinary wild captures qualify. Eggs, trades, imports, admin grants and raid rewards do not. Persist species/day ledger and deduplicate by capture/Pokémon identity. Operator clock changes cannot create duplicate rewards for a previously recorded day. A research reset is a server economy rule; display its next reset in the client's local time.

This bounded stipend intentionally tolerates repeatable capture setups. It is not a claim that Minecraft spawning can be made bot-proof. Multiple accounts remain an administrative concern; no invasive hardware/account tracking is proposed.

## Crafts

| Action | Dust | Facets | Cores | Result |
|---|---:|---:|---:|---|
| Refine one affix value | 12 | 0 | 0 | Uniform reroll, may be worse/same |
| Reforge one selected affix | 24 | 1 | 0 | Eligible family/parameter/value reroll |
| Common → Uncommon | 20 | 0 | 0 | Add one suffix |
| Uncommon → Rare | 50 | 2 | 0 | Add one prefix |
| Rare → Epic | 100 | 5 | 0 | Add one suffix |
| Epic → Legendary | 200 | 10 | 2 | Add one prefix |
| Legendary → Mythical | 400 | 20 | 5 | Add one suffix |

Full Common-to-Mythical cost: 770 Dust, 37 Facets, 7 Cores. Rare capture starts at Rare, so earlier costs are skipped. Costs are consumed only after eligibility, ownership, lock and revision checks. There are no refunds for an honestly previewed random roll; technical failure follows journal recovery rather than silently losing materials.

## Attunement gates

Promotion requires lifetime attunement of 3 / 10 / 25 / 50 / 90 for Uncommon / Rare / Epic / Legendary / Mythical respectively. Attunement is not consumed. Every qualifying Trial or integrated raid victory gives each participating Pokémon 3 points, once per encounter. A Common Pokémon therefore needs at least 30 qualifying victories to become Mythical; reward budget and difficulty may extend that.

A natural high-rarity capture keeps its initial rarity without an attunement prerequisite. To promote further it must meet the destination's lifetime threshold. Trading does not reset or duplicate attunement. Initial release has no attunement bonus for rarity and no loss penalty.

## Repeatable Trial rewards

| Trial rank | Unlock | Proposed encounter | Victory payout | Assumed full clear cycle |
|---|---|---|---|---|
| 1 | First owned Pokémon and tutorial | Level 30, team of 2 | 6 Dust | 6 min |
| 2 | Five rank-1 victories | Level 60, team of 4 | 10 Dust, 1 Facet | 8 min |
| 3 | Five rank-2 victories | Level 100, team of 6 | 16 Dust, 2 Facets, 25% chance of 1 Core | 12 min |

Levels, teams and cycle times are initial design inputs. Use level eligibility warnings, no entry fee and no player Pokémon auto-scaling at launch. Trial lobby fully heals the challenge's battle copy; results do not duplicate held items or permanent Pokémon objects. If battle-copy correctness cannot be demonstrated, use real Pokémon with explicit standard healing before/after and test disconnect recovery; do not launch both modes. Normal overworld battles retain normal carryover. Opponent trainer Pokémon cannot be caught.

Rank-3 core bad-luck protection: on the fourth consecutive eligible rank-3 victory without a Core, grant one. A natural Core resets the miss counter to zero. The counter is per player and shared with raids mapped to that reward band; never reset on logout or restart. The effective expected Core rate is therefore greater than 25%, computed in the analysis report. The preview states both the base chance and guarantee.

To bound farm output, full rewards apply to the first 20 eligible rewarded Trials/raids per player per UTC day, sharing a persisted budget. Further wins give 25% of Dust (floor, minimum one), no Facets or Cores, no pity advancement, but normal 3 attunement. State remaining full-reward count at entry and reserve a budget slot at successful admission; release it on abort/defeat. A crash-safe reservation expires only after recovery resolves the encounter outcome. This cap is configurable and must be visible, not discovered after a win.

These values intentionally provide a guaranteed solo route. With no rerolls, rank-3 materials alone need at least 49 wins for 770 Dust, while Facets need 19. The research stipend reduces the Dust requirement. Getting stronger is paced primarily by Dust and access, not by an unbounded final Core drought. Actual time includes losses, unlocks and leveling and will exceed a perfect-clear calculation.

## Raid reward mapping

Do not map numeric raid tiers until the adapter reads the installed raid definitions. Operator configuration maps stable raid definition/tier identifiers to one of the three reward bands above. Default unmapped raids give no overhaul currency, with one startup diagnostic listing unmapped definitions. A provided sample mapping is a template, not silently enabled.

A qualifying participant must be an encounter participant under CobbleRaids rules and pass the same participation/once-only checks. Avoid inventing damage thresholds that exclude support teams; accept a legal action or received opposing action, and separately honor CobbleRaids' own eligibility restrictions. No bonus currency for landing the final hit.

Public raid reward integration must use an authoritative completion hook; scraping chat, listening for boss death entities or repeating reward commands is not acceptable. An owned Trial launched through the raid API can use its completion listener and disable native raid rewards unless intentionally enabled.

The initial catch rarity table is identical in raids and ordinary captures. Raids reward materials and species opportunities, not compulsory superior random rarity. Operators can opt into separate capture tables later, with explicit odds and economy review.

## Crafting randomness

Use server-generated random draws. Commit the drawn result once with its operation ID; retries return the committed result rather than sampling again. Reject attempts with stale wallet/Pokémon revisions. Reforge retains the selected slot category and excludes all families present in other slots. Include the selected slot's old family in the pool, so a same-family outcome is possible.

Typed parameter choice occurs after family selection, uniformly among eligible current types. This means eighteen typed variants do not get eighteen times the probability of a generic affix. Numerical values are integer percentages, uniform inclusive. A Common Pokémon can roll a maximum-value affix; rarity does not secretly alter value ranges.

Server configuration may change weights/costs, but changes are versioned and shown to clients before confirmation. Do not retroactively reroll existing Pokémon when weights change.

## Economic boundaries

Research and reward payouts are material sources; crafting and promotions are sinks. No refund/salvage source exists, so alternating promotion, trade and reforge cannot create a positive currency loop. Trading a progressed Pokémon transfers saved investment, intentionally. Account-bound materials do not prevent indirect economic transfers through Pokémon.

No progression-based consumable is spent just to inspect, try a Trial or lose a battle. No daily login streaks or time-limited power. No mandatory seasonal reset. Maintain operator grant/revoke/audit commands with permission checks and reason fields.

## What must be tuned in playtests

Track clear time and win rate by Trial rank, materials earned/spent per active hour, craft distribution, tier distribution, chosen affix families, and the proportion of play spent recapturing versus developing. Collect only server-local aggregate counters by default. Never upload player data automatically.

Test cohorts: new solo player, veteran with existing level-100 team, raid-only participant, mixed group, breeder and trader. The intended 30–60 minute first reforge requires rank-2 access or raid Facets; new players may take longer because leveling and unlocks are excluded from the model. For new-player onboarding, award one account-bound Facet once after the first five research species, allowing the first reforge using the daily stipend plus one rank-1 win. This one-time milestone is per UUID and persisted.
