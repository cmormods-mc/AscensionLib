# CobbleAscend

A standalone turn-based ARPG progression addon for Minecraft 1.21.1, Fabric and Cobblemon 1.8.1. **CobbleAscend is a working title.** CobbleRaids integration is deferred until later, as requested.

This workspace contains a complete first-release design proposal and a compiled prototype foundation. It is not the finished overhaul.

## Start here

The current reveal-screen direction is the [clean pixel UI prototype](design/reveal/README.md), with an interactive [preview](design/reveal/preview.html). It uses the actual Cobblemon Cards sprite and supersedes the rejected ornate fantasy-card direction.

The matching [affix upgrade UI](design/affix-upgrades/README.md) has an interactive [Upgrade/Reforge preview](design/affix-upgrades/preview.html), including confirmation, result and unavailable states. These previews do not modify game data.

Start with the [Technical Design Specification](docs/TECHNICAL-DESIGN-SPECIFICATION.md), the current development plan covering ranked affixes, milestone upgrades, targeted reforging, Unique Catalysts, persistence, battle effects and capture reveals. It supersedes conflicting rules in older drafts. The [review guide](docs/REVIEW-GUIDE.md) provides historical proposal context.

1. [Game design](docs/DESIGN.md): capture rarity, affixes, lifecycle rules and intended scope.
2. [Economy](docs/ECONOMY.md) and [calculated balance](docs/BALANCE-RESULTS.md): odds, crafting costs, rewards and progression assumptions.
3. [Build status](docs/BUILD-STATUS.md): exactly what exists, what was verified and what remains.
4. [Implementation roadmap](docs/IMPLEMENTATION.md): milestones, risks and acceptance tests.
5. [Architecture](docs/ARCHITECTURE.md) and [player experience](docs/UX.md): persistence, battle boundaries, screens and recovery behavior.
6. [API research](docs/API-RESEARCH.md): primary-source evidence for the target runtime.

## Main design decisions

- Common → Mythical rarity controls one to six affix slots.
- Every Pokémon can eventually become Mythical through guaranteed promotion.
- Twelve prototype affix templates form the starting catalog; the specification adds damage-over-time and other build-enabling effects. Native IVs, EVs, abilities and held items keep their roles.
- Every ten levels grants a pending choice to advance one ordinary affix slot's rank. Reforging preserves that rank.
- A scarce Unique Catalyst adds one selected compatible Unique power in a separate slot, preserving ordinary affixes and ascension rarity.
- Reforge changes one selected affix; Refine rerolls its value; Promote unlocks a slot.
- Standalone Trials and research provide progression without raids.
- PvP remains vanilla; battle affixes target supported PvE.
- Full-product crafting uses a canonical transaction store and per-Pokémon metadata projection. The prototype has no live spending or wallet persistence yet.

## Code present now

`domain` contains validated rules, exact weighted rarity selection, affix generation with exclusive families, immutable profiles, profile serialization, promotion/reforge/refine calculations, wallet arithmetic and reward/Core-pity calculations. Rules are loaded from the JSON files in `design`, so documentation analysis and prototype generation share their inputs.

`fabric` contains the mod entrypoint, a successful-capture subscription, deferred ownership confirmation, per-Pokémon prototype metadata with Cobblemon dirty notification, and inspection/operator initialization commands. Existing data is preserved; invalid or future data is rejected without rerolling it.

**Combat affixes are inactive.** In-game commands explicitly state this. Pure crafting/reward calculations are not exposed as live transactions. No GUI, NPC Trials, canonical database, research payouts, hatch hook, automatic legacy migration or raid integration is implemented.

Prototype metadata uses schema 0 and stores JSON under `persistentData.cobbleascend`. The planned production schema 1/database migration is separate work. Treat this as a development-world prototype, not a production progression release.

## Build and inspect

Requires a Java 21 JDK. Normal Gradle entry points:

```powershell
.\gradlew.bat :domain:test :fabric:build
python .\tools\analyze_balance.py
```

If the local JAVA_HOME points to a missing installation, use the helper with an explicit JDK directory:

```powershell
.\tools\build.ps1 -JavaHome 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.1+1'
```

Output: `fabric/build/libs/CobbleAscend-0.1.0-prototype.jar`. The domain jar is nested automatically. The artifact has no CobbleRaids dependency and does not require SkiesGUIs. The matching Cobblemon/Fabric runtime is required.

Prototype commands:

- `/ascend status` — implemented-feature status.
- `/ascend inspect [1-6]` — read one owned party slot; no mutation.
- `/ascend admin initialize <1-6>` — permission level 2; initialize an unprofiled Pokémon in the operator's own party as Common. Existing profiles are retained.

New confirmed wild captures receive a random rarity and filled affix slots. PC-delivered captures can be inspected after withdrawing them to the party. Live save/restart/trade/evolution behavior still needs the in-game acceptance matrix in the roadmap.

## Development runtime

Kotlin's Gradle plugin is enabled even though addon sources are Java: Loom needs its metadata remapper for Cobblemon's reflective Kotlin serializers. Development runtime dependencies also restore libraries that Loom's remapped Cobblemon dependency does not supply as nested jars. `runServer` explicitly adds the simulator's ICU dependency because Minecraft marks its own ICU library as client-only.

The loader smoke check uses `fabric/run/eula.txt` set to false and stops before world startup. A successful build or loader check is not an in-game capture/battle test. Do not infer gameplay readiness from Gradle's exit status alone; the startup log must also be inspected.

## Review priorities

Review the current specification's slot progression, Unique powers, account-bound material wallet, battle rules and acceptance criteria. The mod name and numerical balance remain provisional. The next implementation milestone is the ranked domain foundation; durable crafting, simulator integration and native screens follow in the specified dependency order.

The retained [raid investigation](docs/COBBLERAIDS-INTEGRATION.md) is background material for later integration. It does not impose work on the user's separately maintained CobbleRaids version.
