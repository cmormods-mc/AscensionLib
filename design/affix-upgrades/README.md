# Affix upgrade UI prototype

**Design approved by the owner on October 3, 2026. Preserve for later native implementation.** The approved reference is this clean-pixel Upgrade/Reforge interface, including selection, roll-range comparison, explicit confirmation, result feedback and pending-credit handling. Approval of presentation does not finalize the provisional balance values or claim live game integration.

Interactive companion to the approved clean-pixel capture reveal. Open `preview.html`; editable source is `affix-upgrades.fragment.html`.

The screen retains the reveal's pale inventory panels, hard pixel borders, Pixelify Sans preview font and commit-pinned Cobblemon Cards Growlithe sprite. Mythic is a small crimson/silver rarity accent. Native Minecraft integration should use the game's font and texture renderer.

## Implemented preview interactions

- Select any occupied affix slot and inspect its description, current value, rank, and next roll band.
- Review, cancel or confirm a one-credit upgrade. A confirmed upgrade advances the selected slot exactly one rank, rolls within that next band and consumes one pending credit.
- Allocate multiple credits separately; show an unavailable action when none remain or when the selected slot is rank V.
- Reforge mode shows eligible replacement examples and their ranges at the selected slot's existing rank. Confirmation costs 10 Dust and 1 Facet in this preview and preserves the slot rank and pending upgrades.
- Results update the list and selected detail; repeated operations require fresh confirmation. No silent spending on selection or mode changes.
- Decide later and reopen without losing the current session's upgrades or sample balances.
- Optional short synthesized sound on successful operations; reduced-motion system preference suppresses the result transition.
- Preview scenarios cover two available upgrades, no remaining credits, and one rank-V affix. Changing scenarios resets the sample session.

## Scope and balance

This is a browser UI prototype, not a live Fabric crafting implementation. All sample bands, replacement pools, prices and wallet balances are provisional. Rolls run locally only to demonstrate interaction. Real crafting must use server-authored previews and committed results with operation IDs, ownership/revision checks, eligibility/family rules and atomic currency/profile persistence.

Fire Focus's five bands match the design example. Other bands and additional reforge definitions illustrate presentation and do not amend the production catalog. The display promises a stronger raw value on an upgrade; final combat impact still depends on conditions and approved shared caps.

The max-rank example is level 68: six earned milestone credits, four invested in Fire Focus to reach V, two pending. The level-28 exhausted example invests two credits across two slots. These states preserve the milestone accounting model.

Preview preferences persist; simulated crafting changes are session-local. No actual Pokémon, game wallet or server profile is modified. Sprite provenance and license are retained in `../reveal/assets/` and documented in `../reveal/README.md`.

## Verification

`work/check-affix-ui.cjs` passed upgrade bounds and credit consumption, cancel/Escape without spending, rank-preserving reforge, depleted materials, defer/reopen, no-credit/max-rank cases, script errors and label/width geometry at 780, 600, 360 and 320 px. Sprite loading was verified with network access. Screenshots: `work/affix-ui-desktop.png` and `work/affix-ui-mobile.png`.
