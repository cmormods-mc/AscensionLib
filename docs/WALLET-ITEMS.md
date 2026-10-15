# The wallet as real items

Status: built 2026-10-16, all tests pass, not tried in a live world or with a real resource pack in a client. Numbers and base items are provisional.

Owner decision: the seven wallet currencies are also real, tradable items (an auction house will trade them), shown with art from a server resource pack. The wallet in
the store stays the source of truth. Three levels, all built:

1. **Look.** Each material is a vanilla item with a custom model number a server resource pack maps to art. The pages show the same icons.
2. **Loot form.** Loot tables hand out the currency item; a player who carries one has it absorbed into their wallet within a second. It is never stored or traded.
3. **Vouchers.** `/ascend wallet withdraw <material> <amount>` turns wallet balance into tradable items; `/ascend wallet deposit [material]` redeems them.

## The items (`design/currency-items.json`)

| Material | Base item | Model |
|---|---|---|
| resonance_dust | `minecraft:glowstone_dust` | 8101 |
| facet | `minecraft:amethyst_shard` | 8102 |
| ascension_core | `minecraft:heart_of_the_sea` | 8103 |
| unique_fragment | `minecraft:echo_shard` | 8104 |
| unique_catalyst | `minecraft:nether_star` | 8105 |
| scouter | `minecraft:prismarine_crystals` | 8106 |
| ascension_sigil | `minecraft:nautilus_shell` | 8107 |

Each is a vanilla item plus `minecraft:custom_data {ascensionlib:{material, batch?}}`, a `custom_model_data`, a coloured name and lore. No client mod and no registered item.
Base items are inert (no use action), stack to 64 and have a plain generated model a pack can override, so only tagged items look different. (A clock or compass would break:
their vanilla models carry animation overrides.) A tag on the wrong base item, an unknown material or a malformed batch id is not currency.

## The resource pack

`python -I tools/build_resourcepack.py` regenerates, from the design file and the art tables in the script: `resourcepack/` (pack format 34 for 1.21.1: textures, item models, the
base items' `custom_model_data` overrides), the icons the pages use (`web/ascensionlib/icons/`), the loot tables, and `build/AscensionLib-resourcepack.zip` with its sha1. Host the
zip anywhere with a direct URL and put in `server.properties`:

```
resource-pack=https://example.com/AscensionLib-resourcepack.zip
resource-pack-sha1=<the printed sha1>
require-resource-pack=true
```

Clients then download it automatically; nothing is installed. **A server sends only one pack**, so if the server already has one, merge the contents of `resourcepack/` into it
(the base-item model files in `assets/minecraft/models/item/` are the only files that can collide: keep every `overrides` entry from both). The icon art is placeholder pixel art,
drawn in the script; replace the PNGs in `resourcepack/assets/ascensionlib/textures/item/` (and re-copy to the icons folder) when real art exists.

## Loot form

Other mods and datapacks reference `ascensionlib:currency/<material>` as a loot table entry (`{"type":"minecraft:loot_table","value":"ascensionlib:currency/resonance_dust"}`) and add
`minecraft:set_count`. ascensionDungeons' chest tables can use this directly. The server scans each online player's inventory once a second; loot-form stacks are credited to the
wallet in one store transaction (`loot_pickup`), then removed, and the player sees `+12 Resonance Dust` on the action bar. If the credit fails the items stay. A crash between the
credit and the save of the emptied inventory could credit a stack twice after the restart, which only ever affects loot-form value, never a tradable item.

## Vouchers (the tradable form)

- **Withdraw:** needs one free main-inventory slot per 64 units (at most 2304 per withdrawal). The store debits the wallet and opens a **batch** in one transaction
  (`ProgressionStore.mintVoucher`); the server hands over the stacks and marks the batch delivered. A batch whose hand-over was interrupted is delivered again at the next login.
- **A voucher** is the same item plus a batch id, so two batches never stack together. It is a **bearer instrument**: whoever holds it can redeem it, which is what makes it tradable.
- **Deposit:** each stack redeems against its batch (`redeemVoucher`): it credits at most what the batch still has **outstanding**, and the stack is removed. Units beyond that are
  duplicates or already redeemed and are discarded, and the player is told. A voucher the store never minted (a forged or foreign item) is left alone and reported.
- **Dupes cannot inflate the currency.** The wallet plus every batch's outstanding units is exactly what was minted: a duplicated stack can only race the original for what is left.
  Tested: a duplicated 100-stack after a 60 redemption credits 40 and nothing more, and the total in existence never changes.
- **Loss:** vouchers that die in lava or despawn stay outstanding forever (supply the owner loses), the same as any lost item.
- **Not covered:** items minted by operators (`/give`) or by another mod's loot tables are as good as their source; the store only protects what it minted.

## Wallet page and commands

`/ascend wallet gui` (or the Wallet button on the craft page) opens `wallet.html`: every material with its icon and balance, a withdraw box, a Deposit button for the vouchers you
carry, and Deposit all. Commands: `/ascend wallet`, `/ascend wallet withdraw <material> <amount>`, `/ascend wallet deposit [material]`. The craft page's wallet column now shows the
item icons.

## Store

Schema 3 adds a `vouchers` table (`batch_id`, `player_id`, `material_id`, `minted`, `outstanding`, `delivered`, `created_at`); schema 1 and 2 stores upgrade in place and keep all
rows. Kinds `MINT_VOUCHER` and `REDEEM_VOUCHER`. Forward-only: an older build refuses a schema 3 store.

## Auction house notes

Vouchers are ordinary vanilla items with components, so any auction mod can list them, and they keep their batch through trades. If the auction mod strips or rewrites components, a
voucher stops being redeemable (it becomes an unrecognised plain item): test one listing and one purchase before relying on it. Selling loot-form items is not possible by design
(they vanish into the wallet when carried); withdraw first.
