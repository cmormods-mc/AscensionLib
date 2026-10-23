# Performance and data safety

## Store throughput (measured 2026-10-15)

Every store operation (a craft, a reward, a wallet change) runs on the server thread and commits with a durable write (`PRAGMA synchronous=FULL`, WAL mode). That is the safe choice, and it is cheap on ordinary hardware.
`StoreThroughputTest` measures it on every test run (it prints the figures and fails only if a commit becomes pathologically slow):

| What | Measured (SSD, a developer desktop) |
|---|---|
| One durable write (a grant) | **0.6 ms** |
| One wallet read | **0.015 ms** |
| 20 players earning at the same instant (20 writes) | **11 ms** |

Even a very busy moment (twenty completions settling together) costs under a tenth of a server tick budget of 50 ms. Slower storage changes the write figure, not the read figure: a network disk or a spinning drive can take 5 to 20 ms
per commit, which is still fine for one-off player actions but would show if a hundred writes landed in a single tick. If a server is on slow storage, read the `perf` events of the telemetry log (below): `store.grant`, `store.craft` and the rest are
the durable-write times as the server saw them. Nothing in the mod writes in a loop: sweeps and polls read only (the bleed watch, attunement participation and the battle polls look at in-memory battle state twice a second).

## Backups

- **Before a schema upgrade:** when an existing store is older than the mod's schema, the mod first copies it beside itself as `progression.db.before-schemaN-fromM-<UTC time>.bak` (a consistent copy made by SQLite). If the copy cannot be made the store is **not** upgraded and progression stays disabled with the reason in the log. A newer store cannot be opened by an older mod (`STORE_SCHEMA_UNKNOWN`), so this copy is the way back: stop the server, put the `.bak` back as `progression.db` (and delete the `-wal` and `-shm` files next to it), and run the older mod.
- **Dated snapshots:** `<world>/cobbleascend/backups/progression-<UTC time>.db`, taken when the server has started, then whenever the newest is more than a day old (checked every ten minutes), and on demand with `/ascend admin backup`. The newest 14 are kept.
- **Restoring:** the world and its progression database belong together (the database holds an authority id that the world's `authority.id` marker must match). Restore the world folder and the database from the same moment, or the mod refuses the database and disables progression rather than mix them.

## Telemetry: measuring a live server and balancing from it

The mod writes a structured log of how it is played and how it performs: `<world>/cobbleascend/telemetry/events-YYYY-MM-DD.jsonl`, one JSON object per line with `t` (epoch milliseconds) and `type`. It is on by default, written from its own thread (the server thread only builds a small object per event), keeps 30 days, and stops a day's file at 128 MB. Nothing is sent anywhere. Turn it off with `-Dascensionlib.telemetry=false`, `ASCENSIONLIB_TELEMETRY=0`, or an empty `config/ascensionlib-telemetry-off.flag`.

| Event | What it records | What it answers |
|---|---|---|
| `server_start` | build, Minecraft version, every configured number, what differs from the defaults | which balance a log was played under |
| `ledger` | every committed store operation: kind, player, Pokemon, materials spent and gained, the reason, wallet revision, transaction time | income by source, spending by use, how long a durable write takes |
| `craft` / `craft_refused` | the slot's rank, value and floor before and after; or the reason a craft was turned down | success and gain per craft kind, what players cannot afford |
| `acquire` | origin, dimension, zone tier, species, level, rarity, modifiers | the rarity table as it plays |
| `encounter` / `settlement` | what a tower boss, completion, trial or dungeon run was worth, and how it ended per player (defeats included) | win rate by tier, payout per run |
| `join` / `leave` | wallet and party (rarity, attunement, pending upgrades, modifiers) at both ends; session length | progression speed per hour played |
| `economy` | the total of each material across all wallets, every 30 minutes | inflation |
| `perf` | once a minute: server tick, store transactions, page requests (`page.<channel>.wait` is the time queued behind the tick, `.run` the handler), event sizes (`emit.<event>.chars`, limit 30000), the pages' own render times (`ui.<page>.render.<event>`, `ui.<page>.firstData`, `ui.<page>.longtask`), heap | where time goes, before and after a change |

`python -I tools/telemetry_report.py <world>/cobbleascend/telemetry [--days N]` turns the files into tables. Players appear only as a 12-character salted hash (`Telemetry.playerKey`): countable, not identifiable.
`-Dascensionlib.profile=true` additionally prints each minute's `perf` summaries to the server log.

The page-side numbers come from the player's own browser through `ascension_perf` (bounded and rate limited on the server; a modified page can only skew its own labels).
