# Status-damage affixes: design (2026-10-05; built the same day, lib commit 1f3c86c)

Status: **built, verified only in the simulator harness, never seen in a live battle.** Scope chosen by the owner: burn and poison potency, plus one custom ("bleed-style")
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

## Decisions (formerly open questions; as built in `ascension-fx.js`)

1. Rending trigger: **30% per damaging move** that deals damage to an opponent, one roll per move use and target however many hits land.
2. Rending damage and length: **1/12 of max HP per stack per turn, up to 3 stacks, 3 turns**, every new application refreshes the turns
   and adds a stack (this replaced the "refresh, don't stack" recommendation). Damage goes through the engine's `Damage` event, so
   **Magic Guard blocks it**.
3. Immunity: no type immunity. Only Magic Guard (and other native damage rules via the `Damage` event) stops a tick; a hit that deals no
   damage never rolls, which is how a Substitute is expected to absorb it (not separately tested).
4. Smoldering and Venomous boost **only their own status**. Rending is boosted by its own rolled percent.
5. Caps: the `residual` channel has a cap of **100** (`caps.res` in the payload), shared by Smoldering, Venomous, Rending and the Unique
   Ashen Heart.
6. Rank bands: same five-band shape as other affixes, numbers still **provisional**; they need a balance pass after the stacked-team live test.

Related Uniques that share the bleed: Rupture (physical moves always bleed, stacks weigh 100/80/60%). The catalog version was not bumped,
so the three affixes roll for new captures immediately and existing profiles stay valid.

Still open: live confirmation that the Cobblemon client renders the plain `-damage` line plus "<name> is bleeding!" without errors.
