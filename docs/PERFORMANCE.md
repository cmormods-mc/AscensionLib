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
per commit, which is still fine for one-off player actions but would show if a hundred writes landed in a single tick. If a server is on slow storage, run `/ascend admin backup` and check the log's `[profile]` lines with the profiler on
(`-Dascensionlib.profile=true`, see `BUILD-STATUS.md`). Nothing in the mod writes in a loop: sweeps and polls read only (the bleed watch, attunement participation and the battle polls look at in-memory battle state twice a second).

## Backups

- **Before a schema upgrade:** when an existing store is older than the mod's schema, the mod first copies it beside itself as `progression.db.before-schemaN-fromM-<UTC time>.bak` (a consistent copy made by SQLite). If the copy cannot be made the store is **not** upgraded and progression stays disabled with the reason in the log. A newer store cannot be opened by an older mod (`STORE_SCHEMA_UNKNOWN`), so this copy is the way back: stop the server, put the `.bak` back as `progression.db` (and delete the `-wal` and `-shm` files next to it), and run the older mod.
- **Dated snapshots:** `<world>/cobbleascend/backups/progression-<UTC time>.db`, taken when the server has started, then whenever the newest is more than a day old (checked every ten minutes), and on demand with `/ascend admin backup`. The newest 14 are kept.
- **Restoring:** the world and its progression database belong together (the database holds an authority id that the world's `authority.id` marker must match). Restore the world folder and the database from the same moment, or the mod refuses the database and disables progression rather than mix them.
