# Respec items

Status: **design decided by the owner 2026-10-15, nothing built yet.** Numbers come from `tools/tower_loot_model.py --respec` (arithmetic only, no live data) and are tuned from live counters later.

## Decisions (owner, 2026-10-15)

| # | Decision |
|---|---|
| 1 | **Rank Respec resets the whole Pokemon**, with **no material refund** (dust and facets already spent on Perfecting stay spent). |
| 2 | **Overreach is a point of no return:** a Pokemon with an Overreach-sealed slot **cannot be Rank Respecced at all**. The preview says so before anyone gambles. |
| 3 | **Unique Respec** is a free Unique swap. |
| 4 | **No Transcendent Respec** (dropped). A Fusion stays irreversible. |
| 5 | Drops: a Rank Respec about **once every 10 Tower completions**, a Unique Respec about **once every 35**. |
| 6 | Both are **tradable** vouchers (3 percent withdrawal fee, like every currency). |
| 7 | A **Tower Marks route** for the Rank Respec, **properly priced** (below). |
| 8 | **Flavour names:** Hourglass of Undoing (Rank Respec), Unbinding Prism (Unique Respec). |

## What can be undone today, and what cannot

| Investment | Today | Why a respec is wanted |
|---|---|---|
| A slot's **rank** (I to V), bought with an upgrade credit (one per 10 levels, at most 10) | Permanent. An upgrade costs only the credit, so the credit is the whole price. | A wrong build is stuck for good. |
| **Perfecting** (a raised floor, paid in dust) | Permanent; only a Reforge of that slot clears it. | Sunk dust. |
| **Overreach** (a one-shot gamble that seals the slot) | Permanent, by design. | Stays a final commitment (decision 2). |
| The slot's **affix** | Reforge swaps one slot's affix. | Already covered; not what a respec is for. |
| The **Unique** | Replace needs 1 Catalyst and 1 Tower Mark. | A Catalyst is about 11 hours of fragments. |

Invariant that shapes the design: a profile's `spentUpgradeCredits` must equal the sum of (rank - 1) over its slots, so a rank reset must refund credits exactly.

## The two items

Both are new wallet materials (like Marks and Sigils), so they get art, a tooltip rarity, vouchers and the withdrawal fee for free. Neither can be used in a battle or on a lent Pokemon (`CraftLocks`), and each is spent in one
store transaction together with the change it makes, so a refusal or a crash never costs the item.

### Hourglass of Undoing (Rank Respec, epic)
Wipes the Pokemon's upgrade investment and gives the credits back to spend again.
- Every ordinary slot returns to **rank I**: its value is re-rolled inside the rank I band and its **perfected floor is cleared**. `spentUpgradeCredits` becomes 0, so every earned credit is pending again.
- **Kept:** the affixes themselves, the Unique, the rarity, attunement and the earned milestones. **Not refunded:** any dust or facets (decision 1).
- **Refused, and not consumed,** when any slot is Overreach-sealed (decision 2), when every slot is already rank I with no floor (nothing to undo), or when the Pokemon has no profile.
- Preview shows the ranks and floors that will be lost and the credits returned. It also notes that Fusion needs a host that has spent all its credits, so a respecced host must spend them again before it can be fused.

### Unbinding Prism (Unique Respec, legendary)
Replaces the Pokemon's Unique with a different one **without a Catalyst or a Mark** (the normal Replace costs both). Same picker and rules as Replace: a different Unique, and not on a Transcendent. Its value is about one Catalyst plus a Mark,
so it is kept rarer than a Catalyst drop.

## Where they come from

The Tower crown table (`TowerCompletionBands`) gains two entries. Weights are on the crown table's scale (the real table multiplies every weight by 10 so fractions fit as integers); a crown slot rolls one entry. Model, 1 completion = 1 hour:

| Crown entry | Bronze | Silver | Gold | Mythic | Result |
|---|---:|---:|---:|---:|---|
| Hourglass of Undoing | 0 | 6 | 8 | 10 | **one every 10.6 completions** |
| Unbinding Prism | 0 | 0 | 3 | 5 | **one every 34.3 completions** |

Cost to everything else: cores go from 0.59 to 0.54 an hour (Mythic promotion stays attunement-bound at 15 hours; cores still arrive in about 13); Catalysts stay at 0.05 an hour. Nothing else pays these (not dungeons, not trials).

### The Tower Marks route (Hourglass of Undoing only)
**90 Tower Marks** for one Hourglass. Marks arrive at 4.5 an hour, so that is about **20 hours of Tower play**, twice the average wait for a drop (10.6 hours): a deterministic path that is deliberately worse than luck on average, so drops stay valuable
and the Marks stay a sink. The price is one constant. The Hourglass is a tradable voucher, so this converts personally earned (bound) Marks into a tradable item; at 20 hours of play per Hourglass that conversion is the exchange rate, so watch the market price
and raise the Marks price if Hourglasses sell for less than about 20 hours of Tower time.

## Presentation
- A **Respec** tab on the craft page: the two items with how many you hold, the Pokemon's current investment, a **preview of exactly what changes** (and, for the Hourglass, the Overreach refusal and the Marks price), then the usual confirm.
- The completion screen already lists crown items by name and icon; the two get their own icons and rarity colours. A party announcement for a Unique Respec, like the Core and Catalyst ones.
- Art in the same style (`tools/pixel_art.py`): an hourglass whose sand runs upward, a cracked prism.

## Build plan
1. **Domain:** `RankedProgression.respecRanks` (the credit invariant, Overreach refusal), `replaceUnique` with a free path, two new `MaterialId`s (`rank_respec`, `unique_respec`), `design/currency-items.json` entries (rarity, kind, art), the crown weights in `TowerCompletionBands` scaled to integers, the Marks price constant. Tests: the credit invariant after a reset, a sealed slot refuses and keeps the item, nothing-to-reset refusal, a free swap spends only the item, no material refund, seeded crown rates match this document.
2. **Store:** new `Kind`s `RESPEC_RANKS` and `RESPEC_UNIQUE` (and `BUY_RESPEC` for the Marks route), each one transaction with the item debit, idempotent per operation ID. No schema change expected.
3. **Mod:** the craft page Respec tab (preview and confirm), messages, announcement line, wallet page rows and icons, the pack art, the Marks purchase button.
4. **Tune** from live item counters and a market check once vouchers trade.
