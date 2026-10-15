# Request for the auction mod: a listing fee on AscensionLib vouchers

AscensionLib's wallet currencies can be taken out as ordinary stackable items (vouchers: `custom_data` `{ascensionlib:{material, batch}}`) and redeemed back with
`/ascend wallet deposit`. AscensionLib already takes a 3 percent fee, rounded up, on every withdrawal. Trading volume is the other half of the sink, and only the auction mod can
charge it. This note is the request to whoever owns that mod (nothing to build in AscensionLib).

## What to ask for
1. **A listing fee**: a percentage (suggest 5 percent of the asking price, minimum 1, taken in the sale currency when the listing is created and not refunded) or a tax on the sale.
   A percentage scales with the economy where a flat fee goes stale.
2. **Keep the item intact**: a listed voucher must come back to the buyer exactly as it was, with its custom data. Stripping or rewriting the components makes the stack worthless
   (redemption is capped by the batch's outstanding units and an unknown batch credits nothing).
3. **No splitting side effects**: stacks of the same batch may merge or split; the batch id is the identity, the count is the amount.

## What not to ask for
- Marks (`tower_mark`) never exist as vouchers, so they cannot be listed.
- Do not special-case materials by price: how much a Core is worth is for the market to say. Watch it instead: a Core trading below the value of a Tower hour means the faucet is too
  generous; a Core that never trades means the drop or the fee is too tight.
