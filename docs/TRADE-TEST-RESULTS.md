# Trade test results (2026-10-24T01:14:15.553Z)

Both bots joined.
op TradeA: Nothing changed. The player already is an operator
op TradeB: Nothing changed. The player already is an operator
clearparty TradeA: Cleared TradeA's party.
clearparty TradeB: Cleared TradeB's party.
give A: Gave Bulbasaur to TradeA.
give B: Gave Charmander to TradeB.

## Setup: profile A's Bulbasaur (rare) and B's Charmander (epic), add attunement to A
  TradeA> /ascend admin acquire 1 rare
    Your client does not have the capture reveal channel.
  TradeB> /ascend admin acquire 1 epic
    Your client does not have the capture reveal channel.
  TradeA> /ascend admin attunement 1 60
    Slot 1 gained 60 attunement (now 60).
  TradeA> /ascend admin inspect 1
    Slot 1 · Rare · revision 2 · 2 upgrade(s) pending · attunement 60 · COMBAT EFFECTS INACTIVE | prefix:0 · Special Force I: 5% [3–6%] · Your special moves deal more damage. | prefix:1 · Type Focus I (poison): 8% [4–8%] · Your moves of the chosen type deal more damage. | suffix:0 · Physical Bulwark I: 4% [3–6%] · You take less damage from physical moves.
  TradeB> /ascend admin inspect 1
    Slot 1 · Epic · revision 1 · 2 upgrade(s) pending · attunement 0 · COMBAT EFFECTS INACTIVE | prefix:0 · Type Focus I (fire): 7% [4–8%] · Your moves of the chosen type deal more damage. | prefix:1 · Rending I: 13% [10–20%] · 30% chance per hit to cause bleeding: 1/12 max HP a turn, stacks to 3, lasts 3 turns. This boosts it. | suffix:0 · Type Ward I (flying): 5% [3–6%] · You take less damage from moves of the chosen type. | suffix:1 · Restorative I: 11% [5–15%] · Healing and draining moves you use restore more HP.

## Test 1: trade A slot 1 <-> B slot 1
trade: Trade started; watch the log for TRADE-TEST and run inspect afterwards.
server log:
[21:13:32] [Server thread/INFO]: TRADE-TEST start: TradeA gives afd61151-2842-4004-8e7e-559038d27c42 (Bulbasaur) and TradeB gives 1fb70b29-9c7b-43b6-8301-3ce862cb88ab (Charmander)
[21:14:31] [Server thread/INFO]: TRADE-TEST start: TradeA gives 27e38178-05d3-459e-b0ad-8d7fae925d1c (Bulbasaur) and TradeB gives 863f8266-f24e-4bb4-bb5a-c48b0ee55185 (Charmander)
  TradeA> /ascend admin inspect 1
    Slot 1 · Epic · revision 1 · 2 upgrade(s) pending · attunement 0 · COMBAT EFFECTS INACTIVE | prefix:0 · Type Focus I (fire): 7% [4–8%] · Your moves of the chosen type deal more damage. | prefix:1 · Rending I: 13% [10–20%] · 30% chance per hit to cause bleeding: 1/12 max HP a turn, stacks to 3, lasts 3 turns. This boosts it. | suffix:0 · Type Ward I (flying): 5% [3–6%] · You take less damage from moves of the chosen type. | suffix:1 · Restorative I: 11% [5–15%] · Healing and draining moves you use restore more HP.
  TradeB> /ascend admin inspect 1
    Slot 1 · Rare · revision 2 · 2 upgrade(s) pending · attunement 60 · COMBAT EFFECTS INACTIVE | prefix:0 · Special Force I: 5% [3–6%] · Your special moves deal more damage. | prefix:1 · Type Focus I (poison): 8% [4–8%] · Your moves of the chosen type deal more damage. | suffix:0 · Physical Bulwark I: 4% [3–6%] · You take less damage from physical moves.

## Verdict (text-compared; UUIDs are in the server log)
inspect works before and after: true
A now has B's old profile (identical text): true
B now has A's old profile (identical text, incl. attunement 60): true

## Test 2: new owner crafts (attunement spend / sigil path not available to bots; checked via inspect only)

## Test 6 (wallet is separate)
  TradeA> /ascend admin wallet
    Materials (wallet revision 0): resonance_dust=0 facet=0 ascension_core=0 unique_fragment=0 unique_catalyst=0 scouter=0 ascension_sigil=0 tower_mark=0 rank_respec=0 unique_respec=0
  TradeB> /ascend admin wallet
    Materials (wallet revision 0): resonance_dust=0 facet=0 ascension_core=0 unique_fragment=0 unique_catalyst=0 scouter=0 ascension_sigil=0 tower_mark=0 rank_respec=0 unique_respec=0

## After a server restart
Both bots rejoined and ran `/ascend admin inspect 1`:
- TradeA: Epic, revision 1, attunement 0 (the profile Charmander had before the trade).
- TradeB: Rare, revision 2, attunement 60 (the profile Bulbasaur had before the trade).
The traded profiles survived the restart.

## What this run does and does not show
- The swap ran through Cobblemon's real `ActiveTrade.performTrade` / `TradeManager.performTrade` (server side). The trade screen and its animation were not used; the test command ends the animation step itself.
- Passed: profile, ranks, credits, revision and attunement follow the Pokemon, in both directions, and persist across a restart.
- Wallet balances were all zero on both sides, so "wallet stays separate" is not proven by this run.
- Not tested: crafting by the new owner, Hourglass/Prism after a trade, locked rentals, GTS/auction clones, a trade while a craft is pending.
- Setup: Java 21, `-Dascensionlib.testTrade=true`, two mineflayer bots (resource pack answered by hand, one unparseable modded packet dropped), Pokemon given with `givepokemon`, profiles added with `/ascend admin acquire`.

# Craft after trade (2026-10-24T01:20:07.087Z)

  TradeA> /ascend admin acquire 1 rare
    Your client does not have the capture reveal channel.
  TradeB> /ascend admin acquire 1 epic
    Your client does not have the capture reveal channel.
  TradeA> /ascend admin attunement 1 60
    Slot 1 gained 60 attunement (now 60).
grant A resonance_dust: Granted 500 resonance_dust to TradeA.
grant A facet: Granted 500 facet to TradeA.
grant A rank_respec: Granted 1 rank_respec to TradeA.

## A crafts an upgrade on its own Bulbasaur (before the trade)
  TradeA> /ascendtestcraft TradeA 1 upgrade "prefix:0"
    (no reply)
    [21:20:27] [Server thread/INFO]: CRAFT-TEST upgrade prefix:0 on e7976b84-c8bd-4349-bb8f-362a27a4142a slot 1 by TradeA
    [21:20:27] [Server thread/INFO]: upgrade committed on Pokemon e7976b84-c8bd-4349-bb8f-362a27a4142a slot prefix:0 by TradeA (operation 43a603d3-70c3-4db2-b6ef-044b05b8a61a)
A's Pokemon UUID: e7976b84-c8bd-4349-bb8f-362a27a4142a
  TradeA> /ascend admin wallet
    Materials (wallet revision 3): resonance_dust=500 facet=500 ascension_core=0 unique_fragment=0 unique_catalyst=0 scouter=0 ascension_sigil=0 tower_mark=0 rank_respec=1 unique_respec=0

## Trade A slot 1 <-> B slot 1
Trade started; watch the log for TRADE-TEST and run inspect afterwards.

## Old owner A tries to craft the Pokemon it traded away (must be refused)
result: 
    (no server log line; see BalanceLog / refusal line)


## New owner B crafts on the Pokemon it received (needs B to have funds)
grant B resonance_dust: Granted 500 resonance_dust to TradeB.
grant B facet: Granted 500 facet to TradeB.
grant B rank_respec: Granted 1 rank_respec to TradeB.
  TradeB> /ascend admin wallet
    Materials (wallet revision 3): resonance_dust=500 facet=500 ascension_core=0 unique_fragment=0 unique_catalyst=0 scouter=0 ascension_sigil=0 tower_mark=0 rank_respec=1 unique_respec=0
  TradeB> /ascendtestcraft TradeB 1 upgrade "prefix:1"
    (no reply)
    [21:20:27] [Server thread/INFO]: CRAFT-TEST upgrade prefix:0 on e7976b84-c8bd-4349-bb8f-362a27a4142a slot 1 by TradeA
    [21:20:43] [Server thread/INFO]: CRAFT-TEST upgrade prefix:1 on e7976b84-c8bd-4349-bb8f-362a27a4142a slot 1 by TradeB
    [21:20:27] [Server thread/INFO]: upgrade committed on Pokemon e7976b84-c8bd-4349-bb8f-362a27a4142a slot prefix:0 by TradeA (operation 43a603d3-70c3-4db2-b6ef-044b05b8a61a)
    [21:20:43] [Server thread/INFO]: upgrade committed on Pokemon e7976b84-c8bd-4349-bb8f-362a27a4142a slot prefix:1 by TradeB (operation e9379621-9a50-4e05-bffe-02833f0d1433)
  TradeB> /ascend admin wallet
    Materials (wallet revision 3): resonance_dust=500 facet=500 ascension_core=0 unique_fragment=0 unique_catalyst=0 scouter=0 ascension_sigil=0 tower_mark=0 rank_respec=1 unique_respec=0
  TradeA> /ascend admin wallet
    Materials (wallet revision 3): resonance_dust=500 facet=500 ascension_core=0 unique_fragment=0 unique_catalyst=0 scouter=0 ascension_sigil=0 tower_mark=0 rank_respec=1 unique_respec=0

A wallet unchanged by B's craft: true

## Hourglass of Undoing on the traded Pokemon (B, never Overreached)
  TradeB> /ascendtestcraft TradeB 1 respec_ranks "-"
    (no reply)
    [21:20:51] [Server thread/INFO]: respec_ranks committed on Pokemon e7976b84-c8bd-4349-bb8f-362a27a4142a (2 credits returned) by TradeB (operation d60f791d-7196-4749-85c9-70420da03f9e)
  TradeB> /ascend admin inspect 1
    Slot 1 · Rare · revision 5 · 2 upgrade(s) pending · attunement 60 · COMBAT EFFECTS INACTIVE | prefix:0 · Physical Force I: 6% [3–6%] · Your physical moves deal more damage. | prefix:1 · Rending I: 13% [10–20%] · 30% chance per hit to cause bleeding: 1/12 max H

## Summary of the craft-after-trade run (verified)
Pokemon e7976b84-c8bd-4349-bb8f-362a27a4142a (A's Bulbasaur, Rare, attunement 60):
- Before the trade A crafted an upgrade on it: `upgrade committed ... by TradeA`.
- After the trade (same UUID, now in B's party) A tried to craft it by UUID and was refused: telemetry `craft_refused` with "That Pokémon is no longer yours to change."
- B then crafted an upgrade on it (`upgrade committed ... by TradeB`) and used the Hourglass path (`respec_ranks committed ... 2 credits returned ... by TradeB`).
- Not conclusive: the upgrade costs credits, not materials, so the wallet check ("A's wallet unchanged by B's craft") proves little. The wallet was not otherwise compared before and after the Hourglass.
- Still untested: locked rentals (`ascensionlib_craft_locked`), Prism, GTS/auction clones, a trade during a pending craft.

# Locked rental, Prism and wallets after a trade (2026-10-24T01:23:35.286Z)

## Part 1: the Prism after a trade

- PASS: A installed a Unique (inspect shows "Unique:")
  wallets before trade
    A: resonance_dust=500 facet=500 ascension_core=0 unique_fragment=0 unique_catalyst=1 scouter=0 ascension_sigil=0 tower_mark=4 rank_respec=1 unique_respec=0
    B: resonance_dust=500 facet=500 ascension_core=0 unique_fragment=0 unique_catalyst=0 scouter=0 ascension_sigil=0 tower_mark=5 rank_respec=0 unique_respec=1
Trade started; watch the log for TRADE-TEST and run inspect afterwards.

  wallets after B's Prism
    A: resonance_dust=500 facet=500 ascension_core=0 unique_fragment=0 unique_catalyst=1 scouter=0 ascension_sigil=0 tower_mark=4 rank_respec=1 unique_respec=0
    B: resonance_dust=500 facet=500 ascension_core=0 unique_fragment=0 unique_catalyst=0 scouter=0 ascension_sigil=0 tower_mark=5 rank_respec=0 unique_respec=0
- PASS: B (new owner) swapped the Unique with a Prism: inspect shows Rupture
- PASS: B's Prism was spent (unique_respec 1 -> 0)
- PASS: A's wallet untouched by B's Prism

## Part 2: a locked rental stays locked through a trade
    [Server thread/INFO]: LOCK-TEST locked ee61cac5-5473-4da5-bf39-7d78e84594b3 for TradeA
  locked, crafted by its holder (must be refused):
    {"t":1792805064693,"type":"craft_refused","kind":"upgrade","player":"0fbf69a0ab8d","slot":"prefix:0","reason":"This Pokemon is on loan and cannot be upgraded."}
Trade started; watch the log for TRADE-TEST and run inspect afterwards.
  after the trade, the new holder (must also be refused: on loan):
    {"t":1792805064693,"type":"craft_refused","kind":"upgrade","player":"0fbf69a0ab8d","slot":"prefix:0","reason":"This Pokemon is on loan and cannot be upgraded."}
    {"t":1792805072857,"type":"craft_refused","kind":"upgrade","player":"8f077486f189","slot":"prefix:0","reason":"This Pokemon is on loan and cannot be upgraded."}
- PASS: lock refusal recorded for the original holder and for the new holder
- PASS: the Pokemon A received in exchange (unlocked) can still be crafted

## Server-log confirmation of the Prism and rental run
- 047801d0 (A's Bulbasaur): A installed `stormcaller` (install_unique, Catalyst + 1 Mark). After the trade B ran `respec_unique` to `rupture` (committed, B's Prism 1 -> 0). A's wallet was identical before and after.
- ee61cac5 (Charmander) was marked on loan with the `ascensionlib_craft_locked` tag. Its holder was refused; after the trade (it moved to B) B was refused too ("on loan"). The tag travels with the Pokemon through a Cobblemon trade.
- The Pokemon A received in exchange (047801d0 again, unlocked) was crafted by A afterwards (`upgrade committed ... by TradeA`).
- Not testable here: GTS/auction clone behavior (no such mod on the integration server); a real trade-screen trade (needs a human client).

# SnobbleAuctions storage sequence vs. profiles (2026-10-24T01:31:56.525Z)

## Sale: A lists its Bulbasaur (NBT escrow), B receives it

    [Server thread/INFO]: ESCROW-TEST listed 8bde18ef-5750-4d7b-8055-d7c53ddae19d from TradeA (removed=true, 1468 bytes of NBT)
    [Server thread/INFO]: ESCROW-TEST delivered 8bde18ef-5750-4d7b-8055-d7c53ddae19d to TradeB (added=true, same uuid=true, owner=99860106-7809-33c7-bc28-e627cf43269f)
- PASS: B holds the same profile (identical inspect text, attunement 60)
- PASS: same UUID and an owner set after delivery
seller A tries to craft it by UUID (must be refused): 
- PASS: seller refused
- PASS: buyer B can craft it

## Cancel: B lists it and takes it back (same player)

- PASS: profile identical after a list-and-cancel round trip

## A loaned (locked) Pokemon sold through the auction

    {"t":1792805557621,"type":"craft_refused","kind":"upgrade","player":"8f077486f189","slot":"prefix:1","reason":"This Pokemon is on loan and cannot be upgraded."}
    {"t":1792805564240,"type":"craft_refused","kind":"upgrade","player":"0fbf69a0ab8d","slot":"prefix:1","reason":"This Pokemon is on loan and cannot be upgraded."}
- PASS: the lock survives the NBT round trip (buyer refused: on loan)

## SnobbleAuctions (read from snobbleauctions.zip, not run)
- Its storage steps (`CobblemonPokemon.java`): `saveToNBT` -> gzip/base64, `party.remove`, later `new Pokemon().loadFromNBT` -> `party.add` (or PC). Delivery is skipped if the UUID is already in the buyer's party or PC.
- Reproduced exactly (without money or the page) by `/ascendtestescrow`: the UUID is unchanged, the profile follows the buyer (identical inspect, attunement 60), the seller is refused afterwards, the buyer can craft, a list-and-cancel round trip leaves the profile identical, and the loan lock tag survives.
- Not run: the mod itself (needs CobbleDollars and a WebGUI client), crash windows in its purchase journal, expiry, offline delivery. It lists Pokemon only, so the voucher listing-fee request in docs/AUCTION-LISTING-FEE-REQUEST.md does not apply to it.

## SnobbleAuctions: crash windows (from reading AuctionService.java; not reproduced)
- The listing file is saved synchronously at every step, but Cobblemon saves party storage on its own timer (`pokemonSaveIntervalSeconds`, 30 in the integration server's config) and at shutdown. The two are not saved together.
- Hard crash within ~30 s after a listing: the file says ACTIVE (Pokemon in escrow) while the seller's party on disk may still hold it. After restart both exist: a duplicate with one UUID, and one AscensionLib profile for both.
- Hard crash within ~30 s after a purchase: the file says delivered and the seller paid, but the buyer's party on disk may lack the Pokemon: the buyer is charged and loses it.
- Recovery handles the other orderings correctly (it checks the party and PC by UUID: PENDING_ESCROW is dropped when the Pokemon never left, restored when it did; a half-done purchase is finished or refunded).
- Suggested fix on the auction side: force Cobblemon to save the affected player's party before writing ACTIVE (listing) and before writing `pokemonDelivered` (purchase). A clean stop saves everything, so only a crash or kill is exposed.

## Hard-kill test of the storage window (run on the integration server only)
Setup: A's Rare Bulbasaur (attunement 60) and B's Epic Charmander saved for 45 s, then the auction's move (NBT out, remove from A, NBT in, add to B) done in memory, then `taskkill /F` on the server.
- Kill 0.35 s and 0.40 s after the move (two trials): after restart A still had the Bulbasaur with its profile, B had only its Charmander. The move was not on disk.
- Kill 7 s after the move (one trial): the move had been saved (B held both).
So Cobblemon storage is not written at the moment of a change; a kill inside the window loses the change. With the auction's own file saved at once (per the code), a crash right after a listing leaves the Pokemon with the seller and in the listing (a duplicate, one UUID, one profile); right after a purchase it leaves the buyer charged without the Pokemon. This test did not run the auction mod; it shows the storage half of the argument.
