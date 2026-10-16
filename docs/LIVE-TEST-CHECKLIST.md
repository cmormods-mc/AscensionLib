# Live test checklist

For one guided session on a real server and client before a release, in order. Everything marked **seen** has already been run in a real client by the owner (2026-10-15); it stays on the list as a quick regression pass. The one item never run live is the floor-10 clear, and it comes first.

Set up: the jar and its resource pack on the server (`docs/INSTALL.md`), a client with WebGUI and Rinku, an op account, `/ascend admin grant <you> <material> <amount>` for materials, and a world backup.

## 1. The floor-10 champion clear (not yet run live)

With CobbleTowers installed, run a tower to its floor-10 champion with one account, then with two.

- [ ] Floor 5: the mini chest pays (200 dust, 1 facet, 1 fragment, 1 scouter, 1 Mark, 5% Core) and your party's **fighting** Pokémon gain 3 attunement (a benched one does not).
- [ ] Floor 10: the **completion reveal page** opens: tier banner, the pile counting up, the crown rolling one at a time, the tier meter, Share; the wallet then holds the pile and any crown materials.
- [ ] The reveal **stays up** until you press Done or Esc: the Tower's own intermission screen does not replace it, and appears after you close it.
- [ ] A crown **armor piece** and/or **CobbleCards pack** (when rolled) arrive in the inventory once, from the tower's own table (check the player's tower set).
- [ ] A Core, Catalyst, Prism or Mythic tier is announced to the party in chat; a plain Bronze is not.
- [ ] Both players are paid; each sees their own chest.
- [ ] **Retry safety:** repeat the clear, and, in a separate run, stop the server within a second of the champion's death and start it again: no second chest and no second armor piece or pack (look in `<world>/cobbleascend` logs for `ALREADY_GRANTED`, and count the items).
- [ ] The score: take 0, then several challenge cards; a clean run reaches a higher tier than the same run with a faint (the meter shows why).
- [ ] After 20 chests in a UTC day (use `/ascend admin completion silver pay` to reach it quickly) the next chest pays 25% dust and says so in chat; Cores and Marks are full.

## 2. Respec items (seen)

- [ ] `/ascend admin grant <you> rank_respec 1` and `unique_respec 1`.
- [ ] **Hourglass:** Craft, Respec tab: the card lists every slot with its floor; Review, Confirm: all ranks I, credits back, nothing refunded. A Pokémon that has **Overreached** is refused with the sealed reason and keeps the item. A fresh rank I Pokémon is refused (nothing to undo).
- [ ] **Buy for 90 Marks** (grant 100 `tower_mark`): 90 Marks become one Hourglass.
- [ ] **Prism:** Unique tab, pick another Unique, Use Unbinding Prism: swapped with no Catalyst or Mark spent. Blocked on a Transcendent and with no Unique.

## 3. Wallet, vouchers and the Dex (seen)

- [ ] AscensionDex: right-click opens the party; Inspect, Upgrade; the Wallet tab (fee preview, Take, Deposit); the Guide tab.
- [ ] A voucher shows the paper tag and red seal in its icon (with the pack), loot-form items do not; tooltips open with the rarity line.
- [ ] A voucher in an auction listing keeps its data; a stack that is copied redeems only once.
- [ ] Tower Marks show "Bound" and cannot be taken out.

## 4. Pages and the overlay (seen)

- [ ] Craft (every tab), Fusion, Inspect (Sigil button on a Pokémon with no profile), Wallet, Party, Guide, completion reveal.
- [ ] `- 100% +` in each header and `/ascend ui 130` change the size and it is still there after relogging.
- [ ] A 1080p and a 4K window both fit; the overlay sits cleanly over Cobblemon's battle screen.
- [ ] A bleed badge appears in a real battle with Rending or Rupture, counts down and clears.

## 5. Operations

- [ ] Start log: `Companions found:` names what is installed; no warning for Cobblemon 1.8.1 or 1.8.2.
- [ ] `config/ascensionlib.json` appears with every default; change `hourglassMarksPrice` and `dailyBudget`, run `/ascend admin reload`: it names what changed and the guide shows the new numbers.
- [ ] `/ascend admin backup` writes `<world>/cobbleascend/backups/progression-<time>.db`; restart: another snapshot is made if the newest is a day old.
- [ ] Upgrade from the previous release's world: a `progression.db.before-schema4-from3-<time>.bak` is made, then progression works.
- [ ] Pull the WebGUI mod from a client: the player is told, and `/ascend wallet`, `/ascend inspect` still print text.

## 6. Profiles on every wild Pokémon (new)

These rely on two things never yet checked on a real server. Do them first; if either fails, stop and tell the developer.

- [ ] **A wild Pokémon keeps its ID through capture.** Look at a wild Pokémon with a Scouter (`/ascend scout` or the Dex), note its rarity and modifiers, then catch it: the profile it gets is the same. Repeat with three.
- [ ] **A trainer's Pokémon rates the same all through one battle:** in a trainer battle, the modifier effects on the trainer's Pokémon (with CobbleRaids installed) do not change when it is sent out again or the battle runs long.
- [ ] Catching any wild Pokémon in an ordinary dimension gives a profile, tier 1 (mostly Common and Uncommon). Hatching an egg gives one too.
- [ ] A Pokémon you already owned still says "No profile" until you use a Sigil on it.
- [ ] A battle against a player (PvP) has no modifier effects on either side; a wild or trainer battle does.
- [ ] `config/ascensionlib.json`: set `zones.dimensions` to `{"minecraft:the_nether": 6}`, run `/ascend admin reload`, and catch several Pokémon in the Nether: clearly rarer than in the overworld. Set it to 0: no profile and no effects there. Remove it again.
- [ ] A dungeon (when ascensionDungeons is wired) opens its dimension at its tier and the captures and wild fights there are rated at it.
- [ ] Battle speed: a trainer battle and a wild battle start without a noticeable pause.

## 7. Both Cobblemon versions

Repeat sections 1 to 4 and 6 on **1.8.1** and on **1.8.2**. Record the result, the date and the versions in `docs/BUILD-STATUS.md`.
