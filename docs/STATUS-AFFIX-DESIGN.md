# Status-damage affixes: design (2026-10-05, draft; code waits for the owner's batch test)

Status: **design only, nothing built.** Scope chosen by the owner: burn and poison potency, plus one custom ("bleed-style")
damage-over-time effect the library applies itself. Spec references: `CobbleAscension_TECHNICAL-DESIGN-SPECIFICATION.md` 8.1, 8.2, 9.1 (Ashen Heart).

## Where this plugs in

`ascension-fx.js` already wraps the simulator at battle start: `actions.modifyDamage` (outgoing and incoming percent, three channels
capped once) and each holder's `heal`. Every condition is decided inside Showdown from native state; Java only sends
`{v, caps, mons: {uuid: [{i, p, t}]}}` built by `AscensionBattles.fieldsFor`. Status affixes follow the same rule: Java sends affix ids and
percents, the simulator does the attribution and the maths. A new fourth channel, `residual`, holds the boost multipliers and is capped once
like the other three (cap provisional, default 100).

## The three effects

| Affix | Slot | Behaviour |
|---|---|---|
| Smoldering | prefix | Burn damage on a target the holder burned is boosted by the rolled percent (residual channel). No second burn is applied. |
| Venomous | prefix | Same for regular poison (`psn`); **toxic (`tox`) is excluded** in the first version, per spec. |
| Rending (the custom DoT) | prefix | Open: see questions. A damaging move of the holder can leave a library-ticked bleed on the target. |

Ashen Heart (the Unique that strengthens holder-attributed burns and lowers direct damage) uses the **same residual channel and cap**, so
Smoldering and Ashen Heart multiply once and clamp once; this is the spec's "declared shared channel", not accidental stacking. Ashen Heart
stays out until the burn attribution passes its tests; it is not part of this increment.

## Attribution (the hard part, spec 8.2)

A battle-local ledger: `target Pokemon -> {kind: 'brn'|'psn', source Pokemon, appliedOnTurn}`.

- Written only when the simulator confirms the application: wrap `Pokemon.setStatus` on **every** Pokemon of both sides (not only holders), and
  record when the call returns true and the new status is `brn` or `psn`. A failed or redundant application never overwrites an entry.
- Read when the residual damage lands: wrap `Pokemon.damage(d, source, effect)`; when `effect.id` is `brn` or `psn` and the ledger names a
  source that holds a matching affix and is still active and not fainted, scale `d` once with the residual channel.
- Retired when the target's status clears, changes or is replaced, when the source faints or is switched out (bonus pauses; the native status is
  untouched), or at battle end. A status with no ledger entry (inherited, Toxic Orb, reflected, ally-inflicted) does native damage only.
- Never creates damage the native event prevented or converted to healing (Poison Heal, Magic Guard): the wrapper only scales a damage the
  engine was already going to deal.

**Spike required before building:** confirm in the bundled simulator that (1) burn and poison residual go through `Pokemon.damage` with
`effect.id` `brn`/`psn`, (2) `setStatus` is the single entry for applying them (move secondary, ability, item), (3) the doubled rounding
(`battle.modify`) matches the engine's own for a 1/16 burn. The 21-test harness gets one test per row of the spec's edge table (source
switch/faint/cure, toxic, immunity, Poison Heal, reflected status).

## Rending: the custom DoT (needs owner answers)

Showdown has no native bleed, and conditions are data-driven, so a custom volatile needs either (a) an injected condition into the loaded
dex at battle start, or (b) a library ledger plus a hook on the engine's residual step that deals the damage itself. Both are a spike. The
client must also render it: an unknown `[from]` effect in a `-damage` line is misread by the Cobblemon client (a lesson already recorded in
`triumphant`), so the tick uses a plain `-damage` line plus a `-message` text.

## Open questions

1. Rending trigger: a chance on each damaging hit, or the holder's first hit of the battle, or only on a critical hit?
2. Rending damage and length: a fixed percent of the target's max HP per turn for N turns (recommended: 1/16 like burn, 3 turns, refreshing
   instead of stacking), and does it ignore Magic Guard?
3. Who is immune: Rock/Steel? Pokemon that already have a major status? (Recommended: no type immunity, only Magic Guard and a Substitute block.)
4. Do Smoldering and Venomous also boost Rending (one shared "residual" boost), or only their own status? (Recommended: only their own.)
5. Caps: residual channel cap 100 like outgoing damage, or lower (it multiplies a small base, so a high cap is safe)?
6. Rank bands: same five-band shape as other affixes; the numbers need a balance pass once the effects run.
