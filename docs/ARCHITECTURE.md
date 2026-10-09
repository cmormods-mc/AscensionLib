# Technical architecture and persistence proposal

Status: full-product architecture proposal, written before the build; the text below keeps its original wording. The canonical SQLite store (`store`), the Fabric wiring and a client now exist, and the mod is AscensionLib (mod ID `ascensionlib`), a single Fabric jar in the `ascensionlib` module that nests `domain` and `store`. The module table, the `fabric`/`simulator`/`raids-compat` split and `AscensionService` below are proposals, not the current layout: the Fabric code, client, networking and the battle adapter all live in `ascensionlib`, and the simulator module is installed through CobbleRaids' extension API. See BUILD-STATUS.md for what is actually built and verified. Raid integration is deferred. Primary-source evidence and uncertainties are in `API-RESEARCH.md` and `COBBLERAIDS-INTEGRATION.md`.

## Distribution

Use Java 21, Fabric Loom, official Mojang mappings and the exact verified Cobblemon 1.8.1 Fabric artifact. Match the supported Fabric API/loader and Fabric Language Kotlin requirements declared by that artifact; do not copy the old CobbleRaids versions blindly. Lock artifact hashes and the tested runtime matrix in the eventual release manifest.

Proposed source modules:

| Module | Owns | Must not depend on |
|---|---|---|
| domain | Rarity, affix generation/validation, costs, caps and progression rules | Minecraft, Fabric, Cobblemon, GUI |
| fabric | Lifecycle hooks, storage, commands, networking and native battle adapter | CobbleRaids internals |
| client | Inspection, crafting, reveal and Trial screens | Server filesystem or random roll authority |
| simulator | Namespaced battle effect handlers and handshake | World persistence or reward grants |
| raids-compat | Supported public raid APIs, capability negotiation and reward/catch translation | Private session maps or reflection into grant engines |

Build core/client as one required mod jar initially. Ship raids-compat as an optional separate jar requiring both mods and pinned supported versions. Separate artifact boundaries prevent accidental linkage when raids are absent. Domain can be a Gradle subproject; do not create a reusable plugin framework or arbitrary scripting engine for twelve effects.

## Deep service boundaries

`ProfileService` (originally proposed as `AscensionService`; renamed because CobbleTowers has an unrelated class of that name): initialize once, read snapshot, validate/import profile, promote/reforge/refine using explicit owner and revision. Internalizes generation and lifecycle identity rules.

`ProgressionStore`: atomically owns wallets, profiles, craft operations, research counters, attunement, reward entitlements and run reservations. Exposes domain operations, not arbitrary database access.

`PokemonProjectionAdapter`: resolves a loaded Pokémon by native identity, reads/writes the namespaced persistent compound, marks changes using the target's required notification, and reconciles to canonical progression state. No random draws here.

`BattleAdapter`: classifies mode, snapshots validated effects, negotiates simulator capabilities, attaches battle metadata, receives combat participation and retires temporary state. No wallet writes from JavaScript.

`TrialService`: admission, roster locking, run lifecycle, result validation and creation of one reward entitlement. Delegates execution to standalone NPC battles or optional raid adapter.

`RaidAdapter`: translates a supported raid contract into domain events. Reports precise capabilities instead of a single misleading installed=true flag.

## Persistence authority decision

Use an embedded SQLite store for the canonical server-local progression state. The exact maintained JDBC dependency, license and native packaging must be verified at implementation time. This is a proposed dependency, not installed in this planning pass.

Mirror the portable profile under Pokémon `persistentData.cobbleascend` so inspection, exports and normal lifecycle copies retain metadata. The server database is authoritative for crafts and progression; the Pokémon compound is a projection of the last committed revision. This deliberate choice makes wallet debit + profile change atomic even though Minecraft/Cobblemon and SQLite cannot share a transaction.

Both stores are required for a complete server backup. Never promise cross-server transfers merely because the Pokémon carries metadata. The first release supports one world/server authority. A missing database is a recovery condition, not permission to silently reset everyone's profiles. The world stores an authority ID, and the database stores the same ID; an unexpected missing/mismatched database stops mutations until an operator restores or explicitly initializes it.

For a genuinely new install, legacy Pokémon become Common according to migration policy. For an import from another authority, preserve untrusted metadata in an operator-visible quarantine field, initialize a new Common profile only via explicit import policy, and explain that progress is not transferable by default. No unsigned client-supplied or foreign NBT may create wallet value or a trusted high-rarity profile.

Identity has three distinct parts: native Pokémon UUID, globally unique profile ID, server authority ID. A known UUID/profile mismatch is quarantined. Two native Pokémon with the same profile ID cannot both receive mutable progression until an operator resolves cloning. A legitimate raid catch gets a fresh native/profile identity; evolution retaining identity keeps the existing profile. Runtime tests must prove native identity semantics.

SQLite rows: `profiles`, `wallets`, `craft_operations`, `research_awards`, `research_days`, `reward_entitlements`, `runs`, `reward_budget_reservations`, `core_pity`, `milestones`, `schema_migrations`. Use unique constraints for operation ID, entitlement key and acquisition identity. Monetary values are nonnegative bounded integers; rolled values and rarity are validated against schema. Foreign keys connect profiles and operations, but Pokémon ownership is always rechecked against Cobblemon at mutation time.

Avoid database I/O in simulator callbacks. A dedicated single writer executes transactions; the server thread acquires Pokémon/run locks, submits bounded work and applies projections after acknowledgment. Reject new crafts as temporarily unavailable if the queue is full. Never hold a Minecraft tick waiting indefinitely for storage. A user receives a committed result only after durable database commit, with the Pokémon projection updated before its lock is released.

## Craft transaction and crash recovery

1. Client submits operation UUID, Pokémon UUID, expected profile revision, recipe/slot, displayed config hash and screen nonce. It does not submit authoritative output or cost.
2. Server verifies current ownership, distance/menu context if relevant, no trade/battle lock, recipe, cap, revisions, funds and operation rate limit. Lock Pokémon and wallet for this operation.
3. Draw a result from the server random source exactly once. Within one DB transaction: claim unique operation ID, debit wallet, update profile/attunement if relevant, increment revision, record result and mark committed. Failed validation commits nothing.
4. Apply the committed profile snapshot to the loaded Pokémon projection and call `onChange()`. Return the committed result; repeated identical operation IDs return that result, never re-debit or reroll. A reused ID with a different payload is rejected.
5. If the process fails after step 3, reconciliation projects the canonical new revision when the Pokémon next loads. If it fails before commit, there is no debit or profile change. Disconnect does not refund an already committed valid craft.

The database transaction protects material/profile consistency. It does not make Minecraft inventory, held items, foreign trades or arbitrary mod state transactional. No launch craft consumes native inventory items, which intentionally keeps those outside this atomic boundary.

## Acquisition flow

Use the verified capture hook for ordinary captures; investigate gained/storage callbacks only as classification/reconciliation signals. Record a capture operation keyed by stable identity after confirmed ownership, write the canonical profile, then project and announce. If target event timing precedes durable storage, queue reconciliation until successful ownership rather than granting stipend optimistically.

Unknown grants default to Common. Handle eggs with a verified hatch event or explicit hatch provenance, not string matching a party message. Reconciliation must not race a legitimate capture callback and initialize Common first: serialize acquisition classification on the server thread, prefer explicit capture/hatch/raid-prepared context, and defer fallback initialization until end-of-tick ownership confirmation. A target-runtime ordering test is required; if event order spans ticks, use a pending-acquisition state rather than a guessed delay.

Canonical profile fields: schemaVersion, profileId, pokemonId, authorityId, revision, rarity, affixes, attunement, origin, acquisition timestamp, catalogVersion, initialRarity. Each affix stores stable ID, selected parameters, rolled integer value and definition version. Persist rolled outcomes, not just the random seed. Origin records source kind and optional encounter ID, not an ever-growing travel log.

No mutable battle counters in permanent metadata. Do not store personal chat, IP addresses or unnecessary player identity in a traded Pokémon.

## Battle data and simulator hook strategy

Capture an immutable `BattleProfile` from canonical records at entry. Map original Pokémon identity to simulator side/slot explicitly; do not match nickname or species. Carry allowed affix IDs/values and battle-local counters through a namespaced bridge. Simulator validates finite bounded values and supported effect IDs, not executable strings from configuration.

Preferred order of implementation: supported simulator extension point if present; otherwise a narrow, version-pinned bootstrap hook that registers namespaced handlers while preserving existing functions. Never replace an entire simulator resource owned by another mod. The exact hook remains a research spike; source evidence has not established a general affix API.

Handshake includes protocol version, effect catalog version/hash and active capabilities. Run a controlled canary in development and automated release checks. A mismatched live handshake disables affix application uniformly for the affected battle mode and labels inspection accordingly. Enhanced challenge admission is refused before creating a run; ordinary unsupported battles remain vanilla and advertise that fact.

Run all percentage changes inside the simulator's authoritative move pipeline. Java receives normal damage/heal/faint outcomes. Only CobbleRaids commits its shared HP. Do not subtract extra HP in a second Java listener, replace native abilities, or encode affixes as held items.

Battle mode policy: ordinary wild/NPC PvE enabled after tests; standalone Trials enabled after launch hook proof; raid PvE only with ready adapter; player-versus-player disabled for every side; unsupported custom formats disabled. Keep a log of mode classification and reason in debug mode, without exposing opponents' hidden build data to clients.

## Networking and permissions

C2S: inspect own party slot/UUID, list own wallet, preview craft, confirm craft, list Trials, enter Trial, exit own run. Require server-side ownership and active menu checks; never accept an arbitrary world Pokémon reference as permission. S2C: sanitized profile/wallet/preview/result/capability DTOs. Viewing other players' builds requires their explicit share action or operator permission; the default screen exposes only the player's own Pokémon.

Bound packet sizes and affix counts, validate enum/identifier lengths and ranges, reject unknown protocol versions, rate-limit previews/confirmations, and coalesce repeated inspection requests. Server permissions govern all admin grants, migrations and overrides. Commands should use Minecraft permission conventions; no mandatory third-party permissions plugin.

Proposed commands: `/ascend inspect [party-slot]`, `/ascend wallet`, `/ascend craft`, `/ascend trials`, `/ascend help`, `/ascend status`; operator `/ascend admin inspect <player> <slot>`, `grant`, `initialize`, `repair`, `audit`, `reload`. Mutating operator actions require a reason and audit entry. Exact syntax is implementation work.

## Configuration and migrations

Server config defines weights, costs, rewards, caps, mode enablement and adapter mappings. Data content defines affix templates/localization and Trial teams. Validate the entire replacement catalog before publishing it atomically; invalid reload retains the old configuration and reports exact paths/errors. No arbitrary embedded JavaScript in user content. Battle snapshots retain their initial catalog version until completion.

Additive new definitions do not reroll old Pokémon. Deleted/renamed definitions require explicit aliases or a migration; otherwise disable unknown affixes visibly. Over-cap old numerical rolls require versioned clamp migration with a backup and audit rather than hidden interpretation drift. Unknown future schema blocks mutation while retaining raw data.

Database migration runs in a transaction after backup. Pokémon projections update lazily from canonical profiles. Forward migration is supported; downgrades require restoring the matched world/database backup. Keep schema version and content version separate.

## Runtime operations

Persist run admission before launching an encounter; refused starts release reservations. Record terminal outcome before issuing rewards. Unique entitlement key is `(runId, playerId, rewardKind)`. Public raids require an authoritative result contract with stable IDs and durable reconciliation; existing owned callbacks alone do not prove exactly-once delivery after a process crash.

On restart, unresolved active Trials are aborted without a victory reward, and admission reservations are released only after recovery confirms no committed victory. Committed outcomes replay their unclaimed wallet entitlements. No entry fee in release one means there is no token refund to fabricate. Pending public raid results with insufficient durable evidence remain operator-reviewable; never infer victory from boss disappearance.

Operational tools: startup compatibility summary, bounded structured audit log, profile quarantine report, pending-run recovery count and a DB health command. No automatic remote telemetry. Logs should include IDs/revisions for diagnosis but not full Pokémon data by default.

Backup with server stopped or a consistent SQLite backup operation coordinated with a world save; copying a live WAL database file alone is insufficient. Restore the matched authority/world/database bundle. Uninstall leaves persistent compounds inert, returns normal combat rules and retains DB backups. Reinstall reconciles only when authority matches. A clean removal tool may strip metadata after an explicit backup, but is not part of ordinary disablement.

## Initial performance budgets

Targets to measure, not achieved results: no database reads per damage hit; at most six evaluated affixes per active Pokémon per applicable event; no whole-PC scans per tick; amortized metadata reconciliation on load; less than 1 ms additional server-thread work per ordinary capture/inspection on the reference test machine excluding async storage; no growing simulator objects after 1,000 battle starts/finishes. Record reference hardware, player counts and p95 timings during validation. Never promise a TPS number without measurements.
