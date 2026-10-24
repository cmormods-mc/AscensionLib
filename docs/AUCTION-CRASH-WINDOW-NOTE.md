# Note for the auction mod (SnobbleAuctions 1.0.0): a crash can duplicate or lose a Pokémon

Found while checking that AscensionLib profiles survive an auction (they do: see `TRADE-TEST-RESULTS.md`). Nothing to build in AscensionLib; this is for whoever maintains
SnobbleAuctions. Written 2026-10-23 from reading `AuctionService.java` and `CobblemonPokemon.java` and from a kill test on a throwaway server. The mod itself was **not run**.

## The problem in one line
`listings.json` is saved at once at every step, but Cobblemon does not write a player's party at the moment it changes. After a hard crash (kill, power loss, JVM crash)
the two can disagree.

## Evidence
Cobblemon saves party and PC data on a timer (`pokemonSaveIntervalSeconds`, default 30) and at shutdown. Kill test on a Cobblemon 1.8.1 server: two players, A holds a Pokémon
and B holds another, both saved for 45 s. Then the auction's own sequence was run in memory (save to NBT, `party.remove`, `loadFromNBT`, `party.add` to B) and the server
process was killed.

| Kill after the move | Party data on disk after restart |
|---|---|
| 0.35 s | A still has its Pokémon, B does not |
| 0.40 s | same |
| 7 s | the move was saved |

So a move is lost when the process dies shortly after it (up to about the save interval).

## What that does to an auction
1. **Listing, then a crash.** `createListing` saves `ACTIVE` with the full NBT and then calls `removeFromParty`. If the crash comes before Cobblemon writes the seller's party,
   the seller restarts with the Pokémon still in the party **and** the listing holds a copy. Recovery does not help: `recoverEscrow` only looks at `PENDING_ESCROW`, and this
   listing is already `ACTIVE`. Two copies with the same UUID can exist (the seller keeps one, a buyer gets the other). Anything keyed by UUID (AscensionLib's profile, for one) then
   has one record for two Pokémon.
2. **Purchase, then a crash.** `purchase` saves `pokemonDelivered = true` right after `deliver`. If the crash comes before the buyer's party is written, the buyer restarts charged
   and without the Pokémon, and the journal says the delivery is done, so recovery does not repeat it. The Pokémon is gone from both sides.

A clean `stop` saves everything, so only a crash or a kill is exposed. The window is short, but the loss is permanent and the duplicate is exploitable by anyone who can crash the server
(or time a kill) right after listing.

## Suggested fixes (smallest first)
1. **Reconcile against real storage at start-up and on seller join** (needs no Cobblemon internals). For every listing the file says the auction holds (`ACTIVE`, `EXPIRED`,
   `CANCELLED`, `PURCHASING`), ask `holds(seller, pokemonUuid)`. If it says `YES`, the Pokémon is also in the seller's storage: remove it from the party or PC (the listing is the
   authority) and log it. For listings that are `SOLD` but recent (say the last 5 minutes before the crash), ask `holds(buyer, pokemonUuid)`; if `NO` and the buyer is online,
   `deliver` again. `deliver` is already safe to repeat because it checks the UUID first.
2. **Make Cobblemon write first** (narrows the window, version-fragile). Cobblemon's `FileBackedPokemonStoreFactory` has `save(PokemonStore, RegistryAccess)`, but the list of factories
   in `PokemonStoreManager` is private, so reaching it means reflection. If you do it: save the seller's party after `removeFromParty` and before writing `ACTIVE`, and save the buyer's
   party after `deliver` and before writing `pokemonDelivered`. Treat a failed save as a failed step.
3. **Order the listing so a crash is harmless.** Write `PENDING_ESCROW`, remove the Pokémon, force the save, only then write `ACTIVE` (this keeps `recoverEscrow` meaningful, because it
   then runs for every crash before the save).

Fix 1 alone closes both cases on the next start. Fix 2 or 3 stops the bad state from existing.

## How to reproduce (throwaway server only)
Run a server with two test players, give each a Pokémon, wait over 30 s, then do a listing or purchase and kill the server process within a second (`taskkill /F /PID <pid>` on
Windows). Restart and compare the parties with `listings.json`.

## Not checked
The auction mod's own recovery code was read, not executed. Whether `CobbleDollars` balances have a similar write delay was not looked at. Expiry over days, offline delivery, and a full PC
refund are untested (the mod's guide says so too).
