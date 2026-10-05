# Capture reveal — clean pixel direction

Current direction, October 3, 2026: compact Cobblemon-style panels, crisp pixel edges, real Cobblemon Cards sprites, restrained rarity accents and deliberate short transitions. The owner rejected the ornate fantasy/mobile-card concept. Do not revive engraved silver ornaments, serif display type, gems, mist or magical circles.

## Reviewable prototype

Open `preview.html`. The initial view shows the finished sample capture. Replay starts the full reveal; Sound enables original synthesized preview cues. Choose any of the six rarities, inspect an affix, skip or use Escape, and try reduced motion. The level-28 sample has two pending milestone upgrades.

This is a browser UI prototype, not a Minecraft screen. Capture data, affix values and party delivery are sample content. It does not grant Pokémon, implement affix effects, spend currency or persist gameplay. Only preview preferences are saved. Native game integration remains future work.

Editable source: `capture-reveal-pixel.fragment.html`. The standalone wrapper is `preview.html`. The inline copy lives in the task visualization directory. Regenerate the wrapper after source changes.

## Presentation rules

- Use flat pale panels, dark outlines, stepped corners and short hard shadows. Keep borders on a consistent pixel grid.
- Show the Pokémon at an integer sprite scale. The 48×32 Growlithe texture displays at 240×160 (5×), without interpolation or altered aspect ratio. Its opaque bounds are `(9,4)-(30,26)`; the portrait compensates for asymmetric transparent padding with an integer offset. Native rendering needs per-sprite framing rather than a universal Growlithe offset.
- Keep the species portrait and affix list side by side at game-sized widths. Narrow browser previews stack them for review; this is not a request for a mobile-game layout in Minecraft.
- Mythic uses crimson with silver/white. Common is silver, Uncommon cyan, Rare blue/cyan, Epic violet, Legendary gold. Never use Collector green.
- Use the native Minecraft/Cobblemon font in-game. Pixelify Sans is a browser preview approximation.
- Rarity is an accent strip, a short color hint during anticipation, and twelve square burst particles. No full-screen flash, looping particle storm or ornamental backdrop.
- Affixes use readable names, values, rank markers and expandable explanations. Prefix/suffix markers have distinct colors and accessible text; meaning does not depend on color alone.

## Motion and sound

Sequence: sealed panel → short ball movement → rarity accent → 500 ms card turn → 340 ms panel expansion → affixes staggered 85 ms apart → settled result. Anticipation is 400 ms for Common through 1,350 ms for Mythic. Mythic completes in approximately 2.62 seconds. Timing is provisional for in-game evaluation.

All scheduled work is canceled on replay, skip, preference restoration or exit. Reduced motion shows the result immediately. Hiding the page finishes an in-flight reveal and suspends sound. No autoplay audio; audio is enabled by a user gesture. Sound cues are lightweight synthesized placeholders: anticipation notes, a three-note reveal and quiet row ticks. Final in-game audio needs dedicated sound assets and a volume category.

## Source and provenance

Growlithe is imported unchanged from Howlite-UI/CobblemonCards, commit `c02aafb50d5915b83dafa9af28ddebd58be24b5e`:

`common/src/main/resources/assets/cobblemon-cards/textures/item/cards/pokemon/entity_icon/0058_growlithe/growlithe.png`

Repository: https://github.com/Howlite-UI/CobblemonCards

The original sprite and upstream license are retained in `assets/`. The inline preview uses the same commit-pinned asset through jsDelivr. The repository declares CC0-1.0; Pokémon remains third-party franchise content. No generated creature replaces the requested sprite. CSS panel art, layout, choreography and synthesized cues are original prototype work. The rejected generated concept is not an implementation asset.

## Validation

`work/check-pixel-reveal.cjs` checks all six rarities and slot counts, affix inspection, completion/replay, full timed reveal, skip/Escape, reduced motion, runtime errors, and width/label overlap at 780, 560, 360 and 320 px. Network-enabled checks confirm the actual sprite and preview font load. Desktop and narrow screenshots are under `work/pixel-reveal-*.png`.

Next native milestone: reproduce the accepted layout at Minecraft GUI scale using resource textures and the native font, preserving species/form/shiny texture resolution. Do not ship this HTML or its synthetic browser audio as the Minecraft GUI.
