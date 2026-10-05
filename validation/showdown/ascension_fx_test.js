#!/usr/bin/env node
// Tests ascension-fx.js (docs/BATTLE-ADAPTER-DESIGN.md) against the REAL Showdown simulator Cobblemon unbundles, with no
// Minecraft server.
//
//   node validation/showdown/ascension_fx_test.js --showdown-dir L:/claude-cobbleraids-work/testserver-full/showdown
//       [--tower-fx L:/CobbleTowers/src/main/resources/assets/cobbletowers/showdown/tower-fx.js]
//
// Showdown is plain CommonJS, so the module is installed into it and driven through BattleStream with the same `>start` /
// `>player` lines Cobblemon writes. Each test asserts what the simulator actually did (damage dealt, HP healed), never what
// the module claims it asked for. Not part of CI: it needs the unbundled simulator from a server directory. Run it after
// touching ascension-fx.js and after a Cobblemon upgrade.

const assert = require('assert');
const fs = require('fs');
const path = require('path');

const args = process.argv.slice(2);
const dirArg = args.indexOf('--showdown-dir');
const towerArg = args.indexOf('--tower-fx');
const SHOWDOWN = path.resolve(dirArg >= 0 ? args[dirArg + 1] : (process.env.SHOWDOWN_DIR || 'showdown'));
const TOWER_FX = path.resolve(towerArg >= 0 ? args[towerArg + 1] : path.join(__dirname, '../../../../CobbleTowers/src/main/resources/assets/cobbletowers/showdown/tower-fx.js'));
const FX_FILE = path.resolve(__dirname, '../../ascensionlib/src/main/resources/assets/ascensionlib/showdown/ascension-fx.js');
const AFFIXES = path.resolve(__dirname, '../../design/affixes.json');
if (!fs.existsSync(path.join(SHOWDOWN, 'sim', 'battle.js'))) {
  console.error('no Showdown simulator at ' + SHOWDOWN + ' (pass --showdown-dir)');
  process.exit(2);
}

globalThis.__ASCENSION_FX_TEST__ = true;
globalThis.__TOWER_FX_TEST__ = true;
const BS = require(path.join(SHOWDOWN, 'sim/battle-stream'));
const {Battle} = require(path.join(SHOWDOWN, 'sim/battle'));
const fx = require(FX_FILE);

/** The tests write effects as {id, pct, type}; the wire format is {i, p, t}. Anything that is not an object passes through. */
function compact(payload) {
  if (!payload || typeof payload !== 'object' || !payload.mons || typeof payload.mons !== 'object') return payload;
  const mons = {};
  for (const [uuid, list] of Object.entries(payload.mons)) {
    mons[uuid] = Array.isArray(list)
      ? list.map(e => (e && typeof e === 'object' ? {i: e.id, p: e.pct, t: e.type} : e))
      : list;
  }
  return Object.assign({}, payload, {mons});
}

// ================================ driving the real simulator ========================================================

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
const U1 = '11111111-1111-1111-1111-111111111111';
const U2 = '22222222-2222-2222-2222-222222222222';

/** Cobblemon's packed team: name|species|uuid|currentHealth|status|statusDuration|item|ability|moves|pp|nature|... */
function pack(species, uuid, ability, moves, level, health = '', item = 'none') {
  return [species, species, uuid, health, '', '', item, ability, moves.join(','), moves.map(() => '16/16').join(','),
    'Hardy', '', '', '', '', String(level), ''].join('|');
}

async function battle({fx: payload, teams, moves, turns = 1, towerFx} = {}) {
  const stream = new BS.BattleStream();
  const lines = [];
  
  (async () => { try { for await (const chunk of stream) lines.push(...String(chunk).split('\n')); } catch (e) { /* surfaced by asserts */ } })();
  const format = {mod: 'gen9', gameType: 'singles', gen: 9, ruleset: [], effectType: 'Format'};
  if (payload !== undefined) format.ascensionFx = compact(payload);
  if (towerFx !== undefined) format.towerFx = towerFx;
  stream.write('>start ' + JSON.stringify({format, seed: [1, 2, 3, 4]}));
  stream.write('>player p1 ' + JSON.stringify({name: 'A', team: teams[0]}));
  stream.write('>player p2 ' + JSON.stringify({name: 'B', team: teams[1]}));
  await sleep(200);
  for (let turn = 0; turn < turns && !stream.battle.ended; turn++) {
    stream.write('>p1 ' + moves[0]);
    stream.write('>p2 ' + moves[1]);
    await sleep(200);
  }
  return {lines, battle: stream.battle};
}

const mon = (b, side) => b.battle.sides[side].active[0];
const lost = (b, side) => mon(b, side).maxhp - mon(b, side).hp;

const BLASTOISE = (health = '') => pack('Blastoise', U1, 'torrent', ['hydropump', 'icebeam', 'surf', 'earthquake', 'recover'], 50, health);
const CHARIZARD = () => pack('Charizard', U2, 'blaze', ['flamethrower', 'airslash', 'dragonclaw', 'roost'], 50);
const TEAMS = () => [BLASTOISE(), CHARIZARD()];

const tests = [];
const test = (name, fn) => tests.push({name, fn});
let baseline;

test('1. identity: effects keyed by the packed uuid reach exactly that Pokemon', async () => {
  const b = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 20}]}}});
  assert.strictEqual(mon(b, 0).uuid, U1, 'the simulator holds the uuid Cobblemon packed');
  assert.strictEqual(mon(b, 1).uuid, U2);
  assert.ok(lost(b, 1) > lost(baseline, 1), 'the keyed Pokemon hits harder');
  assert.strictEqual(lost(b, 0), lost(baseline, 0), 'the other Pokemon is untouched');
});

test('1b. identity: an unknown uuid does nothing', async () => {
  const b = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {'99999999-9999-9999-9999-999999999999': [{id: 'overwhelming_force', pct: 50}]}}});
  assert.strictEqual(lost(b, 1), lost(baseline, 1));
});

test('2. outgoing: +20% lands within native rounding of 1.2x', async () => {
  const b = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 20}]}}});
  const ratio = lost(b, 1) / lost(baseline, 1);
  assert.ok(Math.abs(ratio - 1.2) < 0.02, 'ratio ' + ratio);
});

test('2b. outgoing: a typed affix applies to its type only', async () => {
  const on = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'type_focus', pct: 20, type: 'Water'}]}}});
  const off = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'type_focus', pct: 20, type: 'Fire'}]}}});
  assert.ok(lost(on, 1) > lost(baseline, 1));
  assert.strictEqual(lost(off, 1), lost(baseline, 1), 'hydropump is not fire');
});

test('2c. outgoing: super_effective_force reads native effectiveness after the engine computed it', async () => {
  const on = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'super_effective_force', pct: 20}]}}});
  assert.ok(lost(on, 1) > lost(baseline, 1), 'water vs fire is super effective');
  const resisted = await battle({teams: TEAMS(), moves: ['move 4', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'super_effective_force', pct: 20}]}}});
  const plain = await battle({teams: TEAMS(), moves: ['move 4', 'move 1']});
  assert.strictEqual(lost(resisted, 1), lost(plain, 1), 'earthquake is not super effective on Charizard (flying: immune)');
});

test('2d. outgoing: an HP threshold uses the HP before the move', async () => {
  const low = [pack('Blastoise', U1, 'torrent', ['surf'], 50, '40'), pack('Charizard', U2, 'blaze', ['flamethrower', 'airslash', 'dragonclaw', 'roost'], 100)];
  const withFx = await battle({teams: low, moves: ['move 1', 'move 4'], fx: {v: 1, mons: {[U1]: [{id: 'last_stand', pct: 20}]}}});
  const without = await battle({teams: low, moves: ['move 1', 'move 4']});
  assert.ok(lost(withFx, 1) > lost(without, 1), `last_stand active at low HP: ${lost(withFx, 1)} vs ${lost(without, 1)} (hp ${mon(withFx, 0).hp}/${mon(withFx, 0).maxhp})`);
  const healthy = await battle({teams: TEAMS(), moves: ['move 3', 'move 4'], fx: {v: 1, mons: {[U1]: [{id: 'last_stand', pct: 20}]}}});
  const healthyPlain = await battle({teams: TEAMS(), moves: ['move 3', 'move 4']});
  assert.strictEqual(lost(healthy, 1), lost(healthyPlain, 1), 'last_stand inactive at full HP');
});

test('2e. outgoing: stacking multiplies and the channel total is capped once', async () => {
  const two = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 12}, {id: 'physical_force', pct: 0}, {id: 'kindred_force', pct: 10}]}}});
  const one = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 12}]}}});
  assert.ok(lost(two, 1) > lost(one, 1));
  const huge = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 90}, {id: 'kindred_force', pct: 90}]}}});
  const doubled = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 100}]}}});
  assert.strictEqual(lost(huge, 1), lost(doubled, 1), 'capped at +100% however it was reached');
});

test('3. incoming: a reduction lowers damage taken and never to zero', async () => {
  const b = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'iron_resolve', pct: 9}]}}});
  assert.ok(lost(b, 0) < lost(baseline, 0));
  const huge = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'iron_resolve', pct: 90}]}}});
  const half = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'iron_resolve', pct: 50}]}}});
  assert.strictEqual(lost(huge, 0), lost(half, 0), 'capped at 50% reduction');
});

test('3b. incoming: opening_guard works on the first hit only (once-per-battle state)', async () => {
  const fx = {v: 1, mons: {[U1]: [{id: 'opening_guard', pct: 10}]}};
  const two = await battle({teams: TEAMS(), moves: ['move 4', 'move 1'], fx, turns: 2});
  const plain = await battle({teams: TEAMS(), moves: ['move 4', 'move 1'], turns: 2});
  // Turn one the guard applies, turn two it must not: so the second hit equals the plain second hit.
  const hits = b => b.lines.filter(l => /^\|-damage\|p1a/.test(l) && !l.endsWith('/100')).map(l => Number(l.split('|')[3].split('/')[0]));
  const a = hits(two), p = hits(plain);
  assert.ok(a.length >= 2 && p.length >= 2, 'two incoming hits each: ' + JSON.stringify({a, p}));
  assert.ok(a[0] > p[0], 'first hit reduced (more HP left)');
  assert.strictEqual(a[0] - a[1], p[0] - p[1], 'second hit is the native amount ' + JSON.stringify({a, p}));
});

test('4. healing: restorative boosts the holder\'s own recovery move and nothing else', async () => {
  const hurt = [pack('Blastoise', U1, 'torrent', ['recover'], 50, '30'), CHARIZARD()];
  const fx = {v: 1, mons: {[U1]: [{id: 'restorative', pct: 20}]}};
  const withFx = await battle({teams: hurt, moves: ['move 1', 'move 4'], fx});
  const without = await battle({teams: hurt, moves: ['move 1', 'move 4']});
  const gain = (b) => mon(b, 0).hp;
  assert.ok(gain(withFx) > gain(without), `recover heals more: ${gain(withFx)} vs ${gain(without)}`);
});

test('4b. healing: drain moves are healed more', async () => {
  const teams = [pack('Blastoise', U1, 'torrent', ['gigadrain'], 50, '60'), pack('Charizard', U2, 'blaze', ['splash'], 50)];
  const fx = {v: 1, mons: {[U1]: [{id: 'restorative', pct: 20}]}};
  const withFx = await battle({teams, moves: ['move 1', 'move 1'], fx});
  const without = await battle({teams, moves: ['move 1', 'move 1']});
  assert.ok(mon(withFx, 0).hp > mon(without, 0).hp, `drain heals more: ${mon(withFx, 0).hp} vs ${mon(without, 0).hp}`);
});

test('4c. healing: a residual heal (Aqua Ring) is not boosted, only the holder own move', async () => {
  const teams = [pack('Blastoise', U1, 'torrent', ['aquaring'], 50, '30'), CHARIZARD()];
  const fx = {v: 1, mons: {[U1]: [{id: 'restorative', pct: 50}]}};
  const withFx = await battle({teams, moves: ['move 1', 'move 4'], fx, turns: 3});
  const without = await battle({teams, moves: ['move 1', 'move 4'], turns: 3});
  assert.ok(mon(without, 0).hp > 30, 'aqua ring did heal in the baseline: ' + mon(without, 0).hp);
  assert.strictEqual(mon(withFx, 0).hp, mon(without, 0).hp);
});

test('4d. healing: the opponent drain is not boosted by the holder affix', async () => {
  const teams = [pack('Blastoise', U1, 'torrent', ['surf'], 50), pack('Charizard', U2, 'blaze', ['gigadrain'], 50, '60')];
  const fx = {v: 1, mons: {[U1]: [{id: 'restorative', pct: 50}]}};
  const withFx = await battle({teams, moves: ['move 1', 'move 1'], fx});
  const without = await battle({teams, moves: ['move 1', 'move 1']});
  assert.strictEqual(mon(withFx, 1).hp, mon(without, 1).hp);
});

test('2f. outgoing: opening_strike boosts the first damaging move after entering, not the second', async () => {
  const fx = {v: 1, mons: {[U1]: [{id: 'opening_strike', pct: 20}]}};
  const dealt = b => b.lines.filter(l => /^\|-damage\|p2a/.test(l) && !l.endsWith('/100')).map(l => Number(l.split('|')[3].split('/')[0]));
  const big = [BLASTOISE(), pack('Charizard', U2, 'blaze', ['splash'], 100)];
  const withFx = await battle({teams: big, moves: ['move 3', 'move 1'], fx, turns: 2});
  const plain = await battle({teams: big, moves: ['move 3', 'move 1'], turns: 2});
  const a = dealt(withFx), p = dealt(plain);
  assert.ok(a.length >= 2 && p.length >= 2, JSON.stringify({a, p}));
  assert.ok(a[0] < p[0], 'first hit harder: ' + JSON.stringify({a, p}));
  assert.strictEqual(a[0] - a[1], p[0] - p[1], 'second hit native: ' + JSON.stringify({a, p}));
});

test('5. triumphant: a move that faints the target heals the holder', async () => {
  const teams = [pack('Blastoise', U1, 'torrent', ['surf'], 100, '50'), pack('Charizard', U2, 'blaze', ['splash'], 5)];
  const fx = {v: 1, mons: {[U1]: [{id: 'triumphant', pct: 20}]}};
  const withFx = await battle({teams, moves: ['move 1', 'move 1'], fx});
  const without = await battle({teams, moves: ['move 1', 'move 1']});
  assert.ok(mon(withFx, 0).hp > mon(without, 0).hp, `healed after the kill: ${mon(withFx, 0).hp} vs ${mon(without, 0).hp}`);
});

test('6. hostile payloads never stop a battle', async () => {
  for (const fx of ['text', 5, null, {v: 2, mons: {}}, {v: 1, mons: 'x'}, {v: 1, mons: {[U1]: 'no'}},
                    {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 'lots'}, {id: 'nonsense', pct: 5}, null, 7]}}]) {
    const b = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx});
    assert.ok(b.lines.some(l => l.startsWith('|switch|')), 'the battle started for ' + JSON.stringify(fx));
    assert.strictEqual(lost(b, 1), lost(baseline, 1));
  }
});

test('7. no payload: byte-identical to a battle without the module', async () => {
  const b = await battle({teams: TEAMS(), moves: ['move 3', 'move 1']});
  const strip = log => log.filter(l => !l.startsWith('|t:') && !l.startsWith('|request|'));
  assert.deepStrictEqual(strip(b.lines), strip(baseline.lines));
});


test('8. every affix in the catalog has a handler (so a new affix cannot silently do nothing)', async () => {
  const catalog = JSON.parse(fs.readFileSync(AFFIXES, 'utf8'));
  const ids = (Array.isArray(catalog) ? catalog : catalog.affixes).map(a => a.id);
  const missing = ids.filter(id => !fx.HANDLED.has(id));
  assert.deepStrictEqual(missing, []);
});

test('9. caps come from the payload and are bounded', async () => {
  const doubled = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, caps: {out: 10}, mons: {[U1]: [{id: 'overwhelming_force', pct: 50}]}}});
  const tenth = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 10}]}}});
  assert.strictEqual(lost(doubled, 1), lost(tenth, 1), 'a 10% cap makes 50% land as 10%');
});

test('10. a type the dex does not know drops that effect, not the battle', async () => {
  const b = await battle({teams: TEAMS(), moves: ['move 3', 'move 1'], fx: {v: 1, mons: {[U1]: [{id: 'type_focus', pct: 20, type: 'Nonsense'}]}}});
  assert.strictEqual(lost(b, 1), lost(baseline, 1));
});

test('11. it composes with the CobbleTowers tower-fx.js module, which wraps the same modifyDamage', async function () {
  if (!fs.existsSync(TOWER_FX)) { console.log('       (skipped: no tower-fx.js at ' + TOWER_FX + ')'); return; }
  require(TOWER_FX).install({Battle});
  // A sturdy target, so no hit is capped by the target's remaining HP.
  const teams = [BLASTOISE(), pack('Charizard', U2, 'blaze', ['splash'], 100)];
  const moves = ['move 3', 'move 1'];
  const tower = [{op: 'damage', sides: ['p1'], type: 'any', percent: 110}];
  const mine = {v: 1, mons: {[U1]: [{id: 'overwhelming_force', pct: 20}]}};
  const plain = await battle({teams, moves});
  const towerOnly = await battle({teams, moves, towerFx: tower});
  const ascensionOnly = await battle({teams, moves, fx: mine});
  const both = await battle({teams, moves, towerFx: tower, fx: mine});
  assert.ok(lost(towerOnly, 1) > lost(plain, 1), 'tower-fx damage is applied');
  assert.ok(lost(ascensionOnly, 1) > lost(plain, 1), 'ascension damage is applied');
  assert.ok(lost(both, 1) > lost(towerOnly, 1) && lost(both, 1) > lost(ascensionOnly, 1), 'both multipliers apply together');
  const ratio = lost(both, 1) / lost(plain, 1);
  assert.ok(Math.abs(ratio - 1.32) < 0.05, 'about 1.1 x 1.2, got ' + ratio);
});

(async () => {
  baseline = await battle({teams: TEAMS(), moves: ['move 3', 'move 1']});          // before the module is installed
  fx.install({Battle});
  let failed = 0;
  for (const {name, fn} of tests) {
    try { await fn(); console.log('  ok   ' + name); } catch (err) { failed++; console.log('  FAIL ' + name + '\n       ' + (err && err.message)); }
  }
  console.log(failed ? `\n${failed} of ${tests.length} failed` : `\nall ${tests.length} passed`);
  process.exit(failed ? 1 : 0);
})();
