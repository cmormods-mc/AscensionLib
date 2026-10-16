# AscensionLib 1.0.0 beta 1

The first public beta of AscensionLib: a progression overhaul for [Cobblemon](https://cobblemon.com). Every Pokémon you catch or hatch rolls a **rarity** and a set of **modifiers** that change how it fights, and you grow them over time instead of replacing them.

**Requires** Minecraft 1.21.1, Fabric Loader 0.17.2 or later, Fabric API, Java 21 and **Cobblemon 1.8.1 or 1.8.2** on the server. Every player needs the **WebGUI** mod and **Rinku**, because every screen is a web page WebGUI shows. CobbleRaids (modifiers act in battle), CobbleTowers and ascensionDungeons (earning) and Polymer (hosts the resource pack) are optional.

## What is in it

- **Rarity and modifiers.** Common to Mythic sets how many modifier slots a Pokémon has (one to six). 34 modifiers, ranks I to V. Prefixes boost offense, suffixes add defense and recovery.
- **Growing a Pokémon.** Upgrade, Refine, Reforge, Perfect (a raised floor), Overreach (a one-shot gamble that seals the slot) and Promote, up to Mythic.
- **Uniques and Fusion.** A Unique Catalyst installs a build-defining power with a real drawback; two Mythics with different Uniques fuse into a **Transcendent**.
- **A materials wallet** of eight materials, also real items with their own art: tradable vouchers (a 3% withdrawal fee) and loot that joins the wallet when carried. Tower Marks are bound to you.
- **Tower completion chests.** A guaranteed pile, a quality tier earned from challenge cards, Ascension cycle and a clean run, and a crown of notable items; a floor-5 mini chest; a daily limit of 20 full chests, then 25% dust.
- **Respec items.** The Hourglass of Undoing resets a Pokémon's ranks and returns its credits; the Unbinding Prism swaps its Unique for free. They drop from Tower completions, are tradable, and the Hourglass can be bought with 90 Tower Marks.
- **Screens.** Craft, Fusion, Inspect, Wallet, the AscensionDex (party, wallet and guide tabs), a completion reveal and a battle overlay. A crest for every modifier: the plate's shape shows the slot, its colour the theme, and type modifiers take their type's colour. Pages scale to the screen and each player sizes them (`/ascend ui`).
- **An in-game guide** (`/ascend guide`) and a wiki handoff document, generated from one source with every number read from the code.
- **Server owners:** a tuning file (`config/ascensionlib.json`, reloaded with `/ascend admin reload`), automatic store backups (before any upgrade, at start and daily), and a startup report of which companion mods were found.

## Install

1. Put `AscensionLib-1.0.0-beta.1.jar` in the server's `mods/` folder with Cobblemon and Fabric API.
2. Start the server once; it creates its store and publishes its pages to WebGUI.
3. For the item art, host `AscensionLib-resourcepack.zip` (sha1 `9a5bbb106ba5d3582b79227b5f4239e49d8946d3`) and set `resource-pack` and `resource-pack-sha1` in `server.properties`, or, with Polymer, add the zip to `include_zips` in `config/polymer/resource-pack.json`. Without the pack, items still work as plainly named vanilla items.
4. In game: `/ascend dex` gives an AscensionDex and `/ascend guide` opens the guide.

Full steps, backups and updating are in [`docs/INSTALL.md`](https://github.com/cmormods-mc/AscensionLib/blob/main/docs/INSTALL.md).

## Known limits

- This is a beta. The Tower completion chest, the respec items and the in-game screens have been played in a real client, but the Tower completion path has had only a few live runs.
- Rarity-coloured tooltip frames need Minecraft 1.21.2 or later.
- Core pity is not built (deferred by design).
- Modifiers act in battle only with CobbleRaids installed.

## Files

| File | sha256 / sha1 |
|---|---|
| `AscensionLib-1.0.0-beta.1.jar` | sha256 `b5f7a1afeacb232131e1dc5b5cd63bbdc02455f0235a7adaa6fc34e6e7859a2d` |
| `AscensionLib-resourcepack.zip` | sha1 `9a5bbb106ba5d3582b79227b5f4239e49d8946d3` |

Licence: MIT. Pokémon and Cobblemon content is third-party and not covered by it. Full changelog: [`CHANGELOG.md`](https://github.com/cmormods-mc/AscensionLib/blob/main/CHANGELOG.md).
