'use strict';

// Explicit per-battle fixed baseline stats. No payload means untouched simulator behavior.
// Loaded through the same extension host as ascension-fx.js, before any battle is created.
const Pokemon = require('./sim/pokemon').Pokemon;
const Battle = require('./sim/battle').Battle;
const rawSetSpecies = Pokemon.prototype.setSpecies;
const rawStart = Battle.prototype.start;
const rawChoose = Battle.prototype.choose;
const rawUndo = Battle.prototype.undoChoice;
const rawDestroy = Battle.prototype.destroy;
const rawCheckWin = Battle.prototype.checkWin;
const prepared = new Map();
const states = new WeakMap();
const streams = new WeakMap();
const BattleStream = require('./sim/battle-stream').BattleStream;
const rawWriteLine = BattleStream.prototype._writeLine;
BattleStream.prototype._writeLine = function () {
  const result = rawWriteLine.apply(this, arguments);
  if (this.battle && this.battle.format && this.battle.format.ascensionNormalization) streams.set(this.battle, this);
  return result;
};
const keys = ['atk', 'def', 'spa', 'spd', 'spe'];
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/;

function valuesFor(mon) {
  if (typeof globalThis.ascensionNormalizationIsArchived === 'function' && globalThis.ascensionNormalizationIsArchived(mon)) return null;
  const payload = mon.battle.format && mon.battle.format.ascensionNormalization;
  if (payload === undefined) return null;
  if (!payload || payload.v !== 1 || !payload.mons || typeof payload.mons !== 'object'
      || !uuid.test(payload.battle) || Array.isArray(payload.mons)) throw new Error('Invalid normalization payload');
  const identities = Object.keys(payload.mons);
  if (!identities.length || identities.length > 24 || identities.some(id => !uuid.test(id)))
    throw new Error('Invalid normalization identities');
  if (!Object.prototype.hasOwnProperty.call(payload.mons, mon.uuid))
    throw new Error('Unregistered normalized combatant');
  const values = payload.mons[mon.uuid];
  if (!Array.isArray(values) || values.length !== 7 || values.some(value => !Number.isInteger(value))
      || values[0] < 1 || values[0] > 100 || values.slice(1).some(value => value < 1 || value > 10000)
      || mon.level !== values[0]) throw new Error('Invalid normalized combatant stats');
  return values;
}

Pokemon.prototype.setSpecies = function () {
  const values = valuesFor(this);
  const initializing = !this.maxhp;
  const result = rawSetSpecies.apply(this, arguments);
  if (!values || !result) return result;
  // Run before the constructor applies the supplied currentHealth. Never heal on a form change.
  if (initializing) {
    this.baseMaxhp = values[1]; this.maxhp = values[1]; this.hp = values[1];
  }
  if (this.baseStoredStats) this.baseStoredStats.hp = values[1];
  for (let index = 0; index < keys.length; index++) {
    const key = keys[index], value = values[index + 2];
    this.storedStats[key] = value;
    if (this.baseStoredStats) this.baseStoredStats[key] = value;
    if (this.modifiedStats) this.modifiedStats[key] = value;
  }
  this.speed = this.storedStats.spe;
  return result;
};

Battle.prototype.start = function () {
  const payload = this.format && this.format.ascensionNormalization;
  if (payload !== undefined && !this.deserialized) {
    const seen = new Set();
    for (const side of this.sides) {
      if (!side) throw new Error('Normalized battle is missing a side');
      for (const mon of side.pokemon) {
        const values = valuesFor(mon);
        if (seen.has(mon.uuid)) throw new Error('Duplicate normalized combatant');
        seen.add(mon.uuid);
        if (mon.maxhp !== values[1] || mon.baseMaxhp !== values[1]
            || !Number.isInteger(mon.hp) || mon.hp < 0 || mon.hp > mon.maxhp
            || keys.some((key, index) => mon.storedStats[key] !== values[index + 2]))
          throw new Error('Normalized simulator stat read-back failed');
      }
    }
    if (seen.size !== Object.keys(payload.mons).length)
      throw new Error('Normalized battle manifest contains absent combatants');
    if (prepared.has(payload.battle) || prepared.size >= 1024)
      throw new Error('Normalized simulator identity is already active or capacity is exhausted');
    const state = {battle: this, manifest: JSON.stringify(payload), acknowledged: false, suspended: false, choices: new Map()};
    prepared.set(payload.battle, state); states.set(this, state);
    console.log(`[AscensionLib] Normalized simulator baseline verified for ${seen.size} combatants.`);
  }
  return rawStart.apply(this, arguments);
};

Battle.prototype.choose = function (side, choice) {
  const state = states.get(this);
  if (state && state.suspended) {
    if (!state.periodic) return false;
    if (!/^p[1-4]$/.test(side) || typeof choice !== 'string' || choice.length > 4096)
      throw new Error('Invalid checkpoint-buffered choice');
    state.checkpointChoices.set(side, choice); return true;
  }
  if (state && state.restored && !state.acknowledged) return false;
  if (state && !state.acknowledged) {
    if (!/^p[1-4]$/.test(side) || typeof choice !== 'string' || choice.length > 4096)
      throw new Error('Invalid pending normalized battle choice');
    state.choices.set(side, choice);
    return true;
  }
  return rawChoose.apply(this, arguments);
};
Battle.prototype.undoChoice = function (side) {
  const state = states.get(this);
  if (state && state.suspended) {
    if (state.periodic) {
      if (!/^p[1-4]$/.test(side)) throw new Error('Invalid checkpoint-buffered undo');
      state.checkpointChoices.set(side, null);
    }
    return;
  }
  if (state && !state.acknowledged) { state.choices.delete(side); return; }
  return rawUndo.apply(this, arguments);
};
Battle.prototype.checkWin = function () {
  const state = states.get(this);
  if (state && state.acknowledged && !this.ended && this.gameType === 'singles'
      && this.sides.length === 2 && this.sides[0].pokemonLeft === 0
      && this.sides[0].pokemon.length && this.sides[0].pokemon.every(mon => mon.fainted === true)) {
    // Halt at the native terminal check without emitting a win/tie or draining the retained queue.
    state.wipe = this.sides[1].pokemonLeft === 0 ? 'DOUBLE_WIPE' : 'PLAYER_WIPE';
    state.suspended = true; state.choices.clear(); this.requestState = 'switch';
    return true;
  }
  return rawCheckWin.apply(this, arguments);
};

globalThis.ascensionNormalizationOutcome = function (id) {
  const state = prepared.get(id);
  if (!state || !state.acknowledged || state.battle.ended)
    throw new Error('Normalized simulator is not ready for outcome inspection');
  return state.wipe || 'ACTIVE';
};

Battle.prototype.destroy = function () {
  const state = states.get(this);
  if (state) {
    const id = JSON.parse(state.manifest).battle;
    if (prepared.get(id) === state) prepared.delete(id);
    states.delete(this);
  }
  return rawDestroy.apply(this, arguments);
};

globalThis.ascensionNormalizationVerify = function (id) {
  const state = prepared.get(id);
  if (!state || state.battle.ended) throw new Error('Normalized simulator battle is not active');
  for (const side of state.battle.sides) for (const mon of side.pokemon) {
    const values = valuesFor(mon);
    if (mon.level !== values[0] || mon.baseMaxhp !== values[1]
        || (!state.restored && (mon.maxhp !== values[1] || keys.some((key, index) => mon.storedStats[key] !== values[index + 2]))))
      throw new Error('Simulator stats changed before native acknowledgement');
  }
  return state.manifest;
};
globalThis.ascensionNormalizationAcknowledge = function (id) {
  const state = prepared.get(id);
  if (!state || state.battle.ended || state.suspended) throw new Error('Normalized simulator battle is not active');
  if (state.acknowledged) return;
  state.acknowledged = true;
  if (state.resumed) {
    state.resumed = false;
    globalThis.ascensionNormalizationResumeRequests(state.battle, state.handoff);
  }
  const choices = Array.from(state.choices.entries()); state.choices.clear();
  for (const [side, choice] of choices) rawChoose.call(state.battle, side, choice);
  state.battle.sendUpdates();
};

globalThis.ascensionNormalizationCapture = function (id, periodic) {
  const state = prepared.get(id);
  if (!state || state.battle.ended || !state.acknowledged)
    throw new Error('Normalized simulator is not ready for an interruption checkpoint');
  if (state.periodic) throw new Error('Another complete checkpoint is already capturing');
  state.suspended = true; state.periodic = periodic === true && !state.wipe;
  if (state.periodic) state.checkpointChoices = new Map();
  const confirmedFaints = [];
  for (const side of state.battle.sides) for (const mon of side.pokemon)
    if (mon.fainted === true) confirmedFaints.push(mon.uuid);
  const checkpoint = JSON.stringify({v: 1, manifest: JSON.parse(state.manifest),
    confirmedFaints, outcome: state.wipe || 'ACTIVE', battle: state.battle.toJSON(), archives: typeof globalThis.ascensionNormalizationArchives === 'function'
      ? globalThis.ascensionNormalizationArchives(state.battle) : []});
  if (checkpoint.length > 1048576) throw new Error('Normalized simulator checkpoint exceeds size limit');
  state.lastCapture = checkpoint;
  return checkpoint;
};
globalThis.ascensionNormalizationReleaseCheckpoint = function (id, checkpoint) {
  const state = prepared.get(id);
  if (!state || state.battle.ended || !state.acknowledged || !state.suspended
      || !state.periodic || state.wipe || state.lastCapture !== checkpoint)
    throw new Error('Complete checkpoint token no longer owns the simulator gate');
  const choices = Array.from(state.checkpointChoices.entries());
  state.checkpointChoices.clear(); state.periodic = false; state.suspended = false;
  delete state.lastCapture;
  for (const [side, choice] of choices) {
    if (choice === null) rawUndo.call(state.battle, side);
    else rawChoose.call(state.battle, side, choice);
  }
  state.battle.sendUpdates(); return 'RESUMED';
};

module.exports = {prepared, states, streams, valuesFor};
