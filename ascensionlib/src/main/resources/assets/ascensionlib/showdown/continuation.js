'use strict';

const runtime = require('./ext-ascensionlib-normalization');
const Battle = require('./sim/battle').Battle;
const Pokemon = require('./sim/pokemon').Pokemon;
const State = require('./sim/state').State;
const archived = new WeakMap();
const archives = new WeakMap();
const rawToRef = State.toRef;
const rawFromRef = State.fromRef;
const rawDeserialize = State.deserialize;
let loadingArchives = null;

function canonical(value) {
  if (Array.isArray(value)) return '[' + value.map(canonical).join(',') + ']';
  if (value && typeof value === 'object') return '{' + Object.keys(value).sort()
    .filter(key => value[key] !== undefined).map(key => JSON.stringify(key) + ':' + canonical(value[key])).join(',') + '}';
  return JSON.stringify(value);
}
function equal(a, b, label) {
  if (canonical(a) !== canonical(b)) throw new Error('Continuation changed ' + label);
}

// Keep historical effect sources without putting withdrawn companions in the usable team.
State.toRef = function (object) {
  const id = archived.get(object);
  return id ? `[LFArchived:${id}]` : rawToRef.apply(this, arguments);
};
State.fromRef = function (reference, battle) {
  if (reference.startsWith('[LFArchived:') && reference.endsWith(']')) {
    const mon = archives.get(battle)?.get(reference.slice(12, -1));
    if (!mon) throw new Error('Missing archived continuation source');
    return mon;
  }
  return rawFromRef.apply(this, arguments);
};
State.deserialize = function (state, object, skip, battle) {
  if (loadingArchives && object === battle && !archives.has(battle)) {
    if (loadingArchives.length > 64) throw new Error('Too many archived continuation sources');
    const restored = new Map(); archives.set(battle, restored);
    for (const entry of loadingArchives) {
      if (!/^p[12]$/.test(entry.side) || !entry.data || !entry.data.set || restored.has(entry.data.uuid))
        throw new Error('Invalid archived continuation source');
      const mon = new Pokemon(entry.data.set, battle.sides[Number(entry.side[1]) - 1]);
      restored.set(entry.data.uuid, mon); archived.set(mon, entry.data.uuid);
    }
    for (const entry of loadingArchives) State.deserializePokemon(entry.data, restored.get(entry.data.uuid));
  }
  return rawDeserialize.apply(this, arguments);
};

globalThis.ascensionNormalizationArchives = function (battle) {
  return Array.from(archives.get(battle) || [], ([id, mon]) => ({side: mon.side.id, data: State.serializePokemon(mon)}));
};
globalThis.ascensionNormalizationIsArchived = mon => archived.has(mon);

function replaceTeam(battle, fresh) {
  const side = battle.sides[0], incoming = fresh.sides[0];
  if (side.active.length !== 1 || incoming.active.length !== 1) throw new Error('Continuation supports ranked singles only');
  const former = side.pokemon.slice(), formerActive = side.active.slice();
  const saved = archives.get(battle) || new Map(); archives.set(battle, saved);
  if (saved.size + former.length > 64) throw new Error('Continuation archive capacity reached');
  for (const mon of former) {
    if (saved.has(mon.uuid)) throw new Error('Duplicate archived continuation identity');
    archived.set(mon, mon.uuid); saved.set(mon.uuid, mon); mon.isActive = false;
  }
  // References now use archive IDs; snapshot before changing the usable team.
  const field = State.serializeField(battle.field);
  const opponent = battle.sides[1].pokemon.map(mon => State.serializePokemon(mon));
  const random = battle.prng.seed.slice(), turn = battle.turn;
  const sideConditions = State.serializeWithRefs(side.sideConditions, battle);
  const slotConditions = State.serializeWithRefs(side.slotConditions, battle);
  const keeperActions = battle.queue.list.filter(action => !former.includes(action.pokemon));
  side.name = incoming.name; side.avatar = incoming.avatar;
  // Staging may have run entry abilities. Reconstruct from sets so those effects run once in the retained field.
  side.team = JSON.parse(JSON.stringify(incoming.team));
  side.pokemon = side.team.map(set => new Pokemon(set, side));
  for (const [index, mon] of side.pokemon.entries()) {
    mon.side = side; mon.battle = battle; mon.position = index; mon.isActive = false;
  }
  side.pokemonLeft = side.pokemon.filter(mon => !mon.fainted && mon.hp > 0).length;
  if (!side.pokemonLeft) throw new Error('Incoming continuation team has no survivors');
  if (battle.requestState === 'teampreview') {
    side.active = [null];
  } else {
    const mon = side.pokemon[0];
    if (mon.fainted || mon.hp <= 0) throw new Error('Incoming lead is unavailable');
    mon.isActive = true; mon.newlySwitched = true; side.active = [mon];
    if (formerActive[0]) {
      // The approved full-field handoff retains active-slot boosts and volatile effects.
      mon.boosts = {...formerActive[0].boosts};
      mon.volatiles = State.deserializeWithRefs(State.serializeWithRefs(formerActive[0].volatiles, battle), battle);
      for (const effect of Object.values(mon.volatiles)) if (effect.target === formerActive[0]) effect.target = mon;
    }
  }
  // Outgoing team commands cannot be executed by new species; keep all other queued actions in order.
  battle.queue.list = keeperActions;
  side.clearChoice();
  equal(field, State.serializeField(battle.field), 'battlefield');
  equal(opponent, battle.sides[1].pokemon.map(mon => State.serializePokemon(mon)), 'guardian state');
  equal(sideConditions, State.serializeWithRefs(side.sideConditions, battle), 'side hazards');
  equal(slotConditions, State.serializeWithRefs(side.slotConditions, battle), 'slot conditions');
  equal(random, battle.prng.seed, 'random state');
  if (turn !== battle.turn) throw new Error('Continuation advanced the turn during handoff');
}

function summary(battle) {
  return {v: 1, turn: battle.turn, sides: battle.sides.map(side => ({id: side.id,
    active: side.active.map(mon => mon ? mon.uuid : null), pokemon: side.pokemon.map(mon => ({
      uuid: mon.uuid, hp: mon.hp, maxhp: mon.maxhp, fainted: mon.fainted, status: mon.status,
      statusState: State.serializeWithRefs(mon.statusState, battle), ability: mon.ability, item: mon.item,
      species: mon.species.id, boosts: mon.boosts,
      moves: mon.moveSlots.map(move => ({id: move.id, pp: move.pp, maxpp: move.maxpp, virtual: move.virtual === true}))}))}))};
}

globalThis.ascensionNormalizationRestore = function (id, checkpoint, handoff) {
  if (typeof checkpoint !== 'string' || checkpoint.length > 1048576 || typeof handoff !== 'boolean')
    throw new Error('Invalid continuation input');
  const current = runtime.prepared.get(id), fresh = current && current.battle;
  const stream = fresh && runtime.streams.get(fresh);
  if (!current || current.acknowledged || !stream || stream.battle !== fresh) throw new Error('Fresh native continuation is not staged');
  const envelope = JSON.parse(checkpoint);
  if (envelope.v !== 1 || !envelope.battle || !envelope.manifest || !Array.isArray(envelope.archives || []))
    throw new Error('Invalid continuation envelope');
  let battle;
  try {
    if (loadingArchives) throw new Error('Nested continuation restore');
    loadingArchives = envelope.archives || [];
    battle = Battle.fromJSON(envelope.battle);
  } finally { loadingArchives = null; }
  equal(envelope.battle, battle.toJSON(), 'serialized battle round-trip');
  equal(envelope.archives || [], globalThis.ascensionNormalizationArchives(battle), 'archived effect sources');
  if (battle.ended || battle.sides.length !== 2 || battle.sides[1].pokemon.length !== 1)
    throw new Error('Continuation is not an active ranked guardian battle');
  const target = JSON.parse(current.manifest), old = envelope.manifest;
  const guardian = battle.sides[1].pokemon[0].uuid;
  equal(old.mons[guardian], target.mons[guardian], 'guardian normalization');
  battle.format = fresh.format;
  if (handoff) replaceTeam(battle, fresh);
  else equal(old.mons, target.mons, 'retry identities and normalization');
  battle.restart(fresh.send);
  const identities = battle.sides.flatMap(side => side.pokemon.map(mon => mon.uuid));
  equal(identities.slice().sort(), Object.keys(target.mons).sort(), 'restored team manifest');
  for (const side of battle.sides) for (const mon of side.pokemon) runtime.valuesFor(mon);
  runtime.states.delete(fresh); runtime.streams.delete(fresh);
  const restored = {battle, manifest: current.manifest, acknowledged: false, suspended: false,
    choices: new Map(), restored: true, resumed: true, handoff};
  runtime.prepared.set(id, restored); runtime.states.set(battle, restored);
  runtime.streams.set(battle, stream); stream.battle = battle;
  return JSON.stringify(summary(battle));
};

globalThis.ascensionNormalizationResumeRequests = function (battle, handoff) {
  // Rebuild native presentation without replaying old turns or re-running guardian entry effects.
  for (const side of battle.sides) for (const mon of side.active) if (mon) {
    battle.add('switch', mon, mon.getDetails);
    for (const [stat, stages] of Object.entries(mon.boosts)) if (stages) battle.add('-setboost', mon, stat, stages);
  }
  if (handoff && battle.sides[0].active[0])
    battle.queue.insertChoice({choice: 'runSwitch', pokemon: battle.sides[0].active[0]});
  const requests = battle.getRequests(battle.requestState);
  for (let index = 0; index < battle.sides.length; index++) {
    const side = battle.sides[index];
    const done = side.isChoiceDone();
    side.emitRequest(done ? {wait: true, side: side.getRequestData()} : requests[index]);
  }
  // An interrupted switch phase may already have all remaining actions queued.
  if (battle.midTurn && battle.sides.every(side => side.isChoiceDone())) battle.go();
  battle.sendUpdates();
};
