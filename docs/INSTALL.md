# Installing and running AscensionLib

For a server owner. The player-facing explanation of the mechanics is in the game (`/ascend guide`) and in [`WIKI-HANDOFF.md`](WIKI-HANDOFF.md).

## What you need

- A Fabric server on **Minecraft 1.21.1** with **Java 21**, Fabric Loader 0.17.2 or later and Fabric API.
- **Cobblemon 1.8.1 or 1.8.2** (the mod declares `>=1.8.1 <1.9.0`).
- On every **client**: the **WebGUI** mod (2.0.0 or later) and **Rinku**. Every AscensionLib screen is a web page that WebGUI shows; the server tells a player when theirs is missing, and the key actions also exist as commands.
- Optional: **CobbleRaids** (installs the battle module so modifiers act in fights), **CobbleTowers** and **ascensionDungeons** (they pay rewards through the library), **Polymer** (hosts the resource pack).

## First start

1. Copy `AscensionLib-<version>.jar` into `mods/` beside Cobblemon and Fabric API. Install WebGUI on the server as well (it ships the pages to clients).
2. Start the server. In the log you should see `SQLite driver OK`, then the world opening its store, and `Published the AscensionLib pages`.
3. Join and run `/ascend status` (it reports the store and the authority id), then `/ascend dex` and `/ascend guide`.

Each world keeps its data in `<world>/cobbleascend/`:

| File | What it is |
|---|---|
| `progression.db` | The SQLite store: profiles, wallets, vouchers, the daily budget. Back it up with the world. |
| `authority.id` | A marker that ties the database to this world. A mismatch disables progression instead of mixing worlds. |
| `backups/` | Dated snapshots (see below). |

Page sizes are saved per player in `<world>/ascensionlib-ui.json`.

## The resource pack (item art)

The items are vanilla items with custom model numbers; the pack gives them their pictures. Without it they still work, as plainly named items.

- **Manual:** host `AscensionLib-resourcepack.zip` (built by `python -I tools/build_resourcepack.py`, or taken from a release), then set `resource-pack=<url>`, `resource-pack-sha1=<sha1>` and, if you want, `require-resource-pack=true` in `server.properties`. A server sends one pack: merge ours with any other you use.
- **With Polymer:** copy the zip somewhere under the server folder, add its path to `include_zips` in `config/polymer/resource-pack.json`, and set `enabled: true` in `config/polymer/auto-host.json`. Polymer merges the item overrides and hosts the result over the game port.
- The pack's hash changes whenever the art or the item list changes, so re-host it with every release.

## Tuning the economy

`config/ascensionlib.json` is written on the first start with every default in it. It holds the economy's dials, never its rules: the daily budget (`dailyBudget`), the withdrawal fee, the Hourglass's Tower Marks price, attunement per victory, the floor-5 mini chest and the whole completion chest (`towerCompletion`: the pile, and for each tier its first score, its pile percent, its crown slots and its crown weights). The `zones` section decides where profiles act and how rare they are: `defaultTier` (1 is the ordinary rarity table, 0 switches profiles off in every dimension nothing else names), `dimensions` (a tier from 0 to 10 for a named dimension, such as `"minecraft:the_nether": 3`, which beats everything including a zone another mod opened), `rarityTilt` (how fast each tier above 1 shifts toward rarer, 0.30 by default, 0 keeps every tier ordinary) and `rarityWeights` (an explicit list of six weights, Common to Mythic, that replaces the tilt for one tier). Edit a number and run `/ascend admin reload`, which says what changed and names anything it could not use. A missing or invalid value keeps its default and is named in the log; a file that cannot be read leaves the defaults in force and is never rewritten. The in-game guide shows a server's own numbers.

Everything else (the modifier catalog, rank bands, crafting costs, promotion costs) lives in the design data and the code, and changes with a release.

## Backups

- The mod copies the store to `progression.db.before-schemaN-fromM-<time>.bak` before it upgrades the schema, and does **not** upgrade if it cannot.
- It takes a snapshot into `backups/` when the server starts and then whenever the newest is a day old; the newest 14 are kept. `/ascend admin backup` takes one now.
- To restore: stop the server, restore the world **and** its `cobbleascend/progression.db` from the same moment (and delete `progression.db-wal` and `-shm`), then start.
- A newer store cannot be opened by an older mod (`STORE_SCHEMA_UNKNOWN`): to go back a version, restore the `.bak` made at the upgrade.

## Updating

1. Stop the server and take a world backup.
2. Replace the jar. The first start after an upgrade makes the `.bak` described above if the schema changed.
3. Rebuild or re-fetch the resource pack and update its hash.
4. Read the new `CHANGELOG.md` for anything players will notice.

## Commands for admins (permission level 2)

`/ascend admin grant <player> <material> <amount>`, `backup`, `reload`, `attunement <slot> <points>`, `initialize <slot>`, `completion <tier> [pay]` (shows a completion reveal, or with `pay` settles a real chest for you), `reveal <slot>`, `acquire <slot> <rarity>` and `rate-target`.

## Troubleshooting

| Symptom | Likely cause |
|---|---|
| A page does not open | The client lacks WebGUI or Rinku (the player is told). |
| Items look like plain vanilla items | The resource pack is not hosted or not accepted. |
| "Progression is disabled" in the log | The store could not be opened: the log names the reason (missing database with an existing marker, a different world's database, a failed upgrade backup). |
| Modifiers do nothing in a fight | CobbleRaids is not installed, so the battle module is absent; or the battle is a fight between players (native by design), or the dimension's tier is 0 in `zones`. |
| A Tower clear paid the old small amount | The installed AscensionLib is older than CobbleTowers expects and fell back to the milestone formula. |
