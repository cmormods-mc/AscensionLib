# Compatibility

What AscensionLib 1.0.0 beta is built for, what has been checked, and what has not. The mod declares `minecraft 1.21.1`, `java >=21`, `fabricloader >=0.17.2` and `cobblemon >=1.8.1 <1.9.0`; WebGUI and CobbleRaids are `suggests`. At server start it logs which companions it found and warns about a Cobblemon outside the verified list (`CompatReport`).

## Verified

| Combination | How it was checked |
|---|---|
| Cobblemon **1.8.1** | Built against it; unit tests, page harness and simulator tests; played on the owner's test server. |
| Cobblemon **1.8.2** | Built against it (Modrinth `GJ2a1b8O`, sha512 pinned in `tools/verify_artifact.py`); every Cobblemon class we reference is present with only additive changes (new `BattleEndResult`, `BATTLE_ENDED`, `ElementalType.getOrder`); the bundled Showdown (`data/cobblemon/showdown.zip`) is byte-identical to 1.8.1's, so the simulator tests apply unchanged; the dev server boots with the pages published and the Showdown extension loaded. |
| WebGUI **2.0.0** (1.21.1) | Compiled against and loaded by the dev server; every page seen in a real client. |
| Polymer **0.9.19** | Hosts the resource pack (config only); the built pack contains all item textures and the nine model overrides. |

## Not yet verified

- A live **floor-10 champion clear** in CobbleTowers paying the completion chest and handing over the armor piece and pack (the code path is covered by tests and by `/ascend admin completion <tier> pay`; the Towers half has run only in unit tests).
- **WebGUI 1.8.1**: below the declared range (`suggests >=2.0.0`). The pages appeared to work on a test server running it, but that is not a supported combination.
- CobbleRaids, CobbleTowers and WebGUI together on **Cobblemon 1.8.2** (each pins its own Cobblemon version in its own repository).
- **ascensionDungeons** calling `AscensionZones` and `settleDungeonRun`: the contract exists and is tested on our side; wiring it is that project's own TODO.

## The contract the companions reach by reflection

These methods are looked up by name and parameter types by CobbleRaids, CobbleTowers and ascensionDungeons, so a rename would compile here and fail silently there. `ContractSignaturesTest` pins every one; change a signature only by adding a new method.

| Class and method | Used by |
|---|---|
| `AscensionRewards.settleTowerBoss`, `settleScouterDrops`, `settleTrial`, `settleTowerCompletion`, `towerCompletionExtras`, `towerCompletionPreview` (read-only, for the chest preview), `settle` | CobbleTowers |
| `AscensionRewards.settleRaidAttunement`, `scaleItemQuantity` | CobbleRaids, CobbleTowers |
| `AscensionRewards.settleDungeonRun` | ascensionDungeons |
| `AscensionEncounters.declareEnemy`, `armBattle`, `disarmBattle`, `end` | CobbleRaids, CobbleTowers |
| `AscensionGrants.upgradeRandomly`, `grantWithRarity` | CobbleRaids, CobbleTowers (rentals) |
| `AscensionZones.openZone`, `closeZone`, `zoneTier`, `contractVersion` | ascensionDungeons |

Older companions keep working against a newer library, and a newer CobbleTowers falls back gracefully against an older library (`settleTowerCompletion` missing means the champion pays the milestone formula).

## Storage compatibility

The store schema is 4. A store is upgraded in place from schema 1, 2 or 3 and copied first (`docs/PERFORMANCE.md`); a newer store cannot be opened by an older mod. Player data keeps the names it had before the library was extracted (`<world>/cobbleascend/`, the `cobbleascend` NBT keys), so existing worlds are found, not reset.

## Minecraft 1.21.2 and later

Not supported (the mod depends on 1.21.1 exactly). Moving there would also allow rarity-coloured tooltip frames (`tooltip_style`), which 1.21.1 does not have.
