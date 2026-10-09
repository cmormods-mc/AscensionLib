# Ascension inspect screen — design record

Native injection foundation prepared October 5, 2026: see [integration notes](../../docs/INSPECTION-INJECTION.md) for the built artifact, client hooks, implemented data flow and required live fitting.

Browser prototype of the planned `AscendInspectScreen`, in the clean-pixel direction (see `../reveal/README.md`) with more Cobblemon personality: type-themed battle backdrop with a platform and drifting motes, a pixel Poké Ball rarity badge (top half in the rarity colour) and close button, Pokédex-style "No." tag, segmented stat bars, diamond rank pips, a scanner-style screen for unscouted data with a target-lock reveal, a "Scouted · source" stamp and a flame-edged Unique card. Sample data only: nothing reads a real Pokémon. Recipes, costs and scouting rules are not final.

## Owner decisions (October 5, 2026)

- **Entry points are Mixin buttons from the start:** a magnifier on each Pokémon tile of Cobblemon's battle overlay and one in the summary-screen header. Both are version-pinned to Cobblemon 1.8.1 and may need rework when Cobblemon changes those screens. The preview mocks show positions only; real placement needs in-game fitting. Keep a keybind/command fallback so the screen stays reachable if a Mixin target changes.
- **Scouting gates the reveal.** Enemy rarity, modifiers and Unique stay behind an unscouted "?" layout until scouted. Once scouted, the screen shows rarity and modifiers plus level, types and base stats, never IVs, EVs or moves.
- **Scouting sources:** CobbleTowers provides a modifier that grants scouting, raids provide their own scouting, and everywhere else a craftable one-use **Scouter** item scouts any Pokémon (including wild ones) so players can decide whether to catch it. Scouters should be craftable from ordinary Minecraft progression; the recipe is not decided.
- **Wild Pokémon** show regular Cobblemon data. Their rarity and modifiers stay "?" until scouted with a Scouter; the screen then shows what they would have **if caught**. This requires a wild Pokémon's result to exist before capture (see open decision below).
- **PvP:** ascension effects are off, so the ascension panel is inactive and shows nothing.
- **Own Pokémon:** full detail, including pending upgrades and attunement. Upgrading and reforging stay in the separate approved Affix Workshop screen.

## Design rules this preview encodes

- Unscouted panels always show three placeholder rows, whatever the real slot count, so the layout cannot leak rarity. Boss and Unique presence also stay hidden until scouted.
- Scouted wild Pokémon show all slots at rank I plus the upgrades they would start with at their level, since capture awards milestones but spends none.
- Powerhouse affixes (very rare, high range) carry a visible label, not colour alone. Prefix/Suffix are text tags with distinct colours.
- Each row shows the rolled value, its rank band, rank diamonds I–V and an expandable condition. Every effect is labelled inactive until the battle integration exists.
- Escape closes the screen; reduced motion removes animation (also honours the OS setting). The Scouters-in-bag field demonstrates the "No Scouter" state.

## Open decisions

- **How a wild Pokémon's rating exists before capture.** Recommended: derive it deterministically from the wild Pokémon's own UUID plus a server-only secret and the catalog version, so a Scouter preview equals the capture result exactly, nothing needs storing, and fleeing and re-encountering cannot reroll it (a new spawn is a different Pokémon). Alternative: roll and store it on the entity at spawn.
- Scouting persistence: per encounter, per entity, or remembered per player? Does a scout carry over a flee and re-engage?
- Which Cobblemon facts are shown before scouting in tower and raid fights? The preview assumes only what the battle already shows (name, HP, status).
- CobbleTowers and CobbleRaids should reach scouting through the shared library mod. Scouter recipe, stack size and any cooldown are undecided.
- Native rendering of the Pokémon model, per-sprite framing and the exact Mixin targets are unproven and need an in-game spike.

## Provenance

Growlithe/Arcanine/Gyarados/Houndoom/Gengar pixel sprites come unchanged from Howlite-UI/CobblemonCards commit `c02aafb50d5915b83dafa9af28ddebd58be24b5e`, path `common/src/main/resources/assets/cobblemon-cards/textures/item/cards/pokemon/entity_icon/<dex>_<name>/<name>.png` (repository declares CC0-1.0; license copied to `assets/`). Pokémon remains third-party franchise content. Layout, CSS, Poké Ball and magnifier pixel art and behaviour are original prototype work. Pixelify Sans is a browser approximation of the in-game font.

Browser check (removed 2026-10-09 with the preview, see git history): `work/check-inspect-ui.cjs` (run with `PLAYWRIGHT_BROWSERS_PATH=work/preview-browsers`): scenarios, scouting reveal, no slot-count leak, wild Scouter use and the empty-bag state, entry buttons, Escape, reduced motion, layouts at 860/600/360/320 px, no script errors. Screenshots were removed with it.
