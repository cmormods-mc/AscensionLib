# Profiles on every wild Pokémon (design, numbers provisional)

Status: owner decisions and the rarity table approved 2026-10-16; built the same day (steps 1 to 4 and hatching below). Not yet built: the Scouter on a trainer's Pokémon (it works on wild Pokémon everywhere; a trainer's Pokémon is rated and fights with its build but cannot be scouted yet). Not yet verified live: the two ID assumptions at the end. Supersedes the "profiles act only in zones" rule in `PROFILE-ZONES-AND-SIGIL.md` (that document stays correct for how zones and Sigils work).

## Decisions (owner, 2026-10-16)

1. **Profiles act against every wild Pokémon**, in every dimension, and **every wild capture gets a profile**.
2. **Hatching gives a profile**, rated at the ordinary tier (1). Gifts, trades and starters stay unprofiled.
3. **Pokémon already owned stay unprofiled** and are left to the Ascension Sigil (the login sweep never creates profiles).
4. **The Sigil stays for now** (its job: profile an unprofiled Pokémon); it may be removed later if it becomes obsolete.
5. **Ordinary dimensions use the ordinary rarity table** (55 / 28 / 12 / 4 / 0.9 / 0.1 percent), tuned later from live data through the config file.
6. **Dungeons give higher rarity** through an explicit per-tier weight table (below), replacing "best of N draws".
7. **PvP stays native for both sides** until there is data to balance it.
8. **Trainers' Pokémon can have profiles** (they are rated like wild Pokémon, at the dimension's tier).
9. **Tower and raid encounters** keep their own arming and are unchanged.

## What changes

| Today | After |
|---|---|
| Only registered dimensions ("zones") rate wild Pokémon and profile captures; everywhere else is native | An unregistered dimension is a zone of the **default tier (1)**; a setting names the default and per-dimension overrides, including "off" (tier 0, native) |
| Capture profiles only inside a zone | Every capture profiles at the zone's tier |
| Hatching: no profile | Hatching profiles at tier 1 |
| NPC trainer Pokémon: native unless a tower arms them | Rated like wild Pokémon at the dimension's tier |
| Higher tier = best of 2 to 4 draws from the ordinary table (capped at tier 4) | Higher tier = its own weight table (below), tiers 1 to 10 all different |
| Scouter works on declared encounters and registered zones | The Scouter works on any wild or trainer Pokémon in the battle you are in |
| Client badge "inactive in this battle" | Gone (only PvP shows "native") |

## Provisional rarity table by zone tier (percent)

Each tier tilts the ordinary weights by `exp(0.30 x (tier - 1) x rarity index)`, so every step toward rarer is made more likely at the same pace. Tier 1 is the ordinary table. Editable in `config/ascensionlib.json` once built.

| Tier | Common | Uncommon | Rare | Epic | Legendary | Mythic |
|---|---|---|---|---|---|---|
| 1 (ordinary) | 55.00 | 28.00 | 12.00 | 4.00 | 0.90 | 0.10 |
| 2 | 42.99 | 29.54 | 17.09 | 7.69 | 2.34 | 0.35 |
| 3 | 30.22 | 28.03 | 21.89 | 13.30 | 5.45 | 1.10 |
| 4 | 18.46 | 23.12 | 24.37 | 19.98 | 11.06 | 3.02 |
| 5 | 9.54 | 16.13 | 22.95 | 25.40 | 18.97 | 7.00 |
| 6 | 4.15 | 9.47 | 18.18 | 27.17 | 27.39 | 13.64 |
| 7 | 1.54 | 4.75 | 12.32 | 24.84 | 33.81 | 22.73 |
| 8 | 0.50 | 2.10 | 7.34 | 19.99 | 36.73 | 33.33 |
| 9 | 0.15 | 0.84 | 3.97 | 14.58 | 36.17 | 44.30 |
| 10 | 0.04 | 0.31 | 1.99 | 9.88 | 33.08 | 54.69 |

For comparison, the best-of-N rule it replaces gave 0.4 percent Mythic at tier 4 and above. Tier 10 here is deliberately extreme; ascensionDungeons decides which tiers it actually uses.

## Must be verified before relying on it

- **A wild Pokémon keeps its ID through capture.** The fight is rated from that ID, so if it changes at capture the captured Pokémon would not match the one fought. (Open since the first profile-zone design.)
- **A trainer's Pokémon has a stable ID** for the length of a battle, and a way to be told apart from a player's.
- **Cost.** Rating every wild and trainer Pokémon in every battle is one HMAC and a few draws each; measure it with the existing profiler before shipping.
- **Modifiers act in battle only with CobbleRaids installed.** Without it every profile is cosmetic; the install guide says so.

## Build order

1. Default tier setting and per-dimension overrides (`ProfileZones`, config), with tests.
2. Per-tier rarity weights in the config and `RankedProgression.rateWild`; the best-of-N rule is removed. Tests: every row sums to 100 and tier 1 equals the ordinary table.
3. Capture and hatch always profile at the zone's tier.
4. Planner: wild and trainer Pokémon rated everywhere, PvP still returns nothing.
5. Scouter on any wild or trainer Pokémon in the current battle; remove the "inactive in this battle" badge.
6. Guide, wiki handoff, README and the zone document updated; live check of the wild-ID and trainer-ID assumptions on a real server.
