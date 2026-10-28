# Session state: AscensionLib

Branch `cmor/amazing-hopper-1r6071`. Latest release `1.0.0+fabric`, plus Halloween 2026 work on top.
Target: Minecraft 1.21.1, Fabric (Kotlin 2.2.20, loom 1.13.6), Cobblemon 1.8.1 or 1.8.2. Server-side; clients need WebGUI and Rinku. Polymer is optional (item art).
Commit and push to the branch once tested; no PRs unless asked. Glossary: `CONTEXT.md`. Changelog: `CHANGELOG.md`.

## Where things are
* Build and test: `./gradlew build`. Release steps: `docs/RELEASING.md`. Live checks: `docs/LIVE-TEST-CHECKLIST.md`. Build status: `docs/BUILD-STATUS.md`.
* CobbleRaids integration: `docs/COBBLERAIDS-INTEGRATION.md`. Halloween design: `docs/HALLOWEEN-EVENT-DESIGN.md`.
* Player commands are only `/ascend dex|guide|status|scout`; the rest live under `/ascend admin`.

## Recent state (newest first)
* Halloween 2026 upgrade screen: Haunting row (roll, odds, ten icons) and a spooky page theme in the event window.
* World Boss art and sound: event music, glass dome panels, haunted boss bar.
* Halloween 2026: Spooky Essence, ten Hauntings, World Boss reward.
* Calm effects option and persistent overlay; AscensionDex kept for survival players; Fuse button in the Dex.
* SnobbleAuctions crash-duplicate guard (`docs/AUCTION-CRASH-WINDOW-NOTE.md`).

## Known gaps
Update this list at the end of each session: anything untested live, unwired, or promised to the user.
