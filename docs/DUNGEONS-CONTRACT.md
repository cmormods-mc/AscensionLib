# AscensionLib contract for ascensionDungeons

Status: built 2026-10-16, all tests pass, not seen in a live run. Written against `L:\ascensionDungeons` (timed runs in per-run Fantasy levels,
up to four players, wild Pokemon placed by the dungeon, arena raids through CobbleRaids). Every number is provisional pending balance approval.

AscensionLib stays optional for the dungeon: both classes below use only `java.*` types, so the dungeon reaches them by reflection (the way it
reaches CobbleRaids). Call them on the server thread.

## What a dungeon gets

1. **Profile zones with a tier.** Open its run level as a zone while the run exists. Inside, wild Pokemon fight with an Ascension rating at the zone's
   tier, and a Pokemon a player captures there gets a profile rated at the same tier (the Scouter preview equals the capture). Anywhere else wild fights
   stay native, as today. Tier 1 is the ordinary rating, unchanged; a deeper tier draws rarity as the best of 1 to 4 weighted draws (`1 + min(tier-1, 3)`),
   so tier 4 and above are equally generous in rarity, and each Pokemon's rating is fixed (HMAC of its ID, the tier and the server secret).
2. **Run rewards.** One call pays a finished run from the library's own bands: dust for depth and arena wins, Facets, Unique Fragments from a boss,
   +3 attunement for each paid player's party, and a chance at an Ascension Sigil. Exactly once per (run, player): retrying is the intended recovery.
3. **Scouting a roaming wild Pokemon (built 2026-10-16, no dungeon work needed).** A player runs `/ascend scout look` while looking at a wild Pokemon
   (within 32 blocks) in an open zone: one Scouter is spent and the inspect page shows its rarity and modifiers at the zone's tier, the rating it will have in a
   battle and when caught. Outside a zone it says there is nothing to scout. Each player remembers the last six they scouted. It assumes a wild Pokemon keeps its
   UUID through capture, which is still to be confirmed in a live world.
4. **Not provided yet:** per-district tiers inside one run, and dungeon-specific affixes. The dungeon's raid bosses are declared the way CobbleRaids does it (`AscensionEncounters`, see its Javadoc).

## `com.ascensionlib.AscensionZones`

| Method | Meaning |
|---|---|
| `static int contractVersion()` | `1`. Bumped only when a method is removed or changes meaning. |
| `static boolean openZone(String dimensionId, int tier)` | Make the dimension a profile zone of `tier` (1..10), or change its tier. `false` (nothing changes) for an invalid id or tier. |
| `static void closeZone(String dimensionId)` | Close it. Safe twice, for an unknown id, or `null`. |
| `static int zoneTier(String dimensionId)` | The tier, or `0` when not an open zone. |

Open the zone when the run's level is created and close it when the level is deleted. The registry is in memory only: nothing survives a restart, and a run
level does not either, so there is nothing to restore. A Pokemon captured after its level is gone is simply not in a zone (it gets no profile).

## `com.ascensionlib.AscensionRewards.settleDungeonRun`

```java
static Map<UUID, String> settleDungeonRun(UUID runId, String outcome, int tier, int arenaWins, boolean bossDefeated, Collection<UUID> players)
```

- `runId`: the run's own ID, never reused. `outcome`: `VICTORY`, `DEFEAT` or `ABORTED`; only `VICTORY` pays (the dungeon decides what counts as a
  victory: for example one arena won, or the boss beaten). `tier`: the same tier passed to `openZone`. `arenaWins` 0..16. `bossDefeated`.
- Returns, per player, `GRANTED`, `ALREADY_GRANTED`, `NOT_PAID`, `CONFLICT`, `REFUSED` or `DISABLED` (same meanings as `settle`). `DISABLED` means no
  running world: nothing was written, call again later. Throws `IllegalArgumentException` for a malformed call (unknown outcome, tier or arena count out of range).
- Pays per player: dust `10 x tier + 14 x arenaWins x (1 + 0.1 x (tier-1)) + 30 x tier if the boss fell`; one Facet for the boss plus 35 percent per arena win;
  one Unique Fragment (50 percent a second) for a boss. **Never Ascension Cores**: the Tower stays the only core source, as for Trials.
- Sigil: `2 + (tier-1)` percent, at most 10, rolled per player, seeded by (run, player); reward kind `dungeon_sigil`.
- Attunement: +3 for each paid player's party, once per run (`dungeon:<runId>`).

## Calling it by reflection (sketch for the dungeon)

```java
Class<?> zones = Class.forName("com.ascensionlib.AscensionZones");
int version = (int) zones.getMethod("contractVersion").invoke(null);          // refuse if it is not 1
Method open = zones.getMethod("openZone", String.class, int.class);
Method close = zones.getMethod("closeZone", String.class);
open.invoke(null, run.levelId().toString(), tier);                             // when the level is created
close.invoke(null, run.levelId().toString());                                  // when it is deleted (also on failure paths)

Class<?> rewards = Class.forName("com.ascensionlib.AscensionRewards");
Method settle = rewards.getMethod("settleDungeonRun", UUID.class, String.class, int.class, int.class, boolean.class, Collection.class);
Map<?, ?> statuses = (Map<?, ?>) settle.invoke(null, run.id(), "VICTORY", tier, arenaWins, bossDefeated, paidMembers);
```

Guard every call (`ClassNotFoundException`, `NoSuchMethodException`, `LinkageError`): the library may be absent or older. Keep a pending record for any
player whose status is `DISABLED` or whose call threw, and retry, the way CobbleTowers keeps unconfirmed settlements.

## Rules that must hold

- Run IDs are never reused. A retried `settleDungeonRun` with the same arguments pays nobody twice; with different amounts the first stands (`CONFLICT`).
- `ContractSignaturesTest` pins these signatures: change a method only by adding a new one.
- `ProfileZones.tierOf` is read from the dimension of the **wild actor's entity** in a battle and of the **player** at capture, so the dungeon should
  keep players and wild Pokemon in the run level while a battle or capture is in progress (they already are).

## Where it lives

`AscensionZones`, `AscensionRewards.settleDungeonRun`, `battle/ProfileZones` (tiers), `RankedProgression.rateWild/createWild` with a tier,
`DungeonRewardBands`, `SigilDrops.rollDungeon`. Tests: `WildRatingTest` (tier invariants), `DungeonRewardBandsTest`, `SigilDropsTest`, `ProfileZonesTest`,
`ContractSignaturesTest`.
