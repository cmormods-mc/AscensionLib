# Player experience specification

**Added progression screen:** [Milestone upgrades](MILESTONE-UPGRADES.md) specifies the pending choice screen earned every ten levels, including deferral, multiple credits and slot selection.

Status: proposed client behavior. Text wireframes are layout references, not implemented screens.

## First session

After the first eligible capture, show a short toast: `Common ascension · Growlithe · Fire Focus +7%`. Add a quiet prompt: `Inspect with /ascend inspect`. Do not open a modal during battle or block another throw.

First inspection teaches three things: ascension rarity is separate from species rarity; bonuses work only in supported PvE; any Pokémon can be promoted to Mythical. Place the tutorial in a dismissible help panel. Do not repeatedly explain it to every character login.

The research panel shows `3 / 5 species today · +4 Dust per new species` and the one-time five-species Facet milestone. A player earns 20 Dust and one Facet after five first-day distinct catches, then 6 Dust from one rank-1 Trial, enough for the 24-Dust / 1-Facet first reforge. Leveling and combat readiness are separate prerequisites; the onboarding estimate is not a promise that a fresh level-5 team can beat a level-30 Trial.

## Inspection

```text
Growlithe                       RARE ASCENSION [three-slot symbol]
Level 28 · Fire                  Battle effects: PvE enabled

OFFENSE
Fire Focus       +7% Fire move damage                 [4–8%]
Physical Force   +4% physical move damage             [3–6%]
DEFENSE
Opening Guard    8% less direct damage from the first
                 damaging move each battle           [5–10%]

Attunement 18 / 25 for Epic
Next promotion: +1 suffix · 100 Dust + 5 Facets

[Craft] [Progression] [Share build] [Close]
```

The selected Pokémon's nickname remains its existing nickname. Rarity is a separate badge. Show actual stored values, possible ranges and conditions. Typed bonuses describe move type, not Pokémon type. Opening Guard's expanded text explains multihit and switch behavior. Highlight active applicable bonuses only when current battle state is known; do not claim a conditional bonus is always active.

A small channel summary says `Overhaul direct-damage bonus capped at +100%` (read the figure from the active balance config, not a literal) when explaining caps; do not display a misleading permanent Attack number. The screen distinguishes native stats from ascension modifiers. If a definition is disabled or a mode is unsupported, show the exact reason beside the relevant effect.

## Crafting bench

Accessible with `/ascend craft` and a keybinding/menu button once registered. No physical block dependency in the first release. Opening the bench during battle shows a read-only profile and `Finish this battle to craft`.

```text
Craft · Growlithe · Rare          Wallet: 86 Dust · 4 Facets · 0 Cores

[Reforge] [Refine] [Promote]
Selected: Physical Force +4%

Reforge replaces this one offensive affix.
Other affixes stay fixed. The same result can occur.
Eligible outcomes: [View exact pool and chances]
Cost: 24 Dust + 1 Facet

[Reforge this affix] [Cancel]
```

First action requests a current server preview. The confirmation is bound to its Pokémon UUID, slot, revision and config hash. Do not reuse a preview after selecting a different Pokémon. Double clicks display one pending operation; retries use the same operation ID. A disconnect followed by reopening shows the persisted outcome and correct balance.

Refine copy: `Reroll this value from 3% to 6%. It may improve, worsen, or stay the same.` Promotion copy: `Promote to Epic. Keep existing affixes and gain one random suffix. Guaranteed success.` A disabled button names the unmet requirement, such as `Need 7 more attunement` or `Need 2 more Facets`.

No randomized spinning wheel or near-miss animation. A short optional change highlight is enough. Support reduced motion and instant results.

## Trial selection and encounter behavior

List rank, opponent archetype, team size/level, item policy, reward band and full-reward budget remaining before entry. Show that rank 1 has no Facets except the one-time research onboarding grant; prevent players expecting endgame currency from farming an unsuitable rank.

Launch rule: switching allowed; held items allowed; bag items disabled; standard legal moves/abilities; no consumable permanent item loss in the challenge copy. Trial copies have full HP/PP at entry; the owned original is unchanged on exit. This copying guarantee is conditional on the implementation gate; failure to prove it blocks release rather than silently consuming a player's held item.

A confirmation shows the roster and any unsupported affixes. Enhanced Trials refuse entry if the simulator capability is unavailable. A normal loss returns to the lobby, gives no material reward and releases the reserved daily budget. Victory shows one summary with wallet payout, Core pity progress and attunement awarded to participating Pokémon.

One active Trial per player. Declining or closing before admission changes nothing. Disconnect aborts a standalone Trial with no rewards and releases its reservation during recovery. An integrated public raid follows CobbleRaids membership rules; disconnecters are reward-eligible only if the authoritative raid terminal contract explicitly retains them as eligible. Do not infer eligibility from a briefly cached roster. A participant who leaves voluntarily before terminal victory receives no overhaul victory reward.

No launch timer, paid keys or death penalties. At most one unresolved run may lock a Pokémon. Operators can diagnose and resolve an orphan lock through a logged recovery action.

## Rarity language and visual treatment

Use the full phrase `Ascension rarity` in help. Compact badges may use `Rare ascension`. Species categories retain their ordinary meaning; a Mythical ascension Rattata is still not a Mythical species.

Proposed category palette: Common gray, Uncommon green, Rare blue, Epic violet, Legendary gold, Mythical rose. Contrast must be verified in the actual Minecraft UI. Always pair color with label and a slot-count/symbol treatment; no information depends on hue. Do not recolor the Pokémon model or imply shiny status.

Use Minecraft's UI scale settings, keyboard navigation and translated text. Test minimum supported window size, long nicknames, longer translations and non-Latin glyphs. Tooltip text wraps and never pushes confirmation controls offscreen. Client-server mismatch shows a compatibility message before entering the world/feature according to the chosen networking negotiation.

## Operator and failure messages

| Condition | Player-facing behavior |
|---|---|
| Stale preview | `This Pokémon or recipe changed. Review the updated preview.` No spend. |
| Already committed retry | Display the original result and current wallet. |
| Insufficient materials | Show missing amounts. No partial debit. |
| Pokémon traded or moved out of authorized ownership | `You no longer own this Pokémon.` No spend. |
| Battle/raid unsupported | Badge says `Ascension effects inactive in this battle mode`. |
| Storage temporarily unavailable | `Crafting is temporarily unavailable. Your materials were not spent.` Only use this copy when non-commit is known; otherwise show `Checking the result of your last craft.` |
| Profile quarantine | `This Pokémon's ascension data needs administrator review.` Ordinary Pokémon use remains available where safe. |
| Unknown affix | Show affix ID and `Inactive: content missing`; never erase it silently. |
| No compatible raid adapter | Hide raid-specific offers and show standalone Trials normally. |
| Daily full rewards exhausted | Show exact reduced Dust payout and no Facet/Core eligibility before admission. |

## Client acceptance checks

Capture several Pokémon rapidly with toast queue capped and no lost profiles; toggle reduced motion; inspect all rarities; reforge after a server reload invalidates the preview safely; spam confirmation without repeated charge; use keyboard-only navigation; test long localized strings; verify an unauthorized client cannot inspect arbitrary PC entries or submit a made-up crafting result.

## Accepted direction: card-style capture reveal (2026-10-03)

Owner requested a custom capture rarity screen based on the open-source CobblemonCards architecture. This supersedes the earlier toast-only default as the intended full reveal mode; compact notifications remain an optional setting. Screen implementation has not been added yet.

Source reviewed at commit `c02aafb50d5915b83dafa9af28ddebd58be24b5e`:

- [BoosterPackScreen](https://github.com/Howlite-UI/CobblemonCards/blob/c02aafb50d5915b83dafa9af28ddebd58be24b5e/common/src/client/java/com/howlite/cobblemoncards/screen/BoosterPackScreen.java): a Minecraft Screen with five item-backed cards, per-card flip/shake state, rarity-sensitive sounds, glow/rays and screen particles. `setRewards` accepts ItemStacks; screen removal sends its own CloseBoosterPayload. These are implementation hooks, not a demonstrated general-purpose capture-reveal API.
- [OpenBoosterPayload](https://github.com/Howlite-UI/CobblemonCards/blob/c02aafb50d5915b83dafa9af28ddebd58be24b5e/common/src/main/java/com/howlite/cobblemoncards/network/OpenBoosterPayload.java): mod-specific opening message.
- [License](https://github.com/Howlite-UI/CobblemonCards/blob/c02aafb50d5915b83dafa9af28ddebd58be24b5e/LICENSE): repository declares CC0 1.0. Any adapted source should retain an upstream provenance note; original presentation assets are preferred.

Selected approach: a CobbleAscend-owned `CaptureRevealScreen`, informed by or adapting the relevant reveal animation code, using a dedicated read-only capture-result payload. Avoid a hard dependency on the full card mod and its item/reward pipeline. Never invoke its booster close/reward actions for a captured Pokémon.

Flow: confirmed capture and persisted rarity → recipient-only payload → queue until battle/another essential screen finishes → one centered Pokémon card → flip/glow → species/nickname, ascension rarity and actual rolled affixes. The server determines and saves the outcome before the animation. Closing or skipping the screen cannot reroll, grant, remove or duplicate the Pokémon.

Default proposal: a reveal for each successful ordinary capture, with instant skip, reduced motion and optional compact mode. Rapid captures queue by Pokémon/profile identity; existing screen content is never overwritten by a newer result. Queue limits should collapse overflow into an inspectable recent-captures list rather than drop saved outcomes. Reconnect never changes rarity; replaying reveals is presentation-only. While the world continues running, allow immediate dismissal and avoid claiming the screen pauses multiplayer.

Presentation: one original card frame; Pokémon portrait/model; colored rarity label plus symbol; affixes below; optional Inspect and Continue actions. Mythical presentation may last longer but is always skippable. The specific Cobblemon portrait-renderer hook and the adapted screen must be tested on our 1.8.1 runtime. Upstream uses `mythic`; our domain continues using `mythical` with an explicit mapping if upstream animation utilities are adapted.

### Owner palette update

Capture rarity styling follows the owner's title artwork: Common iron/slate; Uncommon cyan from Novice Raider's crystal; Rare Veteran Raider blue/cyan; Epic Legend Slayer violet/indigo; Legendary Champion Raider gold/navy. Mythic uses the separately supplied Myth Buster title's crimson, silver trim and white highlights. Collector's emerald palette is excluded. The sixth distinction is derived from Novice Raider's separate metal and crystal accents; it is a design mapping, not an original rarity classification in the artwork. Preview hex values are in `design/rarity-colors.json`. Display label is Mythic; the existing internal `mythical` ID is unchanged.
