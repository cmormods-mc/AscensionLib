# Changelog

## 1.0.0-beta.1

The first public beta. Server-side Fabric mod for Minecraft 1.21.1 with Cobblemon 1.8.1 or 1.8.2; clients need WebGUI and Rinku.

### Progression
- Every captured or hatched Pokémon gets a rarity (Common to Mythic) and modifiers; 34 modifiers with ranks I to V.
- Upgrade, Refine, Reforge, Perfect (a raised floor), Overreach (a one-shot gamble that seals the slot) and Promote.
- Uniques (Catalyst plus a Tower Mark) and Fusion into Transcendents.
- Attunement goes to the Pokémon that actually fought, and a Pokémon with no profile is told why and can be given one with an Ascension Sigil.
- Rare respec items: the Hourglass of Undoing (resets ranks, returns credits; refused on a Pokémon that has Overreached) and the Unbinding Prism (a free Unique swap).

### Economy
- A per-player wallet of eight materials, also as real items with resource-pack art: tradable vouchers (a 3% withdrawal fee, rounded up) and loot that joins the wallet when carried. Tower Marks are bound.
- Tower completion chests: a guaranteed pile, a quality tier from challenge cards, Ascension cycle and a clean run, and a crown of notable items; a floor-5 mini chest; a daily limit of 20 full chests, then 25% dust.
- The Hourglass can be bought with 90 Tower Marks.

### Screens (WebGUI pages)
- Craft, Fusion, Inspect, Wallet, the AscensionDex (party, wallet and guide tabs), the completion reveal and an overlay (capture cards, scouting panel, bleed badge).
- Pages scale to the screen and each player sizes them (60 to 160 percent, saved on the server; `/ascend ui`).
- An in-game guide (`/ascend guide`) and a wiki handoff, generated from one source with every number read from the code.

### Items and tooltips
- Hand-drawn 32x32 art for all nine items (drawn in code, reproducible), rarity-coloured tooltips, the AscensionDex reskin.
- A crest for every modifier on the craft, inspect and overlay pages: the plate's shape shows the slot (hexagon prefix, shield suffix) and its colour the theme; Unique powers have their own.

### Safety and operations
- The progression store is backed up before any schema upgrade (and the upgrade is refused if the copy fails), at server start and daily (the newest 14 kept), and on demand with `/ascend admin backup`.
- Store writes are measured at 0.6 ms each on an ordinary disk (see `docs/PERFORMANCE.md`).
- Runs on Cobblemon 1.8.1 and 1.8.2.

### Licence
- Released under the MIT licence.

### Known limits
- Tooltip frames and backgrounds by rarity need Minecraft 1.21.2 or later.
- Core pity is not built (deferred by design).
