'use strict';
// AscensionLib's Showdown extension (docs/BATTLE-ADAPTER-DESIGN.md).
//
// Installed by CobbleRaids' extension loader as showdown/ext-ascensionlib-fx.js and require()d by raid-patch.js, which is
// why the relative require('./sim/battle') below resolves to the simulator. This file never edits any Showdown file.
//
// WHAT IT DOES. A battle can carry `ascensionFx` on the format object of its >start payload: the ranked affixes of every
// Pokemon that holds any, keyed by the uuid Cobblemon packs into the team (the simulator keeps it as pokemon.uuid):
//
//   {"v":1,"caps":{"out":100,"inc":50,"heal":50},"mons":{"<uuid>":[{"i":"type_focus","p":8,"t":"Water"}, ...]}}
//
// "i" is the affix id, "p" the rolled percent, "t" a Showdown type for the typed affixes. Three channels: outgoing damage,
// incoming damage reduction and healing. Within a channel every active effect is its own multiplier and the total is
// capped once. All conditions are decided HERE, inside the simulator, from native state; Java only says what a Pokemon holds.
//
// WHY IT IS STABLE (the same properties as CobbleTowers' tower-fx.js, which proved them).
//   * It does nothing unless asked: it wraps Battle.prototype.start and returns at once unless format.ascensionFx is a
//     version-1 object. A battle without it is byte-for-byte unchanged.
//   * It is declarative and bounded: no code, no eval; at most MAX_MONS Pokemon with MAX_EFFECTS each, percentages 0..100,
//     unknown ids ignored, types checked against the battle's own dex.
//   * Damage wraps modifyDamage on THIS battle's own `actions` object, so no other battle sees it. The wrapper runs after the
//     engine's own maths, so native rounding, immunity and the minimum of 1 are kept, and it fails open to the engine's number.
//   * Healing wraps heal() on each affected Pokemon (a heal-flag move such as Recover calls pokemon.heal directly, so
//     battle.heal alone would miss it). Only the holder's own move counts: items, abilities and residual healing do not.
//   * Every hook is wrapped in try/catch and falls back to the native value.

const MAX_MONS = 32;
const MAX_EFFECTS = 8;
const HP_HIGH = 0.8;
const HP_LOW = 0.35;
const DEFAULT_CAPS = {out: 100, inc: 50, heal: 50, res: 100};

const hpFraction = mon => (mon.maxhp > 0 ? mon.hp / mon.maxhp : 1);

// Outgoing: (effect, user, target, move, hit, ctx) -> does it apply to this hit? `hit.typeMod` is native effectiveness.
const OUTGOING = {
  type_focus: (fx, user, target, move) => move.type === fx.t,
  type_mastery: (fx, user, target, move) => move.type === fx.t,
  physical_force: (fx, user, target, move) => move.category === 'Physical',
  special_force: (fx, user, target, move) => move.category === 'Special',
  healthy_force: (fx, user) => hpFraction(user) >= HP_HIGH,
  last_stand: (fx, user) => hpFraction(user) <= HP_LOW,
  defiant_force: (fx, user) => !!user.status,
  overwhelming_force: () => true,
  kindred_force: (fx, user, target, move) => user.hasType(move.type),
  executioner: (fx, user, target) => hpFraction(target) <= 0.5,
  opening_strike: (fx, user, target, move, hit, ctx) => ctx.firstMove,
  super_effective_force: (fx, user, target, move, hit) => hit.typeMod > 0,
};

// Incoming: (effect, holder, attacker, move, hit, state) -> does it apply to this hit?
const INCOMING = {
  type_ward: (fx, holder, attacker, move) => move.type === fx.t,
  type_bulwark: (fx, holder, attacker, move) => move.type === fx.t,
  healthy_guard: (fx, holder) => hpFraction(holder) >= HP_HIGH,
  steadfast_guard: (fx, holder) => hpFraction(holder) <= HP_LOW,
  status_guard: (fx, holder) => !!holder.status,
  opening_guard: (fx, holder, attacker, move, hit, state) => !state.hitTaken,
  iron_resolve: () => true,
  resilient_hide: (fx, holder, attacker, move, hit) => hit.typeMod > 0,
  physical_bulwark: (fx, holder, attacker, move) => move.category === 'Physical',
  special_bulwark: (fx, holder, attacker, move) => move.category === 'Special',
};

const HEALING = {restorative: true, triumphant: true};

// Status damage (docs/STATUS-AFFIX-DESIGN.md). The potency affixes boost the native residual of the status the holder inflicted;
// Showdown already records the inflictor in pokemon.statusState.source, so no ledger is needed. Rending is the library's own
// bleed: a chance on each direct damaging hit, ticked here once a turn.
const POTENCY = {smoldering: 'brn', venomous: 'psn'};
const RENDING = 'rending';
const RENDING_CHANCE = 30;      // percent per damaging move that deals damage
const BLEED_DIVISOR = 12;       // each stack bleeds 1/12 of max HP a turn
const BLEED_STACKS = 3;
const BLEED_TURNS = 3;          // refreshed by every new application

const HANDLED = new Set([...Object.keys(OUTGOING), ...Object.keys(INCOMING), ...Object.keys(HEALING),
  ...Object.keys(POTENCY), RENDING]);

function clamp(value, low, high, fallback) {
  const n = Number(value);
  if (!Number.isFinite(n)) return fallback;
  return Math.max(low, Math.min(high, Math.trunc(n)));
}

/** A line in the battle log the player can read; never throws. */
function note(battle, text) {
  try {
    battle.add('-message', String(text).slice(0, 200));
  } catch (err) {
    /* a log line is never worth losing a battle over */
  }
}

/** 1 + the compounded bonuses, never above 1 + cap%. */
function grow(percents, cap) {
  let value = 1;
  for (const p of percents) value *= 1 + p / 100;
  return Math.min(1 + cap / 100, value);
}

/** What is left after the compounded reductions, never below 1 - cap%. */
function shrink(percents, cap) {
  let value = 1;
  for (const p of percents) value *= 1 - p / 100;
  return Math.max(1 - cap / 100, value);
}

/** The effects of a payload, validated and bounded: uuid -> [{i, p, t}]. */
function parse(battle, payload) {
  const caps = {
    out: clamp(payload.caps && payload.caps.out, 0, 100, DEFAULT_CAPS.out),
    inc: clamp(payload.caps && payload.caps.inc, 0, 90, DEFAULT_CAPS.inc),
    heal: clamp(payload.caps && payload.caps.heal, 0, 100, DEFAULT_CAPS.heal),
    res: clamp(payload.caps && payload.caps.res, 0, 100, DEFAULT_CAPS.res),
  };
  const byUuid = new Map();
  let seen = 0;
  for (const [uuid, list] of Object.entries(payload.mons || {})) {
    if (++seen > MAX_MONS) break;
    if (!Array.isArray(list)) continue;
    const effects = [];
    for (const raw of list.slice(0, MAX_EFFECTS)) {
      if (!raw || typeof raw.i !== 'string' || !HANDLED.has(raw.i)) continue;
      const p = clamp(raw.p, 0, 100, 0);
      if (p <= 0) continue;
      let t;
      if (raw.t !== undefined) {
        const type = typeof raw.t === 'string' ? battle.dex.types.get(raw.t) : null;
        if (!type || !type.exists) continue;
        t = type.name;
      }
      effects.push({i: raw.i, p, t});
    }
    if (effects.length) byUuid.set(uuid, effects);
  }
  return {caps, byUuid};
}

function apply(battle, payload) {
  const {caps, byUuid} = parse(battle, payload);
  if (!byUuid.size) return 0;
  const state = new Map();
  const stateOf = mon => {
    if (!state.has(mon)) state.set(mon, {hitTaken: false});
    return state.get(mon);
  };
  const of = (mon, table) => (byUuid.get(mon.uuid) || []).filter(fx => Object.prototype.hasOwnProperty.call(table, fx.i));

  // ---- damage dealt and taken ----
  const actions = battle.actions;
  const originalDamage = actions && actions.modifyDamage;
  if (typeof originalDamage === 'function') {
    actions.modifyDamage = function (baseDamage, pokemon, target, move) {
      const damage = originalDamage.apply(this, arguments);
      try {
        if (typeof damage !== 'number' || !(damage > 0) || !pokemon || !target || !move) return damage;
        const hit = target.getMoveHitData(move);
        const ctx = {firstMove: pokemon.activeMoveActions <= 1};
        const out = of(pokemon, OUTGOING).filter(fx => OUTGOING[fx.i](fx, pokemon, target, move, hit, ctx)).map(fx => fx.p);
        const holder = stateOf(target);
        const inc = of(target, INCOMING).filter(fx => INCOMING[fx.i](fx, target, pokemon, move, hit, holder)).map(fx => fx.p);
        if (byUuid.has(target.uuid)) holder.hitTaken = true;   // opening_guard: only the first damaging hit of the battle
        let scaled = damage;
        if (out.length) scaled = battle.modify(scaled, Math.round(grow(out, caps.out) * 1000), 1000);
        if (inc.length) scaled = Math.max(1, battle.modify(scaled, Math.round(shrink(inc, caps.inc) * 1000), 1000));
        return scaled;
      } catch (err) {
        return damage;
      }
    };
  } else {
    note(battle, 'Ascension damage effects are not available in this simulator.');
  }

  // ---- healing from the holder's own move ----
  const rawHeal = Object.getPrototypeOf(battle.sides.find(Boolean).pokemon[0]).heal;
  for (const side of battle.sides) {
    if (!side) continue;
    for (const holder of side.pokemon) {
      const heals = of(holder, {restorative: 1}).map(fx => fx.p);
      if (!heals.length) continue;
      const inner = holder.heal;
      holder.heal = function (d, source, effect) {
        let amount = d;
        try {
          // A heal-flag move passes no effect; drain passes the 'drain' condition; both happen inside the holder's own action.
          const ownMove = battle.activePokemon === this && battle.activeMove &&
            ((effect && effect.id === 'drain') || !effect || effect.effectType === 'Move');
          if (ownMove) amount = battle.modify(d, Math.round(grow(heals, caps.heal) * 1000), 1000);
        } catch (err) {
          amount = d;
        }
        return inner.call(this, amount, source, effect);
      };
    }
  }

  // ---- triumphant: a direct move of the holder fainted the target ----
  const originalFaint = battle.faintMessages;
  if (typeof originalFaint === 'function' && [...byUuid.values()].some(list => list.some(fx => fx.i === 'triumphant'))) {
    battle.faintMessages = function () {
      const killers = [];
      try {
        for (const entry of this.faintQueue) {
          if (entry && entry.source && entry.effect && entry.effect.effectType === 'Move' && entry.source !== entry.target) {
            const heals = of(entry.source, {triumphant: 1}).map(fx => fx.p);
            if (heals.length) killers.push([entry.source, heals]);
          }
        }
      } catch (err) { /* fail open */ }
      const result = originalFaint.apply(this, arguments);
      for (const [holder, heals] of killers) {
        try {
          if (holder.hp > 0 && !holder.fainted) {
            const percent = Math.min(caps.heal, heals.reduce((a, b) => a + b, 0));
            // Healed directly with a plain -heal line: a `[from]` the client does not know would be misread.
            if (rawHeal.call(holder, Math.floor(holder.maxhp * percent / 100))) battle.add('-heal', holder, holder.getHealth);
          }
        } catch (err) { /* fail open */ }
      }
      return result;
    };
  }
  applyStatusDamage(battle, byUuid, caps, of);
  return byUuid.size;
}

/** Smoldering / Venomous (potency of the holder's own burn / poison) and Rending (the library's bleed). */
function applyStatusDamage(battle, byUuid, caps, of) {
  const holds = ids => [...byUuid.values()].some(list => list.some(fx => ids.includes(fx.i)));

  // ---- potency: scale a native burn / regular-poison residual once, only when the holder inflicted it ----
  if (holds(Object.keys(POTENCY)) && typeof battle.damage === 'function') {
    const rawDamage = battle.damage;
    battle.damage = function (damage, target, source, effect, instafaint) {
      let scaled = damage;
      try {
        const victim = target || (this.event && this.event.target);
        const cause = effect || (this.event && this.effect);
        if (typeof damage === 'number' && damage > 0 && victim && cause && cause.effectType === 'Status' && (cause.id === 'brn' || cause.id === 'psn')) {
          const inflictor = victim.statusState && victim.statusState.source;
          // An unknown, self-inflicted (Flame Orb) or allied source, a fainted or benched inflictor: native damage only.
          if (inflictor && inflictor !== victim && inflictor.hp > 0 && inflictor.isActive && inflictor.side !== victim.side) {
            const percents = of(inflictor, POTENCY).filter(fx => POTENCY[fx.i] === cause.id).map(fx => fx.p);
            if (percents.length) scaled = damage * grow(percents, caps.res);
          }
        }
      } catch (err) {
        scaled = damage;
      }
      return rawDamage.call(this, scaled, target, source, effect, instafaint);
    };
  }

  // ---- rending: a chance on a direct hit to start or deepen a bleed ----
  if (!holds([RENDING])) return;
  const bleeds = new Map();           // afflicted Pokemon -> {stacks, turns, percent}
  const rolled = new Set();           // one roll per move use and target, however many hits it lands
  const rawSpread = battle.spreadDamage;
  if (typeof rawSpread === 'function') {
    battle.spreadDamage = function (damage, targetArray, source, effect, instafaint) {
      const result = rawSpread.apply(this, arguments);
      try {
        if (source && effect && typeof effect === 'object' && effect.effectType === 'Move' && Array.isArray(targetArray)) {
          const percents = of(source, {[RENDING]: 1}).map(fx => fx.p);
          for (const [i, target] of targetArray.entries()) {
            if (!percents.length || !target || target === source || target.side === source.side || !(target.hp > 0)) continue;
            if (!(typeof result[i] === 'number' && result[i] > 0)) continue;
            const key = `${this.turn}:${source.uuid}:${source.activeMoveActions}:${target.uuid}`;
            if (rolled.has(key)) continue;
            rolled.add(key);
            if (this.random(100) >= RENDING_CHANCE) continue;
            const state = bleeds.get(target) || {stacks: 0, turns: 0, percent: 0};
            state.stacks = Math.min(BLEED_STACKS, state.stacks + 1);
            state.turns = BLEED_TURNS;
            state.percent = Math.max(state.percent, ...percents);
            bleeds.set(target, state);
            note(this, `${target.name} is bleeding!`);
          }
        }
      } catch (err) { /* fail open: the hit already happened */ }
      return result;
    };
  }

  const rawResidual = battle.residualEvent;
  if (typeof rawResidual === 'function') {
    battle.residualEvent = function (eventid) {
      const result = rawResidual.apply(this, arguments);
      if (eventid !== 'Residual') return result;
      for (const [mon, state] of [...bleeds]) {
        try {
          if (!(mon.hp > 0) || !mon.isActive) {
            // A fainted or switched-out Pokemon loses its bleed, as a native volatile would.
            bleeds.delete(mon);
            continue;
          }
          const bleed = {id: 'ascensionbleed', name: 'Bleed', fullname: 'bleed', effectType: 'Status'};
          let amount = Math.max(1, Math.trunc(mon.baseMaxhp / BLEED_DIVISOR * state.stacks * grow([state.percent], caps.res)));
          // Magic Guard and other native damage rules get their say; a plain -damage line is used because the client
          // misreads a [from] it does not know.
          const allowed = this.runEvent('Damage', mon, null, bleed, amount, true);
          if (allowed || allowed === 0) {
            amount = Math.max(1, Math.trunc(allowed));
            const dealt = mon.damage(amount, null, bleed);
            if (dealt) this.add('-damage', mon, mon.getHealth);
          }
          if (--state.turns <= 0) bleeds.delete(mon);
        } catch (err) { bleeds.delete(mon); }
      }
      return result;
    };
  }
}

function install(sim) {
  const Battle = sim.Battle;
  const oldStart = Battle.prototype.start;
  Battle.prototype.start = function () {
    const result = oldStart.apply(this, arguments);
    if (this.deserialized) return result;   // a restored battle must not re-apply what already happened
    const payload = this.format && this.format.ascensionFx;
    if (!payload || typeof payload !== 'object' || payload.v !== 1) return result;
    try {
      const count = apply(this, payload);
      // To the server log as well as the battle: the Java side logs what it HANDED to Showdown, and only this line proves the
      // JavaScript received it and applied it.
      console.log(`[AscensionLib] Applied ascension effects to ${count} Pokemon in a battle.`);
    } catch (err) {
      note(this, 'Ascension effects could not be applied.');
    }
    return result;
  };
}

module.exports = {install, apply, parse, HANDLED, MAX_MONS, MAX_EFFECTS};

// Loaded by Showdown (through CobbleRaids' extension loader): install against the simulator beside this file. The tests
// set the flag and call install() themselves against a simulator they loaded.
if (!globalThis.__ASCENSION_FX_TEST__) install({Battle: require('./sim/battle').Battle});
