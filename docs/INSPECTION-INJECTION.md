# Native inspection integration — October 5, 2026

Prepared in the `ascensionlib` module for Minecraft 1.21.1 / Fabric / Cobblemon 1.8.1. Output: `ascensionlib/build/libs/AscensionLib-0.1.0-prototype.jar`. Both client and server need the matching library build for the added owned-inspection channel.

## Implemented

- `AscendInspectScreen`: native Minecraft screen based on the supplied inspection design. Pale pixel-bordered data panels, themed portrait header, affix category/rank/value rows, Unique name panel, segmented base-stat bars, ownership progress, fixed three-row unknown layout and a server-backed Use Scouter action.
- Own inspection requests verify UUID membership in the requesting player's party or PC and current ownership. They read the canonical profile; inspection cannot spend upgrade credits, initialize a profile, reroll or edit a Pokémon. Responses contain base stats, not IVs, EVs or moves.
- Summary Mixin adds a native magnifier control and reads the currently selected Pokémon on click, including after party selection changes.
- Battle overlay Mixin adds magnifier marks for active tiles. BattleGUI intercepts only left clicks inside these marks; ordinary battle clicks keep their existing behavior. Own party Pokémon route to owned inspection, opponents to the existing permission-filtered encounter inspector.
- Closing restores the originating screen. J remains the fallback for the current encounter. Client-only classes/mixins stay in the client source set/configuration.
- Growlithe, Arcanine, Gengar, Houndoom and Gyarados sprites are bundled unchanged with the upstream license. Portrait canvases preserve the source 48×32 aspect ratio at 2× scale. Other species use a text fallback until the complete sprite/model resolver is implemented.

## Verification performed

The inspection reveal now lasts 2.6 seconds after an already-open unknown enemy receives authorized details. It uses fixed unknown rows, a stepped scan line, four scanner status phases and rising vanilla chime cues. Skip scan/Escape reveal immediately; Motion: Off skips the sequence. Sound: Off stops the screen's active chimes and mutes later cues. Replay scan uses the already-authorized entry and sends no scouting packet or currency debit. Closing, navigation, subject removal or encounter replacement cancels the sequence and stops its sounds. Previously scouted entries and owned-Pokémon inspection open immediately. Presentation never performs an additional roll, scouting grant or debit. Motion and sound preferences currently last for the client session.

The native screen currently uses AscensionLib's shared warm parchment pixel frames. The browser reference's blue/cyan panels, detailed affix cards and full portrait header are not an exact visual match yet; the payload and live fitting gaps below remain necessary for that final pass.

`:ascensionlib:build :domain:test :store:test` completed successfully. The existing suites total 171 tests with no failures or errors. The packaged Summary injection selector remaps to Minecraft's intermediary `method_25426()V`. Build packaging contains the inspection classes, client mixin configuration and texture assets.

These checks prove compilation, packaging and existing rule/store regressions. They do not prove Mixin application, pixel placement or live requests in a running game. No deployment or world change was performed.

## Required live fitting and remaining adapters

1. Open summary, change party selection, inspect party and PC Pokémon, close and return, resize at GUI scales 2–4. Fit the magnifier into the actual header; the current coordinate derives from Cobblemon's version-pinned 331×161 summary bounds.
2. In singles/doubles and compact battle layouts, check every tile's magnifier, animated tile movement and return to the same battle action screen. Current placement follows 1.8.1 tile X displacement and slot/group Y offsets; it needs live overlap checks.
3. Opponent inspection currently opens the registered encounter's enemy list, with previous/next controls. A clicked tile is not yet securely mapped to a declared enemy index. Do not claim exact per-tile enemy identity until the encounter owner supplies that mapping, especially for duplicate species, forms and nicknames.
4. Wild encounters are not yet registered in the scouting channel. An unregistered wild battle displays an unknown/empty inspection state and cannot spend a Scouter. Connect battle entity UUID to the existing deterministic wild rating before enabling wild scouting; verify capture uses the same identity/result.
5. Tile clicks in a battle with player actors on both sides suppress ascension details and the Scouter action. Verify this in live PvP and mixed multiplayer formats. Own-Pokémon summary inspection outside battle still displays the owner's data.
6. The screenshots' exact HP/status, scouting-source stamp, Pokédex number, Powerhouse flags, authoritative enemy-specific roll ranges and Unique effect descriptions are not yet represented by the existing Scout payload. Add explicit authorized server fields before displaying them; do not infer them from a client catalog or sample data.
7. Add species/form/shiny-aware sprite resolution or Cobblemon's native portrait renderer. The five bundled regular sprites are reference assets, not full Pokémon appearance coverage.
8. Test empty wallet, successful scouting, duplicate clicks, invalid owned UUID, ownership transfer while open, disconnect, encounter end and stale responses. The Use button disables pending requests and retries only after response/timeout; the existing server scouting service owns spending and reveal scope.

The browser preview under `design/inspect/` remains the complete art/interaction reference. This build is the native injection foundation; the gaps above are explicit completion gates for matching that reference fully.
