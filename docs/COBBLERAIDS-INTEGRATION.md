# CobbleRaids integration investigation

Status: archived planning evidence, inspected 2026-10-03. The owner subsequently deferred integration and confirmed CobbleRaids version work is handled separately. Local-version observations below are historical evidence only, not an active blocker for this codebase. Existing CobbleRaids source was read only. All contracts explicitly labeled proposed below require implementation. No runtime compatibility is claimed.

## Decision

Keep CobbleAscend core independent of CobbleRaids. Put direct CobbleRaids class references into an optional adapter, initialized only after loader and supported-version checks. Launch crafting materials are account-bound balances in Ascend's server wallet, not registered item drops. Ordinary public raid wallet payouts require a proposed authoritative terminal event plus participation data; the current owned-encounter listener covers only encounters Ascend starts. Use that public owned-encounter API for optional Ascend-owned challenge encounters after compatibility tests. Do not intercept the private reward grant pipeline or replace its claim queue. Defer automatic raid-catch provenance and arbitrary raid boss affixes until explicit hooks exist. Existing loot-table item grants are a verified alternative for a future physical-currency design, not the selected launch integration.

## Compatibility gate

The inspected local project is **CobbleRaids 0.8.114-white-gold-shop**, built for Minecraft 1.21.1, Fabric Loader 0.17.2, Fabric API 0.116.6+1.21.1, Java 21, and **Cobblemon 1.7.3**. Its **SkiesGUIs** dependency is 1.8.1. The requested Cobblemon 1.8.1 is not what this project builds or declares compatible with. Do not silently change the target or label the current integration 1.8.1 compatible. First obtain and verify the requested artifact, then port/test both battle bridges if needed.

Evidence: [build dependencies](<L:/Codex/CobbleRaids GUIs/build.gradle:28>) and [manifest requirements](<L:/Codex/CobbleRaids GUIs/src/main/resources/fabric.mod.json:20>). These are local source observations, not a claim about latest public releases. This workspace has no Git metadata, so there is no verified commit hash for the snapshot.

## Existing public encounter contract

[CobbleRaidsEncounters](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/api/encounter/CobbleRaidsEncounters.java:6>) explicitly defines its package as the public boundary, deliberately excludes Cobblemon/internal CobbleRaids types from signatures, and declares experimental `API_VERSION = 1`. It exposes `start`, `abort`, and `isActive`; mutation calls require the server thread. A refused start leaves no boss or session and triggers no listener.

[EncounterRequest](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/api/encounter/EncounterRequest.java:29>) supplies owner ID, encounter UUID, 1–4 players, a raid definition ID, world/position, final boss level, optional shared max health, policy, and rules. Definition IDs provide species, moves, traits, and base health. Arbitrary Pokemon objects, affix bundles, reward callbacks, or mutable boss handles are not request fields.

[EncounterPolicy](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/api/encounter/EncounterPolicy.java:16>) independently controls catching, raid rewards, raid history, experience/EV progression, health carryover, and PP carryover. Its default disables every side effect. Proposed optional Ascend-owned Trial policy: catchable=false, raidRewards=false, raidHistory=false, progression=false, carryHealth=false, carryPp=false. Trials are standalone and free by default; this optional raid-backed path must prove safe battle copies, lobby healing, and no held-item/permanent-object duplication. Disabling carryover flags alone does not prove copy isolation: verify actual target-version battle construction, mutations and disconnect recovery before shipping. Ascend owns Trial completion wallet rewards. Ordinary public raids retain CobbleRaids' existing catch, reward, progression and carryover policies; these proposed Trial flags do not change them.

[EncounterRules](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/api/encounter/EncounterRules.java:39>) supports player move bans, switching, bag items, opening weather/terrain, and boss shared-health scaling. Health scaling permits 1–1000 percent. Neutral rules permit switching/items and leave the field unchanged. CobbleRaids safety bans still apply. These rules are sufficient for initial encounter modifiers; they are not a general affix engine.

[EncounterListener](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/api/encounter/EncounterListener.java:11>) receives participant departures and one terminal result after boss removal. Listener errors are contained. [EncounterResult](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/api/encounter/EncounterResult.java:16>) reports encounter UUID, outcome, contribution map, remaining players, elapsed combat ticks. It does not report boss Pokemon, catch success, rarity tier, per-Pokemon legal-action participation, or a reward-claim token. Owned Trials therefore also require independently verified participation tracking; the contribution map alone cannot satisfy attunement eligibility. Ascend must persist its own encounter context keyed by UUID before starting. The runtime callback guarantee is not a durable delivery guarantee across process crashes.

## Existing ordinary raid reward path

The victory coordinator sequences progression/carryover, history/catch, then reward queuing before cleanup and the owned encounter callback: [RaidLifecycleCoordinator](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/lifecycle/RaidLifecycleCoordinator.java:207>). The inspected source contains no general public all-raid completion event or reward-plan extension event in the public encounter package. Public Java methods elsewhere are implementation details, not stable integration guarantees.

Reward definitions already accept registered item IDs and loot tables: [RaidDefinition](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/config/RaidDefinition.java:92>). The grant engine resolves either policy or legacy plans, seeds random rolls from pending reward data, and grants via Minecraft item registry/loot tables: [RaidRewardGrantEngine](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/reward/RaidRewardGrantEngine.java:38>). Therefore, an opt-in compatibility datapack could grant registered items through configured tables without an imperative reward hook. This is a verified future physical-item alternative, not launch scope: launch wallet balances cannot be delivered merely by naming a loot table. Inspect the selected policy before installing tables: a custom definition table can select the legacy path; never assume defining two table locations makes both run.

Missing item IDs are logged and skipped rather than throwing: [grant item validation](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/reward/RaidRewardGrantEngine.java:230>). If a future item pack is built, it must not load without its registered items. Disable/remove that pack when uninstalling its owner, rather than accepting perpetual skipped rewards. Launch has no such material-item pack.

The pending queue lives in overworld SavedData, with persisted seeds and definition IDs: [PendingRewardStore](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/reward/PendingRewardStore.java:23>). Config snapshots are retained in memory but re-resolved after restart; removal of a definition drops its pending entries with a warning. Do not promise identical reward contents across edits/restarts solely because seeds persist.

Claims are consumed and persisted before granting to avoid replay; comments explicitly acknowledge a crash-loss window: [RaidRewardService](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/lifecycle/RaidRewardService.java:87>). This is not an atomic transaction across inventory and world data. Ascend must not infer a wallet grant from GUI close, reward command execution, or repeated claim UI callbacks. Its proposed terminal event creates a separate entitlement in its own wallet ledger; native raid item rewards remain independent.

## Proposed public-raid wallet event (not existing)

Launch ordinary public raid progression requires an explicit authoritative terminal hook added to CobbleRaids or a jointly defined adapter SPI. A proposed immutable payload contains stable raid-instance UUID, definition ID and raid-tier ID, final outcome, eligible participant UUIDs, and per-participant Pokemon UUID participation evidence (legal turn or received opposing action). Damage contribution alone cannot prove support participation. The hook fires after the authoritative terminal decision and exposes no mutable reward queue; only victory generates Ascend entitlements. Native eligibility restrictions remain authoritative. Unmapped definition/tier IDs give no Ascend currency and a startup diagnostic, as ECONOMY.md specifies.

Ascend maps verified IDs to reward bands and commits wallet credit, shared daily budget, core pity, attunement and idempotency record under `(raidInstanceId, playerId)`. This is independent of native item reward claim time. Do not award again from both the owned listener and public event: Ascend-owned encounters use exactly one selected route and the same deduplication identity.

An in-memory event plus a durable Ascend ledger prevents retries from duplicating a recorded grant, but cannot recover a victory lost before event persistence. To promise crash-safe completion, propose a durable terminal outbox in the encounter owner with replay/acknowledgment semantics, or an equivalently tested recovery protocol. The existing API supplies neither. Until implemented, interrupted runs enter explicit reconciliation without invented victory; reservations and unknown outcomes must be resolved according to a documented administrative recovery policy. Current listener “exactly once” means per active runtime lifecycle, not guaranteed persisted delivery.

## Catch pathway: essential distinction

[RaidCatchService](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/catching/RaidCatchService.java:74>) clones the boss with `boss.clone(true, player.registryAccess())`, makes the clone catchable, heals it, and calls `party.add(caught)` (which falls back to PC). It does not capture the world entity or explicitly publish a Cobblemon catch event. A normal ball-capture listener alone therefore cannot be assumed to assign raid-caught Pokemon rarity.

The current owned-encounter callback fires after this pathway and has no catch object. Scanning a player's party after victory is unsafe: the clone may be in PC, simultaneous rewards can occur, and matching species is not identity. Do not roll on every storage add: trading, PC movement, and ordinary reloading must not reroll existing Pokemon.

**Proposed upstream extension, not existing:** a server-thread `RaidCatchPrepared` adapter hook after clone construction and before storage insertion, carrying encounter/raid identity, definition ID, raid tier, recipient UUID, and the new Pokemon. Ascend applies persistent acquisition metadata to that clone before it is saved. A separate success notification runs only after `party.add` succeeds; award reveal/statistics use that notification. Callback failure should prevent the integration mutation or roll back its changes, never leave partially copied boss-only traits. For a public API that avoids Cobblemon types, an isolated optional integration SPI may be preferable to expanding the encounter package with a Pokemon dependency.

Until this hook is implemented and tested, baseline raid catches may use only a separately verified generic first-acquisition mechanism with provenance `unknown` and Common rarity, consistent with the acquisition policy, if core supports it. Raid-specific rarity boosts remain disabled. Never copy a boss encounter-only affix bundle into a captured Pokemon: generate a player-valid bundle independently and preserve legitimate IVs/nature/ability/shiny data.

## Battle simulator compatibility

CobbleRaids installs a JS hook, conditions, player count patch, and output pump patch: [ShowdownIntegrationInstaller](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/showdown/ShowdownIntegrationInstaller.java:78>). Unsupported file shapes disable raids rather than patch blindly. It repairs immediately after unbundling and before context creation because another mod replacing index.js previously erased its hook: [ShowdownContextBootMixin](<L:/Codex/CobbleRaids GUIs/src/main/java/com/cobbleraids/mixin/showdown/ShowdownContextBootMixin.java:10>).

[raid-patch.js](<L:/Codex/CobbleRaids GUIs/src/main/resources/assets/cobbleraids/showdown/raid-patch.js:33>) wraps multiple simulator prototypes including logging, healing, move execution, targeting, switching, startup, and update handling. [conditions.js](<L:/Codex/CobbleRaids GUIs/src/main/resources/assets/cobbleraids/showdown/mods/conditions.js:5>) emits `-raiddamage` and swallows boss damage. Boss simulator HP is pinned, with Java owning the shared pool. Boss healing uses `-raidheal`; ordinary healing logs are suppressed for that boss.

Consequences for Ascend:

1. Player offensive affixes must modify damage before the raid shared-pool interception, once per intended hit. Never apply an additional Java-side multiplier after Showdown already applied it.
2. Boss affixes depending on low/full HP cannot read pinned simulator HP. Require an explicit shared-pool ratio bridge or disable these boss effects.
3. Healing, drain, recoil, fixed damage, residual damage, and multi-hit effects require dedicated raid tests. HP-sensitive behavior cannot be inferred from ordinary battles.
4. Preserve original simulator functions and existing wrappers; do not overwrite index.js or raid-patch.js. Proposed hooks must be scoped to Pokemon with validated Ascend battle metadata. Plain battles without affixes must be behaviorally unchanged.
5. Define and test initialization order with CobbleRaids and Mega Showdown; file presence after server startup does not prove the JS context actually loaded the bridge.
6. The bridge should expose a runtime version/capability handshake and a canary battle. If unavailable, disable affix combat/challenge entry with an operator diagnostic; do not silently show bonuses that do not function.

## Proposed adapter boundary

The following is a design contract, **not an existing API**:

- `IntegrationStatus status()` returns absent / unsupported-version / unavailable-simulator / ready plus capabilities. Never load CobbleRaids classes when absent.
- `StartOutcome startChallenge(ChallengeRequest)` validates definition, roster, rules, and capabilities, records a recoverable run ledger, then calls `CobbleRaidsEncounters.start`. Refusal releases daily reward-budget reservations. Launch Trials have no entry fee or consumed key.
- `abortChallenge(runId)` executes on the server thread, is safe to repeat, and transitions the ledger according to its prior state.
- `onEncounterEnded(result)` resolves saved run context; only an eligible victory creates reward entitlements. Use one atomic `(runId, playerId)` reward bundle covering wallet, pity, budget and attunement as the idempotency key, not listener invocation count. Left/disconnected participant policy must be explicit and recorded.
- `onPublicRaidEnded(terminalRecord)` is a proposed upstream hook, not an existing API; it validates eligibility and per-Pokemon participation, maps reward bands, and commits wallet/attunement entitlements with the shared daily budget and core pity. Crash replay requires the separately proposed durable outbox.
- `prepareRaidCatch(context, pokemon)` only becomes available after the proposed upstream hook. Assign once to the new clone using a namespaced acquisition marker and core generation service.
- `describeCapabilities()` distinguishes owned encounters, public-raid wallet terminal events, raid catch provenance, and boss affixes. Physical loot-table materials are not a launch capability. Unsupported features remain individually disabled.

Keep reward entitlements, run outcomes, and currencies in Ascend's own versioned store. Store Pokemon rarity/affixes with the Pokemon through the exact Cobblemon-supported persistence API verified for the target release. CobbleRaids' player SavedData proves nothing about per-Pokemon addon persistence; this repository has no demonstrated custom Pokemon persistent-data implementation to reuse.

## Release tests

| Area | Required assertion |
|---|---|
| Optional loading | Ascend runs without CobbleRaids; no missing-class failure; unsupported version gives a capability diagnostic. |
| Exact target | Fresh dedicated server launches with requested artifact versions; mixins apply; ordinary and raid battle canaries pass. |
| Encounter start | 1 and 4 players work; 0/5 players, duplicate/offline/world-mismatched players, unknown definition, occupied battle are refused without residual boss/session or leaked reward-budget reservation. |
| Completion | Victory grants one entitlement per eligible player; duplicate callback/reconnect does not duplicate; defeat/abort/shutdown do not grant victory loot. |
| Restart recovery | Crash before/after start and before/after entitlement persistence has a documented recovery result; no fabricated victory from an orphan boss. |
| Public raid wallet | Authoritative terminal payload identifies eligible players and participating Pokemon, correct mapped band credits once, support actions count, daily budget and pity share with Trials, native reward claims cannot duplicate wallet grants. |
| Delivery recovery | Simulated crash before terminal persistence, before event delivery, before/after wallet commit and before acknowledgment proves replay or explicit unknown-outcome reconciliation; do not claim guaranteed delivery from the existing listener. |
| Trial isolation | All native side effects disabled for owned Trial; safe copies/healing proven, no native XP/EV/HP/PP leak or item/object duplication; standalone free path remains available. |
| Raid catch | Party/PC delivery, full storage, failed insertion, multiple recipients, and callback failure preserve correct rarity assignment and provenance without announcing undelivered catches. |
| Identity | Trade, PC transfer, reconnect, evolution, and restart never reroll rarity; boss clone cannot inherit boss-only modifiers; verify clone UUID semantics on exact target. |
| Battle math | Ordinary control plus raid test for typed damage, spread/multi-hit, crit, burn, drain, recoil, residual damage, and healing; no double multiplier or erroneous shared HP UI. |
| Load order | CobbleRaids + Ascend, plus supported Mega Showdown matrix; repeated bootstrap is idempotent and loaded JS handshake is confirmed. |
| Rule behavior | Weather/terrain, switching/item bans, safety move bans, and HP scaling behave as previewed. |

## Implementation order

1. Resolve exact Cobblemon version/artifact and verify independent core battle/persistence spike.
2. Prove standalone free Trials, account-bound wallet, participation tracking, shared reward budget/pity and durable entitlement ledger.
3. Add the supported-version owned-encounter adapter with all native Trial side effects disabled; prove battle-copy safety before enabling it.
4. Implement/review the public-raid terminal event and participation contract upstream, with durable outbox/recovery semantics required for crash-safe payouts. Existing public raids retain their own policy.
5. Implement/review the explicit raid catch preparation/success hook upstream in a separately scoped change.
6. Add boss affixes only after shared-HP semantics and simulator coexistence are proven. Physical material loot tables remain a future alternative requiring an economy redesign, not a launch step.

No CobbleRaids source changes, server deployment, or compatibility claims were made by this investigation.


