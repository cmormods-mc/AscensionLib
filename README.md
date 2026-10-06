# AscensionLib

A progression overhaul for [Cobblemon](https://cobblemon.com) (Minecraft 1.21.1, Fabric). Every Pokémon you catch rolls a rarity and a set of **affixes** that change how it fights, and you grow them over time instead of replacing them.

> Early prototype. The core pieces are built and tested in code; only part of it has been played in a live world.

## How it works

- **Rarity.** Common to Mythical decides how many affix slots a Pokémon has (one to six). Every Pokémon can eventually reach Mythical.
- **Affixes.** Prefixes boost offense, suffixes add defense and recovery. Some build around burn, poison or bleed.
- **Growing a Pokémon.** Levelling earns upgrades that raise an affix's rank. You can also refine a roll, reforge an affix, or promote the Pokémon to the next rarity.
- **Uniques.** A rare Catalyst installs one Unique power, each with a real drawback.
- **Everywhere it matters.** Affixes apply to wild battles, and enemies in towers and raids fight with their own. A one-use Scouter reveals an enemy's build first.
- **Earning materials.** Tower runs, trials and raids pay the crafting materials. They live in a per-player wallet, not as items.

## Companion mods

AscensionLib is the shared library for [CobbleRaids](https://github.com/cmormods-mc/SnobblemonRaids) and [CobbleTowers](https://github.com/cmormods-mc/CobbleTowers). It works on its own for capture, inspection and crafting; battle effects need CobbleRaids installed.

## Using it

Requires Java 21, Fabric API and Cobblemon 1.8.1. In game:

- `/ascend craft` to upgrade, refine, reforge, promote or install a Unique
- `/ascend inspect` and `/ascend wallet` to look at a Pokémon and your materials
- `/ascend scout`, or the J key during a fight, to use a Scouter

## Build

```
gradlew :domain:test :store:test :ascensionlib:build
```

The jar is `ascensionlib/build/libs/AscensionLib-0.1.0-prototype.jar`.

## More

Design notes and the full specification are in [`docs/`](docs/), starting with [`DESIGN.md`](docs/DESIGN.md) and [`BUILD-STATUS.md`](docs/BUILD-STATUS.md).
