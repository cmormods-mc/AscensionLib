# AscensionLib

A progression overhaul for [Cobblemon](https://cobblemon.com) (Minecraft 1.21.1, Fabric). Every Pokémon you catch or hatch rolls a **rarity** and a set of **modifiers** that change how it fights, and you grow them over time instead of replacing them.

> **1.0.0 beta.** Everything below is built, tested and has been seen in a real client, except the first live clear of a CobbleTowers floor-10 champion paying its completion chest. See [`docs/BUILD-STATUS.md`](docs/BUILD-STATUS.md) for the dated record of what is verified.

## What it does

- **Rarity and modifiers.** Common to Mythic decides how many modifier slots a Pokémon has (one to six). Prefixes boost offense, suffixes add defense and recovery. Modifiers act inside the battle simulator, so the normal rules still apply.
- **Growing a Pokémon.** Levelling earns upgrade credits that raise a modifier's rank (I to V). Refine a roll, reforge a modifier, perfect or gamble on a rank V one (Overreach), and promote the Pokémon rarity by rarity, all the way to Mythic.
- **Uniques and Fusion.** A Unique Catalyst installs one build-defining power with a real drawback. Two Mythics with different Uniques fuse into a **Transcendent** with a completely new power.
- **A materials wallet.** Resonance Dust, Facets, Cores, Fragments, Catalysts, Scouters, Sigils and Tower Marks live in a per-player wallet and can be taken out as real, tradable items (a small fee applies). Tower Marks are bound to you.
- **Towers, trials, dungeons and raids** pay into the wallet. A Tower completion is an hour's work and pays a guaranteed pile, a quality tier earned by difficulty, and a crown of notable items; a daily limit keeps the easiest run from being the best one.
- **Rare respec items.** The Hourglass of Undoing resets a Pokémon's ranks and gives its credits back; the Unbinding Prism swaps its Unique with no Catalyst. They drop from Tower completions and can be traded.
- **The AscensionDex.** Right-click it for your party (pick a Pokémon to inspect or upgrade), your wallet, and the in-game guide.
- **Wild Pokémon have builds too.** Every wild and trainer Pokémon is rated, and everything you catch or hatch gets a profile; deeper places such as dungeons shift the odds toward rarer. Fights between players stay native. A one-use Scouter reveals an enemy's build first, and a bleed badge on the overlay shows who is bleeding.

The full player guide is in the game (`/ascend guide`, or the Guide tab of the AscensionDex) and, as one document for a wiki, in [`docs/WIKI-HANDOFF.md`](docs/WIKI-HANDOFF.md). Both are generated from the same source with every number read from the code.

## Requirements

| | |
|---|---|
| Server | Minecraft 1.21.1, Fabric Loader 0.17.2 or later, Fabric API, **Cobblemon 1.8.1 or 1.8.2**, Java 21 |
| Clients | The **WebGUI** mod and **Rinku** (every screen is a web page WebGUI shows). No client mod of ours. |
| Optional | **CobbleRaids** (required for the modifiers to act in battle; without it they do nothing in a fight), **CobbleTowers** and **ascensionDungeons** (earning), **Polymer** (hosts the resource pack for you) |

## Install

1. Put `AscensionLib-<version>.jar` in the server's `mods/` folder, with Cobblemon and Fabric API. Players install WebGUI and Rinku.
2. Start the server once. It creates `<world>/cobbleascend/progression.db`, backs it up at every start and then daily, and publishes its pages to WebGUI.
3. Give the items their art. **With Polymer installed there is nothing to do:** the mod adds its models and textures to Polymer's pack itself and merges its item model files with any other pack's (restart the server so Polymer rebuilds the pack; players rejoin to download it). Without Polymer, host `AscensionLib-resourcepack.zip` and set `resource-pack` and `resource-pack-sha1` in `server.properties`. Without any pack the items still work as plainly named vanilla items.
4. In game, `/ascend dex` gives an AscensionDex and `/ascend guide` opens the guide.

More detail, backups and updating are in [`docs/INSTALL.md`](docs/INSTALL.md).

## Companion mods

AscensionLib is the shared library for [CobbleRaids](https://github.com/cmormods-mc/SnobblemonRaids), [CobbleTowers](https://github.com/cmormods-mc/CobbleTowers) and ascensionDungeons. It works on its own for capture, inspection, crafting and the wallet; battle effects need CobbleRaids. The contract the others call is pinned by tests (`ContractSignaturesTest`).

## Build

```
gradlew :domain:test :store:test :ascensionlib:build
python -I tools/build_resourcepack.py     # the pack, the item art and the page icons
python -I tools/build_guide.py            # the in-game guide and the wiki handoff
node tools/page_harness.mjs               # the pages in a headless browser
```

The jar is `ascensionlib/build/libs/AscensionLib-<version>.jar`. The build needs WebGUI to compile against: see [`ascensionlib/libs/README.md`](ascensionlib/libs/README.md). [`docs/RELEASING.md`](docs/RELEASING.md) lists the steps for a release.

## More

Design notes and decisions are in [`docs/`](docs/), starting with [`DESIGN.md`](docs/DESIGN.md) and [`BUILD-STATUS.md`](docs/BUILD-STATUS.md). The changelog is [`CHANGELOG.md`](CHANGELOG.md). Third-party material is listed in [`THIRD-PARTY.md`](THIRD-PARTY.md).

## Licence

AscensionLib is released under the [MIT licence](LICENSE). Pokémon, Cobblemon and the Pokédex concept are third-party franchise content and are not covered by it; bundled third-party material keeps its own licence (see [`THIRD-PARTY.md`](THIRD-PARTY.md)).
