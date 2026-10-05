# Implementation roadmap and acceptance gates

Status: roadmap for the full product. A compiled capture/inspection prototype and tested domain foundation now exist; see BUILD-STATUS.md for the exact implemented subset. CobbleRaids integration is deferred by owner instruction and is not a standalone release gate. Effort bands are sequencing estimates for one developer with modding experience, not delivery commitments. Unknown simulator/version work dominates the schedule.

## Critical path

Exact runtime → persistent capture slice → one real combat affix → atomic crafting → standalone Trials → full client/content/balance validation. Raid integration follows separately.

Do not start elaborate art, a passive tree or a large affix catalog before proving the simulator hook. Do not ship raid compatibility based on the existing 1.7.3 source compiling.

## M0 — exact-version feasibility (estimate 3–8 developer days)

Deliver a minimal isolated Fabric project, exact dependency manifest/hashes, dedicated-server launch log and proof-of-concept branch. Obtain the 1.8.1 binary from a verified first-party distribution or a clearly identified source build. Verify embedded metadata and transitive Kotlin/Fabric requirements. Source-build results must not be called official binary validation.

Tasks:

1. Compile and launch a minimal addon on Minecraft 1.21.1 / Fabric / Cobblemon 1.8.1 / Java 21.
2. Trace capture/gained/store event ordering and hatch provenance; confirm dirty notification and PC/trade/evolution serialization.
3. Establish a namespaced simulator metadata path and handshake.
4. Implement Fire Focus at a fixed +8% in one controlled battle without changing ability, held item, IVs or EVs.
5. Establish how to launch a standalone NPC Trial and observe per-Pokémon participation and terminal outcome.
6. Record standalone runtime compatibility. The owner handles CobbleRaids separately; no raid port work is in this milestone.

Pass: controlled unmodified-vs-modified damage comparison with exact recorded seeds and explained simulator rounding; save/restart/trade PC round trips; no effect in PvP; no client/server HP disagreement; native move mechanics preserved. Test fixed damage, immunity, multihit, substitute, drain, recoil, crits and simultaneous raid bootstrap when available.

Fail/exit: if no composable simulator hook exists, stop combat-affix expansion and propose an upstream extension or explicitly scoped pinned patch. Keep capture/metadata work as a prototype; do not substitute fake HP damage or claim the overhaul is playable. If standalone Trial admission cannot be implemented, full progression release waits for that work.

## M1 — durable capture foundation (estimate 3–5 days)

Implement domain rarity/affix generator, profile schema, server authority initialization, SQLite store, Pokémon projection/reconciliation, capture/hatch/import/legacy policies and `/ascend inspect`.

Pass: seeded unit/property tests for weights and exclusive families; every tier fills its slots; callback duplication never rerolls; normal capture and full-party PC delivery behave correctly; storage failure grants no stipend; SQLite restart recovery and authority mismatch handling; clone identity detection; native stats unchanged. Capture thousands of synthetic domain records without a whole-PC scan per tick.

Deliverable: usable capture rarity inspection, explicitly no promised combat benefit beyond the proven affix prototype.

## M2 — playable combat alpha (estimate 5–10 days)

Integrate immutable battle snapshots and the eight alpha templates, all rarity slots, PvE/PvP classification and clear unsupported-mode behavior. Add one rank-1 standalone Trial, per-Pokémon participation and one durable result path. First alpha craft is Reforge; supply admin-granted Facets for internal tests until research/milestones are ready.

Pass: simulator fixtures cover every alpha condition; +25/+15 caps and native rounding agreed; switching/faint/revive reset rules correct; unknown affix disabled visibly; reload affects new battles only; Trial copies never consume permanent held items or modify the originals; admission/refusal/abort leave no orphan boss or Pokémon lock.

Go/no-go playtest: ten runs with a small human group establish that affixes are understandable and the first Trial is playable. This is a planned exercise, not completed in this package.

## M3 — economy and client beta (estimate 5–10 days)

Implement research/milestone rewards, wallets, all three crafts, promotion/attunement, full reward budgets/Core pity, client inspection/preview/confirmation and remaining four affixes. Add three Trial ranks and three archetypes, content validation and unlocks.

Pass: one DB commit covers payout + pity + budget + attunement; concurrent/replayed requests cannot overdraw wallets; crash before/after commit recovers the correct revision; negative malformed values rejected; no award from failed/aborted encounters; first-session flow works; unknown metadata and upgrade migrations preserve evidence; visible probability matches real outcome selection.

Gate: exact source-data legality validation for every Trial Pokémon, move, ability, item and form. Recompute balance report after content changes. Client copy describes disabled effects and time assumptions accurately.

## M4 — deferred optional CobbleRaids integration (outside current scope)

The owner has handled CobbleRaids version work separately. When integration is requested later, verify its supplied public contract and add an optional adapter against that supported API version. Add narrow proposed public-raid terminal/participation and raid-catch prepare/commit hooks; establish durable event reconciliation for terminal wallet rewards. Do not scrape chats or intercept private grant internals.

Pass: core boots without raids; adapter rejects incompatible versions; public and owned raids have distinct reward ownership; 1–4 participant trials; exact once-only wallet entitlements; no boss-affix inheritance on catch; raid catch party/PC/failure cases; native raid currencies/reward queue unchanged; damage applies before shared-health interception exactly once; failed handshake refuses enhanced entry.

Integration cannot be marketed as complete until these gates pass. Public raid reward hooks are new work, not currently available in the inspected API. Boss HP-sensitive affixes remain excluded.

## M5 — release candidate and operator pack (estimate 5–10 days)

Deliver compiled jars and checksums, configuration reference, migration/backup/uninstall guide, known compatibility list, admin help, changelog and reproducible QA report. Run dedicated-server and client matrix, not only unit tests.

Pass: two restart/restore rehearsals using world+database backups; corruption/quarantine test; supported load-order matrix; 1,000 automated battle lifecycle soak; bounded resource use; client accessibility checks; aggregate balance review; no unresolved critical duplication/data-loss/combat-desync defect.

A rough standalone total excluding deferred M4 is 21–43 developer days before contingency, with a reasonable 30–50% reserve for simulator and upstream compatibility work. This is a planning range, not a promise, and a failed M0 can require redesign. A single vertical slice should be evaluated before committing to the full schedule.

## Detailed acceptance matrix

| ID | Scenario | Expected assertion |
|---|---|---|
| ACQ-01 | Ordinary capture, battle/non-battle | Exactly one canonical profile; matching projection and reveal. |
| ACQ-02 | Full party sends capture to PC | Profile accessible after PC withdrawal and restart. |
| ACQ-03 | Storage rejects capture | No owned profile/research award; no success toast. |
| ACQ-04 | Capture followed by generic gained event | Same profile ID/revision; no reroll. |
| ACQ-05 | Egg/trade/admin/legacy/import | Correct distinct policy, no wild stipend for non-wild origins. |
| LIFE-01 | Evolution/forms/PC/restart/trade | Rarity/affixes/attunement preserved under verified identity semantics. |
| LIFE-02 | Two Pokémon clone one profile | Quarantine duplicate progression without silently spending materials. |
| LIFE-03 | Deleted catalog entry/future schema | Raw data retained, mutation blocked or effect visibly disabled. |
| CRAFT-01 | Duplicate confirm/reconnect | One debit, one committed result, same operation ID replay. |
| CRAFT-02 | Two crafts or trade racing | One authorized revision wins; others rejected or re-previewed. |
| CRAFT-03 | Crash before DB commit | No persisted debit or profile change. |
| CRAFT-04 | Crash after DB commit before projection save | Canonical result reprojects on load; no second charge. |
| CRAFT-05 | Config changed since preview | No spend until a fresh preview is confirmed. |
| BTL-01 | Fixed damage/OHKO/residual/recoil/self-hit | No launch direct-damage affix effect. |
| BTL-02 | Multihit crossing health threshold | Snapshot condition stays fixed for that move. |
| BTL-03 | Protected/immune incoming move | No positive damage created and Opening Guard unconsumed. |
| BTL-04 | Switch/faint/revive | Opening Guard never resets; other conditions recalculate next move. |
| BTL-05 | Drain/recovery/Rock Head/Magic Guard equivalents | Native mechanics respected; no recursive or doubled healing. |
| BTL-06 | PvP including custom/doubles classification | All sides have ascension effects disabled consistently. |
| BTL-07 | Transform/Illusion/form changes | Identity map correct; original affix ownership preserved. |
| BTL-08 | Hot reload during active battle | Existing snapshot unchanged; next battle sees new catalog. |
| RUN-01 | Rank locked/invalid roster/already in battle | No admitted run, lock or reward reservation leak. |
| RUN-02 | Trial victory/defeat/disconnect/server stop | Correct terminal state, copies isolated, one reward or no reward as specified. |
| RUN-03 | Participant only on bench | No attunement; participation proof required. |
| ECO-01 | Midnight/restart/repeated species callback | Unique daily research and shared reward budget stay bounded. |
| ECO-02 | Core pity 3 misses then next eligible win | Exactly one Core and counter reset; overflow win doesn't advance pity. |
| ECO-03 | Rank 3 win duplicated through raid callbacks | One bundle of currency, attunement, budget spend and pity update. |
| RAID-01 | Boss damage across multiple participant battles | One shared-pool application per valid hit, no Java re-multiplier. |
| RAID-02 | Catch boss clone | New owned profile, no boss-only effects, success reveal only after insertion. |
| RAID-03 | Normal raid vs owned Trial | No duplicate native/overhaul rewards; correct independent policies. |
| OPS-01 | Missing/mismatched canonical DB | Mutations stop with actionable recovery message, no silent reset. |
| OPS-02 | Restore consistent bundle | Authority IDs agree, ledgers/profile revisions reconcile. |
| UI-01 | Spoofed IDs/ranges/output packets | Rejected server-side; no private profile leak. |
| UI-02 | Long names/translations/scales/reduced motion | All essential controls readable and reachable. |

Each eventual test report must state exact artifact versions, OS/Java, configuration hash, seed where applicable, assertions and observed outcomes. Static planning checks completed in this package are separate from these future runtime checks.

## Risk register and responses

| Risk | Impact | Response / stop condition |
|---|---|---|
| Wrong 1.8.1 download link | Tests accidentally target old Cobblemon | Inspect metadata/hash; block M0 until exact artifact identified. |
| Competing simulator patches | Broken damage or raids | Shared handshake/composable hook; disable unsupported enhanced modes. |
| Copied native identity | Duplicate progression or wrong owner | Explicit profile identity, clone tests and quarantine. |
| Wallet/Pokémon save skew | Lost materials or rollback exploit | Canonical DB transaction and revisioned projection recovery. |
| Missing public raid terminal contract | Lost/duplicated added rewards | Implement narrow durable hook before claiming public-raid support. |
| Trial execution unavailable | Core cannot earn endgame materials | M0 proves NPC launch; standalone progression is a release gate. |
| Percentage-only affixes feel bland | Weak build identity | Test twelve modest effects first; add individually proven mechanics post-launch. |
| High rarity dominates | Common captures discarded | Deterministic promotion and same value ranges; adjust caps/slots after playtest. |
| Economy too slow/fast | Grind or trivial progression | Configurable costs/rewards and measured cohort tests, no hidden nerfs. |
| Generic acquisition race | Wrong rarity origin | Serialize and reconcile explicit acquisition context; no arbitrary timer guess. |
| Backup omits DB or WAL | Progress lost | Matched backup procedure and restore rehearsal mandatory. |

## Post-launch expansion order

After stable release: more condition-driven affixes with tested simulator support; encounter modifiers using existing legal battle rules; Expeditions; build-sharing presets; special cosmetic milestones. Consider passive specialization only after actual build diversity data. Do not commit to real-time combat, a market or an atlas-sized progression system as hidden release requirements.
