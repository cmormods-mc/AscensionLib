# Third-party build tooling

The Gradle wrapper scripts and bootstrap jar were copied from the existing local project as standard build tooling. Gradle is licensed under Apache License 2.0; see [Gradle's license](https://github.com/gradle/gradle/blob/v8.14.3/LICENSE). The wrapper downloads Gradle 8.14.3.

The addon builds against Minecraft, Fabric and Cobblemon rather than redistributing their complete jars. Production dependencies retain their own licenses. Cobblemon's artifact is pinned by Modrinth version ID and its SHA-512 was verified against the upstream release metadata. Gson is provided by Minecraft at runtime; JUnit is test-only. Development-only Kotlin/Graal/Mongo/ICU dependencies support running the target dependency under Loom.

No license for publishing the original CobbleAscend source has been selected in this prototype. Choose one before public distribution. This note does not change ownership or license terms of any dependency.

## Reveal prototype

The unchanged Growlithe sprite under `design/reveal/assets/` comes from [CobblemonCards](https://github.com/Howlite-UI/CobblemonCards), commit `c02aafb50d5915b83dafa9af28ddebd58be24b5e`. Its upstream CC0-1.0 license is retained alongside the asset. The source path and preview usage are documented in [the reveal design notes](design/reveal/README.md). Pokémon is third-party franchise content.

The browser prototype loads Pixelify Sans through Google Fonts; this is a preview font, not a bundled Minecraft asset. The native screen should use Minecraft/Cobblemon's font renderer. No generated fantasy-card concept artwork is included in the implementation.
