# CobbleAscend API feasibility research

Research date: 2026-10-03. Requested target: Minecraft 1.21.1, Fabric, Cobblemon 1.8.1. This report distinguishes source inspection from a compiled or live-tested integration. No target-runtime compatibility is claimed.

## Findings that affect the plan

### Target release and exact artifact

The official wiki dates 1.8.1 to September 13, 2026. The tagged `1.8.1/gradle.properties` declares Minecraft 1.21.1, Java 21, mod version 1.8.1, and `snapshot=false`. Thus the requested combination is a credible target, not an inferred future version. [Official release page](https://wiki.cobblemon.com/index.php/1.8.1), [tagged build properties](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/gradle.properties).

The parent research pass resolved official Modrinth Fabric 1.8.1 release ID **gBW3vLC7**. Pin `maven.modrinth:MdwFAVRL:gBW3vLC7`. Its supplied SHA-512 is `e9c475c6a8e73bfb378445944812f6e31271cdf50d3227124b44b31c295bf7e9085246ea05d005efff9b7227e7b4e3e34ff1480b1a4b3b97211b5fa127d234ed`. [First-party release API](https://api.modrinth.com/v2/version/gBW3vLC7). This agent's independent API request was unavailable; the exact artifact evidence was supplied by the coordinating research pass.

Following the wiki's Fabric Modrinth link during the initial pass opened release `kF7CvxTo`, labelled **1.7.3**. Use the resolved release above, not that stale link. [Stale linked release](https://modrinth.com/mod/cobblemon/version/kF7CvxTo).

The initial local CobbleRaids snapshot pinned Cobblemon 1.7.3. The user is handling that separately; **it is not a blocker for this standalone mod**. Raids integration is deferred by the user's latest instruction. All raid notes below are future research only.

### Capture and lifecycle events are available

Tagged 1.8.1 exposes `POKEMON_CAPTURED`, `POKEMON_GAINED`, battle start pre/post, battle victory/flee/faint, evolution complete, and trade pre/post observables. A successful catch hook can assign a profile; generic gained events must not blindly reroll it because ownership transfer and acquisition are distinct operations. This last rule is our design recommendation. [Event definitions](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/api/events/CobblemonEvents.kt).

`PokemonCapturedEvent` carries the Pokémon, server player, and thrown ball entity. This is enough to identify the recipient and subject without searching nearby entities. Exact event timing relative to store insertion still requires a runtime test. [Capture event source](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/api/events/pokemon/PokemonCapturedEvent.kt).

### Use persistent Pokémon data, with explicit dirty notification

Tagged `Pokemon` exposes an arbitrary `CompoundTag` named `persistentData`, with an internal setter. Mutate a namespaced child compound and call `onChange()` afterward: its documentation explicitly says mutations alone do not mark the Pokémon for saving. The class supports codec-based NBT and JSON serialization, and `copyFrom` assigns persistent data. It has separate server and client codecs; therefore automatic client visibility must not be assumed. These are source observations, not proof of every trade/evolution/clone path. [Pokemon source](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/pokemon/Pokemon.kt).

The original upstream change describes persistent compound data and JSON serialization through DFU. That supports the storage choice but does not substitute for current-version round-trip tests. [Merged upstream change](https://gitlab.com/cable-mc/cobblemon/-/merge_requests/484).

Design recommendation: save schema version, stable profile identity, rarity, rolled affix IDs and values, revision, and acquisition provenance under `cobbleascend`. Keep battle counters outside the permanent profile. Never repurpose species, shiny status, IVs, EVs, or the Pokémon's native ability as the affix storage mechanism. Snapshot the profile into immutable battle input. Treat source/clone identity as separate fields.

### Battle effects need a simulator implementation

Cobblemon's Graal service boots JavaScript from `showdown/index.js`, starts battles, sends battle messages, exchanges registry data, and returns simulator output to `ShowdownInterpreter`. This establishes the Java/JavaScript boundary. It does not establish a general public per-Pokémon affix registration API. [Graal service](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/battles/runner/graal/GraalShowdownService.kt).

The interpreter has public registration methods for custom update, split, and side instruction parsers. Those are output interpretation hooks; registering one does not implement simulator damage rules. Prefer normal simulator damage/healing messages for ordinary HP changes and use a namespaced custom instruction only when extra presentation or diagnostics require it. [Interpreter source](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/battles/ShowdownInterpreter.kt).

`BattlePokemon` distinguishes `originalPokemon` and `effectedPokemon`; `safeCopyOf` clones the source and marks the copy as a battle clone and uncatchable. Player-owned construction can use the same Pokémon for both roles. The adapter must support both cases rather than assume every battle uses a disposable clone. [BattlePokemon source](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/battles/pokemon/BattlePokemon.kt).

Design recommendation: implement one simulator-side damage modifier first. Never fake extra damage by independently subtracting world Pokémon HP after a move; that risks disagreement about fainting, recoil, drain, substitutes, and victories. Establish simulator ordering, fixed-point rounding, and telemetry before approving the larger affix catalog. Avoid replacing native abilities or held items to carry affixes because those are existing build mechanics.

## Java adapter signature notes

The following Java spellings follow tagged Kotlin public declarations; a successful compile against the pinned jar remains the final check. `Observable` provides explicit `Consumer<T>` overloads, so an explicit cast avoids ambiguity between Java Consumer and Kotlin Function1: `CobblemonEvents.POKEMON_CAPTURED.subscribe((Consumer<PokemonCapturedEvent>) event -> { ... });`. A priority overload accepts `Priority` then `Consumer<T>`. Save the subscription if its lifetime is shorter than the mod. [Observable declarations](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/api/reactive/Observable.kt).

- Capture accessors: `event.getPokemon()`, `event.getPlayer()`, `event.getPokeBallEntity()` from the data class cited above.
- Persistent compound: `pokemon.getPersistentData()`. Put the namespaced child into that returned compound; do not attempt the internal setter. Dirty call: `pokemon.onChange(null)` is the explicit Java call for the Kotlin defaulted nullable packet argument. Do not rely on a generated no-argument overload without confirming it in the jar.
- Identity/type accessors from the Pokémon source: `getUuid()`, `getPrimaryType()`, nullable `getSecondaryType()`, and iterable `getTypes()`. Prefer identifiers over localized labels. `ElementalType` declares `name` and `showdownId`, corresponding to `getName()` and `getShowdownId()`. [ElementalType declarations](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/api/types/ElementalType.kt).

For inspection, `Cobblemon.INSTANCE.getStorage().getParty(serverPlayer)` returns `PlayerPartyStore`; its inherited `get(int slot)` is zero-based and returns null for empty or out-of-range slots. Translate command slots 1–6 to 0–5 and report empty slots without mutation. Iteration skips empty slots, so do not derive displayed party slot numbers from iteration order. [Store manager](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/api/storage/PokemonStoreManager.kt), [party implementation](https://gitlab.com/cable-mc/cobblemon/-/raw/1.8.1/common/src/main/kotlin/com/cobblemon/mod/common/api/storage/party/PartyStore.kt).

## Deferred CobbleRaids integration implications

The local mod already installs a JavaScript raid hook, replaces its Cobblemon conditions resource, and patches simulator bootstrap/output/player count handling. Its installer documents collisions with other mods modifying the same extracted files and contains recovery checks. A second addon must not overwrite these resources or assume patch ordering. [Installer](../../CobbleRaids%20GUIs/src/main/java/com/cobbleraids/showdown/ShowdownIntegrationInstaller.java).

There is an existing **experimental** owned-encounter API, version 1, with start/abort/isActive methods and a completion listener. Its documented calls require the server thread. It is appropriate to investigate for overhaul-owned encounters, but it is not evidence of a public notification for every normal raid reward or catch. Add a narrow versioned integration contract where necessary rather than reaching into raid session internals. [Encounter API](../../CobbleRaids%20GUIs/src/main/java/com/cobbleraids/api/encounter/CobbleRaidsEncounters.java).

Design recommendation: keep the optional bridge in a separate artifact or isolated entrypoint so the core loads without CobbleRaids classes. CobbleRaids owns shared boss HP and encounter completion; CobbleAscend supplies permitted affix snapshots and grants only its own rewards. Use encounter ID + player ID + reward kind as an idempotency key. Never run a second boss-health subtraction for the same damage result. Confirm CobbleRaids on 1.8.1 independently before advertising the combined pack.

## Required technical spikes and pass criteria

| Spike | Required evidence before passing |
|---|---|
| Exact dependency | Obtain verified Fabric 1.8.1 jar, record origin/hash/embedded metadata, compile and boot a minimal addon on Java 21 and Minecraft 1.21.1. A successful 1.7.3 compile does not count. |
| Capture assignment | Capture in and out of battle, with party space and with a full party; each subject receives exactly one profile and no failed throw grants one. Repeated handling is idempotent. |
| Persistence | Profile survives save/restart, party/PC moves, trading in both directions, ordinary evolution, form changes, and relevant special evolutions; every mutation calls the dirty notification. Record any clone behavior explicitly. |
| Client inspection | Server-authoritative inspection shows exactly the saved profile; only permitted DTO fields cross the network. Editing client data cannot modify rolls or craft outcomes. |
| One affix | A type-specific damage modifier changes simulator results by the specified amount under a controlled seed; immunity, fixed damage, recoil, drain, criticals, substitute, multihit, and rounding all have explicit outcomes. |
| Safe copies | Raid/simulator copies read the original profile but cannot persist temporary counters or duplicate permanent acquisition/reward side effects. |
| Optional dependency | Core starts and normal battles work without CobbleRaids; compatible bridge advertises readiness, incompatible bridge disables its integration with a useful reason. |
| Shared simulator | Both load orders and a previously extracted simulator directory work; raid damage is applied once; ordinary battles remain correct. Test other battle-altering mods only if included in the supported pack. |
| Failure containment | Bad profile/config and unsupported simulator signature reject the affected feature without stopping ordinary battles, corrupting Pokémon data, or charging crafting material. |

## Open questions and limits

- The exact dependency is resolved above; compiling and live-testing the standalone adapter remain separate validation gates.
- The concrete simulator affix injection seam is unproven. Existing registry transport and interpreter hooks are building blocks, not a completed extension API.
- The persistent-data codec helper file was not retrieved during this pass. Round-trip persistence remains a required spike, especially through third-party storage/trade systems.
- A historical 1.7.1 bug report describes disconnects when changing forms inside capture handling. This is not proof the bug exists in 1.8.1; it is a reason to keep capture assignment limited to metadata until testing. [Upstream report](https://gitlab.com/cable-mc/cobblemon/-/work_items/1897).
- Claims in the initial pasted proposal about Buffs, Shiny Rarities, Gacha, arbitrary NBT-triggered effects, or uncapped IV/EV stat bonuses were not established here and are not dependencies of this plan.

**Recommendation:** proceed with standalone data/domain work and the exact-version capture/persistence implementation. Gate the full combat implementation on simulator tests. Leave CobbleRaids integration and its combined-runtime tests for a later phase as requested.
