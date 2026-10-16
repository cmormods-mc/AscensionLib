# Third-party build tooling

The Gradle wrapper scripts and bootstrap jar were copied from the existing local project as standard build tooling. Gradle is licensed under Apache License 2.0; see [Gradle's license](https://github.com/gradle/gradle/blob/v8.14.3/LICENSE). The wrapper downloads Gradle 8.14.3.

The addon builds against Minecraft, Fabric and Cobblemon rather than redistributing their complete jars. Production dependencies retain their own licenses. Cobblemon's artifact is pinned by Modrinth version ID and its SHA-512 was verified against the upstream release metadata. Gson is provided by Minecraft at runtime; JUnit is test-only. Development-only Kotlin/Graal/Mongo/ICU dependencies support running the target dependency under Loom.

AscensionLib's own source is released under the MIT licence (see `LICENSE`). This note does not change the ownership or licence terms of any dependency.

## Reveal prototype

The browser design previews, which carried an unchanged Growlithe sprite from [CobblemonCards](https://github.com/Howlite-UI/CobblemonCards), commit `c02aafb50d5915b83dafa9af28ddebd58be24b5e` (upstream CC0-1.0), were removed from the repository on 2026-10-09 together with the sprite and its license copy; they remain in git history. See [the reveal design notes](design/reveal/README.md). Pokémon is third-party franchise content.

The removed browser prototype loaded Pixelify Sans through Google Fonts; no font is bundled. The native screen uses Minecraft/Cobblemon's font renderer. No generated fantasy-card concept artwork is included in the implementation.

## Fusion lore research

The species lore under `design/lore/` was designed after reading Pokemon data from Cobblemon's own species files, PokeAPI (Pokedex entries, genus, habitat) and Bulbapedia (Origin and Biology sections, which are available under CC BY-NC-SA 2.5). The text of those sources is not redistributed: the repository contains only short notes written in our own words about what each species is based on, and our own motif assignments. Pokemon is third-party franchise content.

## Runtime and shipped material

- **WebGUI** (MIT, github.com/mc-webgui/webgui): the client mod that shows our pages. The build compiles against it; it is not redistributed in our jar.
- **Polymer** (LGPL-3.0, github.com/Patbox/polymer): optionally hosts our resource pack on a server that already runs it. Not bundled.
- **Cobblemon**, **Fabric** and **Fabric API**: pinned dependencies, each under its own licence. Cobblemon's artifact is pinned by Modrinth version id and SHA-512 in `tools/verify_artifact.py`.
- **Monocraft** (SIL OFL 1.1, github.com/IdreesInc/Monocraft): the pixel font our pages use, bundled as `web/ascensionlib/fonts/Monocraft-latin.woff` with its licence text beside it.
- **Item art, mod icon and page graphics**: drawn in code by `tools/pixel_art.py`; original to this project.
- **Rarity Tooltips** (MIT, github.com/SnuzzleWump/rarity-tooltips) by Snuzzle: no code or art is used. We borrowed the idea of rarity-coloured tooltips; its frames need Minecraft 1.21.2 or later (`tooltip_style`), so on 1.21.1 we colour a rarity line instead.
- Pokémon, Cobblemon species names and the Pokédex concept are third-party franchise content.
