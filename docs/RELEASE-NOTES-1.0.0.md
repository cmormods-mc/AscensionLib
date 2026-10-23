# AscensionLib 1.0.0

The first full release of AscensionLib (Fabric): a progression overhaul for [Cobblemon](https://cobblemon.com). Every Pokémon you catch or hatch rolls a **rarity** and a set of **modifiers** that change how it fights, and you grow them over time instead of replacing them.

**Requires** Minecraft 1.21.1, Fabric Loader 0.17.2 or later, Fabric API, Java 21 and **Cobblemon 1.8.1 or 1.8.2** on the server. Every player needs the **WebGUI** mod and **Rinku**, because every screen is a web page WebGUI shows. **CobbleRaids is required for modifiers to act in battle**: without it everything else works (rolling, crafting, the wallet, the screens) but modifiers do nothing in a fight. CobbleTowers and ascensionDungeons (earning) and Polymer (hosts the resource pack) are optional.

## What is in it

- **Rarity and modifiers.** Common to Mythic sets how many modifier slots a Pokémon has (one to six). 34 modifiers, ranks I to V. Prefixes boost offense, suffixes add defense and recovery.
- **Profiles everywhere.** Every wild and trainer Pokémon in every dimension has a rating, and everything you catch or hatch gets a profile; fights between players are native. Each dimension has a tier (1 is the ordinary table, higher tiers shift toward rarer, 0 switches profiles off), set in `config/ascensionlib.json`.
- **Growing a Pokémon.** Upgrade, Refine, Reforge, Perfect (a raised floor), Overreach (a one-shot gamble that seals the slot) and Promote, up to Mythic.
- **Uniques and Fusion.** A Unique Catalyst installs a build-defining power with a real drawback; two Mythics with different Uniques fuse into a **Transcendent**.
- **A materials wallet** of eight materials, also real items with their own art: tradable vouchers (a 3% withdrawal fee) and loot that joins the wallet when carried. Tower Marks are bound to you.
- **Tower completion chests.** A guaranteed pile, a quality tier earned from challenge cards, Ascension cycle and a clean run, and a crown of notable items; a floor-5 mini chest; a daily limit of 20 full chests, then 25% dust.
- **Respec items.** The Hourglass of Undoing resets a Pokémon's ranks and returns its credits; the Unbinding Prism swaps its Unique for free. They drop from Tower completions, are tradable, and the Hourglass can be bought with 90 Tower Marks.
- **Battle overlay.** Bleed, Momentum stacks and Wardstone and Ensnaring procs show as badges for both sides.
- **Screens.** Craft, Fusion, Inspect, Wallet, the AscensionDex (party, wallet and guide tabs), a completion reveal and a battle overlay. A crest for every modifier: the plate's shape shows the slot, its colour the theme, and type modifiers take their type's colour. Pages scale to the screen and each player sizes them (the − and + in each page header).
- **An in-game guide** (`/ascend guide`) and a wiki handoff document, generated from one source with every number read from the code.
- **Server owners:** a telemetry log for balancing and performance (`<world>/cobbleascend/telemetry/`, players only as a hash, switch off with `-Dascensionlib.telemetry=false`; see `docs/PERFORMANCE.md`), a tuning file (`config/ascensionlib.json`, reloaded with `/ascend admin reload`), automatic store backups (before any upgrade, at start and daily), and a startup report of which companion mods were found.

## Install

1. Put `AscensionLib-1.0.0+fabric.jar` in the server's `mods/` folder with Cobblemon and Fabric API.
2. Start the server once; it creates its store and publishes its pages to WebGUI.
3. For the item art, host `AscensionLib-resourcepack.zip` (sha1 `685e4d8a071513a724174e5e18a66371ff0a34bd`) and set `resource-pack` and `resource-pack-sha1` in `server.properties`, or, with Polymer, add the zip to `include_zips` in `config/polymer/resource-pack.json`. Without the pack, items still work as plainly named vanilla items.
4. In game: `/ascend dex` gives an AscensionDex, which reaches every screen, and `/ascend guide` opens the guide. Operators keep `inspect`, `craft`, `fuse`, `sigil` and `wallet` under `/ascend admin`.

Full steps, backups and updating are in [`docs/INSTALL.md`](https://github.com/cmormods-mc/AscensionLib/blob/main/docs/INSTALL.md).

## Known limits

- The Tower completion chest, the respec items and the in-game screens have been played in a real client, but the Tower completion path has had only a few live runs.
- Rarity-coloured tooltip frames need Minecraft 1.21.2 or later.
- There is no Core pity; Cores come from Tower completions by chance.
- Modifiers act in battle only with CobbleRaids installed. The battle effects and their overlay badges have not yet been run in a long live battle.
- WebGUI 2.0.0 or later is what the mod is built and tested with; 1.8.1 is not a supported combination.

## Files

| File | sha256 / sha1 |
|---|---|
| `AscensionLib-1.0.0+fabric.jar` | sha256 `c9ed15ef4a02300fc4dbdd3e0bb5d20acd60034a76132c43b0336702134b36b1` |
| `AscensionLib-resourcepack.zip` | sha1 `685e4d8a071513a724174e5e18a66371ff0a34bd` |

Licence: MIT. Pokémon and Cobblemon content is third-party and not covered by it. Full changelog: [`CHANGELOG.md`](https://github.com/cmormods-mc/AscensionLib/blob/main/CHANGELOG.md).
