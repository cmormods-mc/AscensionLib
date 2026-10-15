// Drives AscensionLib's web pages in a headless Chromium with a mock window.webgui, so the pages can be tested without Minecraft.
// usage: node tools/page_harness.mjs [--shots <dir>]
// Needs playwright-core (found in ../CobbleTowers/web/node_modules) and the headless shell in work/preview-browsers.
import { createRequire } from 'node:module';
import http from 'node:http';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const pagesDir = path.join(root, 'ascensionlib/src/main/resources/web/ascensionlib');
const require = createRequire(path.resolve(root, '../../CobbleTowers/web/') + path.sep);
const { chromium } = require('playwright-core');
const shotsDir = process.argv.includes('--shots') ? process.argv[process.argv.indexOf('--shots') + 1] : null;
if (shotsDir) fs.mkdirSync(shotsDir, { recursive: true });

function findShell() {
  const base = path.join(root, 'work/preview-browsers');
  for (const d of fs.readdirSync(base)) {
    const exe = path.join(base, d, 'chrome-headless-shell-win64', 'chrome-headless-shell.exe');
    if (fs.existsSync(exe)) return exe;
  }
  throw new Error('no headless shell in ' + base);
}

const types = { '.html': 'text/html', '.js': 'text/javascript', '.css': 'text/css', '.png': 'image/png' };
const server = http.createServer((req, res) => {
  const file = path.join(pagesDir, decodeURIComponent(req.url.split('?')[0]).replace(/^\/+/, ''));
  if (!file.startsWith(pagesDir) || !fs.existsSync(file)) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': types[path.extname(file)] || 'application/octet-stream' });
  res.end(fs.readFileSync(file));
});
await new Promise(r => server.listen(0, '127.0.0.1', r));
const base = `http://127.0.0.1:${server.address().port}/`;

const MOCK = `
  window.__sent = []; window.__handlers = {};
  window.webgui = {
    isHud: location.pathname.endsWith('hud.html'),
    postToGame(m) { window.__sent.push(m); },
    on(e, fn) { (window.__handlers[e] = window.__handlers[e] || []).push(fn); },
  };
  window.__emit = (e, data, asString) => { (window.__handlers[e] || []).forEach(fn => fn(asString ? JSON.stringify(data) : data)); };
`;

// a 4x4 solid PNG so the sprite path can be exercised
const PNG = 'data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAQAAAAECAYAAACp8Z5+AAAAFUlEQVR42mP8z8Dwn4EIwDiqkL4KAfE8Bv9hHFSfAAAAAElFTkSuQmCC';

const price = { dust: 20, facets: 2, cores: 0 };
const slots = [
  { slotId: 'p1', category: 'prefix', name: 'Burn Boost', condition: 'Burns you inflict deal 50% more damage.', rank: 2, value: 12, bandMin: 10, bandMax: 14, nextMin: 15, nextMax: 19, upgradeBlock: '', refineBlock: '', reforgeBlock: '', reforgePool: 4 },
  { slotId: 's1', category: 'suffix', name: 'Sturdy', condition: '', rank: 5, value: 30, bandMin: 28, bandMax: 32, nextMin: -1, nextMax: -1, upgradeBlock: 'Already at the top rank', refineBlock: 'Needs 20 Dust', reforgeBlock: '', reforgePool: 3 },
];
const craftView = {
  pokemonId: '00000000-0000-0000-0000-000000000001', name: 'Mewtwo', speciesId: 'cobblemon:mewtwo', aspects: [], level: 52, rarityId: 'mythical', uniqueName: '777',
  pending: 1, nextMilestone: 60, profileRevision: 7, walletRevision: 8, dust: 1234, facets: 40, cores: 3, scouters: 5, refine: price, reforge: price,
  promotion: { toRarity: '', price, attunement: 90, attunementNeeded: 0, block: 'Already Mythical' },
  unique: { currentId: '777', catalysts: 1, fragments: 3, fragmentsNeeded: 5, block: '', options: [
    { id: '777', name: '777', benefit: 'More item rewards.', drawback: 'Lower defence.', current: true },
    { id: 'fury', name: 'Fury', benefit: 'Hits harder.', drawback: 'Takes more damage.', current: false } ] },
  slots, message: '', open: false,
};
const entry = (n, id, rarity = 'mythical', party = true, canHost = true, block = '') =>
  ({ id, name: n, speciesId: 'cobblemon:' + n.toLowerCase(), rarityId: rarity, uniqueName: 'Overmind', inParty: party, canHost, block });
const detail = (slotsExtra = []) => ({ level: 40, types: ['fire', 'flying'], baseStats: [90, 100, 80, 110, 80, 95], rarityId: 'epic', uniqueName: '777',
  slots: [{ category: 'prefix', name: 'Burn Boost', rank: 3, value: 14, min: 12, max: 16, condition: 'Burns you inflict deal 50% more damage.' }, ...slotsExtra] });

const results = [];
let failed = 0;
async function check(name, fn) {
  try { await fn(); results.push(['ok', name]); } catch (e) { failed++; results.push(['FAIL', name + ' :: ' + (e && e.message || e)]); }
}
const eq = (a, b, what) => { if (JSON.stringify(a) !== JSON.stringify(b)) throw new Error(`${what}: expected ${JSON.stringify(b)}, got ${JSON.stringify(a)}`); };
const yes = (v, what) => { if (!v) throw new Error('expected: ' + what); };

const browser = await chromium.launch({ executablePath: findShell() });
async function open(page, file, w = 1100, h = 700) {
  const ctx = await browser.newContext({ viewport: { width: w, height: h } });
  const p = await ctx.newPage();
  const errors = [];
  p.on('pageerror', e => errors.push('pageerror: ' + e.message));
  p.on('console', m => { if (m.type() === 'error') errors.push('console: ' + m.text()); });
  await p.addInitScript(MOCK);
  await p.goto(base + file);
  p.errors = errors;
  return p;
}
const sent = p => p.evaluate(() => window.__sent);
const emit = (p, e, d, s) => p.evaluate(([e, d, s]) => window.__emit(e, d, s), [e, d, s]);
const shot = async (p, n) => { if (shotsDir) await p.screenshot({ path: path.join(shotsDir, n + '.png') }); };
const noErrors = p => eq(p.errors, [], 'page errors');

// ---------------------------------------------------------------- craft
await check('craft: asks for its data, renders, upgrade flow, wallet, tabs', async () => {
  const p = await open(null, 'craft.html');
  eq((await sent(p)).map(m => m.channel), ['ascension_craft_ready'], 'first message');
  await emit(p, 'sprites', { mewtwo: PNG });
  await emit(p, 'craftView', craftView);
  eq(await p.textContent('#name'), 'Mewtwo', 'name');
  eq(await p.textContent('#pend'), '1', 'pending');
  eq(await p.locator('.tab').count(), 5, 'tabs');
  eq(await p.locator('#list .row').count(), 2, 'affix rows');
  yes((await p.textContent('#wallet')).includes('1,234'), 'wallet shows dust with separators');
  yes(await p.locator('#art img').count() === 1, 'sprite image appears from the data URL');
  await shot(p, 'craft-upgrade');
  // Upgrade tab is selected (pending > 0): Review is enabled for Burn Boost, then confirm sends one request
  await p.locator('#detail button.primary').click();
  yes(await p.locator('#scrim.on').count() === 1, 'review dialog opens');
  await p.click('#dOk');
  const m = (await sent(p)).find(x => x.channel === 'ascension_craft_confirm');
  yes(m, 'confirm was sent');
  eq([m.kind, m.slotId, m.profileRevision, m.walletRevision, m.pokemonId], ['upgrade', 'p1', 7, 8, craftView.pokemonId], 'confirm body');
  yes(/^[0-9a-f-]{36}$/.test(m.operationId), 'operation id is a uuid');
  await emit(p, 'craftDone', { operationId: m.operationId, ok: true, message: '', slotId: 'p1', oldName: 'Burn Boost', oldRank: 2, oldValue: 12, newName: 'Burn Boost', newRank: 3, newValue: 16, replayed: false }, true);
  yes((await p.textContent('.banner')).includes('Burn Boost'), 'banner shows the result');
  yes(await p.locator('#scrim.on').count() === 0, 'dialog closed');
  // other tabs render without errors
  for (const t of ['Refine', 'Reforge', 'Promote', 'Unique']) { await p.locator('.tab', { hasText: t }).click(); }
  await shot(p, 'craft-unique');
  yes((await p.textContent('#detail')).includes('Benefit'), 'unique tab shows benefit');
  await p.locator('.tab', { hasText: 'Promote' }).click();
  yes((await p.textContent('#detail')).includes('highest rarity'), 'promote at top rarity says so');
  await p.click('#fuse');
  yes((await sent(p)).some(x => x.channel === 'ascension_fusion_open' && x.hostId === craftView.pokemonId), 'Fuse opens fusion for this host');
  noErrors(p);
});

await check('craft: a server refusal shows and a stale wait recovers', async () => {
  const p = await open(null, 'craft.html');
  await emit(p, 'craftView', craftView);
  await p.locator('#detail button.primary').click();
  await p.click('#dOk');
  const m = (await sent(p)).find(x => x.channel === 'ascension_craft_confirm');
  await emit(p, 'craftDone', { operationId: m.operationId, ok: false, message: 'You do not have enough materials.', slotId: 'p1', oldName: '', oldRank: 0, oldValue: 0, newName: '', newRank: 0, newValue: 0, replayed: false });
  yes((await p.textContent('.banner')).includes('enough materials'), 'refusal is shown');
  // a Done for another operation is ignored
  await emit(p, 'craftDone', { operationId: 'someone-else', ok: true, message: '', slotId: '', oldName: '', oldRank: 0, oldValue: 0, newName: 'X', newRank: 1, newValue: 1, replayed: false });
  yes(!(await p.textContent('.banner')).includes('X'), 'foreign done ignored');
  noErrors(p);
});

await check('craft: text from the server is never parsed as HTML', async () => {
  const p = await open(null, 'craft.html');
  const evil = JSON.parse(JSON.stringify(craftView));
  evil.name = '<img src=x onerror="window.__pwned=1">'; evil.slots[0].name = '<b id=pwn>x</b>';
  await emit(p, 'craftView', evil);
  yes(!(await p.evaluate(() => window.__pwned)), 'no script ran');
  eq(await p.locator('#pwn').count(), 0, 'no element injected');
  eq(await p.textContent('#name'), evil.name, 'shown as plain text');
});

// ---------------------------------------------------------------- fusion
await check('fusion: pick, preview, review, confirm, result', async () => {
  const p = await open(null, 'fusion.html');
  eq((await sent(p)).map(m => m.channel), ['ascension_fusion_ready'], 'first message');
  await emit(p, 'sprites', { mewtwo: PNG });
  await emit(p, 'fusionCandidates', { host: entry('Mewtwo', 'h1'), hostBlock: '' });
  eq(await p.textContent('#hostName'), 'Mewtwo', 'host shown');
  yes((await p.textContent('#result')).includes('Choose a donor'), 'asks for a donor');
  await p.click('#changeDonor');
  const pick = (await sent(p)).find(x => x.channel === 'ascension_fusion_pick');
  eq([pick.hostRole, pick.hostId, pick.donorId], [false, 'h1', ''], 'pick request');
  await emit(p, 'fusionPickList', { hostRole: false, entries: [entry('Mew', 'd1', 'epic'), entry('Ditto', 'd2', 'common', false, false, 'Needs Epic rarity or better.')] });
  eq(await p.locator('.tile').count(), 1, 'party tab shows party entries only (d2 is in the PC)');
  await p.click('#tabPc');
  eq(await p.locator('.tile.off').count(), 1, 'PC tab shows the blocked one greyed');
  await p.click('#tabParty');
  await shot(p, 'fusion-picker');
  await p.locator('.tile').first().click();
  yes((await sent(p)).some(x => x.channel === 'ascension_fusion_preview' && x.donorId === 'd1'), 'picking asks for a preview');
  await emit(p, 'fusionDetail', { hostId: 'h1', donorId: 'd1', block: '', name: 'Mewmew', fromUniques: 'Overmind + Overmind', harmony: 'mild', benefit: 70, drawback: 30, blurb: 'b',
    core: 'c', clutch: 'cl', drawbackText: 'd', twistName: 'T', twistText: 'tt', riderText: 'r', hostType: 'psychic', donorType: 'psychic', price: [600, 30, 10, 1], balances: [1, 2, 3, 4],
    hostRevision: 3, donorRevision: 4, walletRevision: 5 }, true);
  await shot(p, 'fusion-preview');
  yes((await p.textContent('#result')).includes('Mewmew'), 'preview shown');
  await p.click('#fuse');                     // Review fusion
  yes((await p.textContent('#fuse')).includes('Fuse for good'), 'second step');
  await p.click('#fuse');
  const c = (await sent(p)).find(x => x.channel === 'ascension_fusion_confirm');
  eq([c.hostId, c.donorId, c.hostRevision, c.donorRevision, c.walletRevision], ['h1', 'd1', 3, 4, 5], 'confirm body');
  await emit(p, 'fusionResult', { operationId: c.operationId, ok: true, message: '', name: 'Mewmew' });
  yes((await p.textContent('#msg')).includes('became Mewmew'), 'result shown');
  yes((await sent(p)).some(x => x.channel === 'ascension_fusion_refresh'), 'host refreshed after');
  noErrors(p);
});

await check('fusion: a host that cannot host shows why, swap needs a valid host', async () => {
  const p = await open(null, 'fusion.html');
  await emit(p, 'fusionCandidates', { host: entry('Pidgey', 'h9', 'common', true, false), hostBlock: 'Only a Mythic Pokémon can be the host.' });
  yes((await p.textContent('#result')).includes('Only a Mythic'), 'reason shown');
  eq(await p.isDisabled('#swap'), true, 'swap disabled');
  noErrors(p);
});

// ---------------------------------------------------------------- inspect
await check('inspect: owned view, affixes, upgrade button; enemy view, scout', async () => {
  const p = await open(null, 'inspect.html');
  eq((await sent(p)).map(m => m.channel), ['ascension_inspect_ready'], 'first message');
  await emit(p, 'sprites', { arcanine: PNG });
  await emit(p, 'inspectOwned', { pokemonId: 'a1', name: 'Arcanine', species: 'Arcanine', pending: 2, attunement: 9, detail: detail(), extra: { speciesId: 'cobblemon:arcanine', aspects: [], origin: 'wild_capture', initialRarity: 'rare', highestLevel: 40, milestones: 3, prefixCap: 2, suffixCap: 2, craftLocked: false } });
  eq(await p.textContent('#name'), 'Arcanine', 'name');
  yes((await p.textContent('#grade')).includes('EPIC'), 'grade');
  yes((await p.textContent('#bottom')).includes('2 upgrades pending'), 'bottom line');
  yes(await p.locator('#rows .row').count() >= 2, 'unique + affix rows');
  await shot(p, 'inspect-owned');
  yes((await p.textContent('#upgrade')).includes('Upgrade (2)'), 'upgrade label');
  await p.click('#upgrade');
  yes((await sent(p)).some(x => x.channel === 'ascension_craft_open' && x.pokemonId === 'a1'), 'upgrade opens craft');
  noErrors(p);
});

await check('inspect: enemy scouting with a hidden and a revealed enemy', async () => {
  const p = await open(null, 'inspect.html');
  await emit(p, 'inspectScout', { encounterId: 'enc-1', scouters: 2, entries: [{ subjectId: 's1', name: 'Growlithe' }, { subjectId: 's2', name: 'Houndoom', detail: detail() }] });
  eq(await p.textContent('#name'), 'Growlithe', 'first enemy');
  yes((await p.textContent('#grade')).includes('RARITY ?'), 'unscouted hides rarity');
  await p.click('#scout');
  const u = (await sent(p)).find(x => x.channel === 'ascension_inspect_use');
  eq([u.encounterId, u.subjectId], ['enc-1', 's1'], 'scout request');
  await p.click('#next');
  eq(await p.textContent('#name'), 'Houndoom', 'next enemy');
  yes((await p.textContent('#grade')).includes('EPIC'), 'revealed shows rarity');
  eq(await p.isDisabled('#scout'), true, 'already scouted');
  await shot(p, 'inspect-enemy');
  noErrors(p);
});

// ---------------------------------------------------------------- hud
await check('hud: capture card, scouting panel, buttons, closes itself when idle', async () => {
  const p = await open(null, 'hud.html', 1000, 600);
  eq((await sent(p)).map(m => m.channel), ['ascension_hud_ready'], 'first message');
  await emit(p, 'sprites', { arcanine: PNG });
  await emit(p, 'hudReveal', { pokemonId: 'a1', name: 'Arcanine', speciesId: 'cobblemon:arcanine', aspects: [], detail: detail() });
  await emit(p, 'hudScout', { encounterId: 'enc-1', scouters: 3, entries: [{ subjectId: 's1', name: 'Growlithe' }, { subjectId: 's2', name: 'Houndoom', detail: detail() }] });
  await emit(p, 'hudTeam', [{ id: 'a1', name: 'Arcanine', speciesId: 'cobblemon:arcanine', rarityId: 'epic', level: 40 }]);
  yes(await p.locator('.card').count() === 1, 'toast shown');
  yes(await p.locator('#panel.on').count() === 1, 'panel shown');
  await shot(p, 'hud');
  await p.locator('#opp button', { hasText: 'Scout' }).click();
  yes((await sent(p)).some(x => x.channel === 'ascension_inspect_use' && x.subjectId === 's1'), 'scout from the overlay');
  await p.locator('#team .n').click();
  yes((await sent(p)).some(x => x.channel === 'ascension_inspect_open' && x.kind === 'owned' && x.id === 'a1'), 'team row opens inspect');
  // the encounter ends: the panel hides; the toast leaves; then the page closes itself
  await emit(p, 'hudScout', { encounterId: '', scouters: 3, entries: [] });
  yes(await p.locator('#panel.on').count() === 0, 'panel hidden when the encounter ends');
  noErrors(p);
});

await check('hud: closes itself after the last card has been shown', async () => {
  const p = await open(null, 'hud.html');
  await emit(p, 'hudScout', { encounterId: '', scouters: 0, entries: [] });
  await p.waitForTimeout(3200);
  const ch = (await sent(p)).map(m => m.channel);
  yes(ch.includes('ascension_hud_closed') && ch.includes('close'), 'idle overlay closes: ' + ch.join(','));
});

await check('pages degrade without WebGUI (no window.webgui): no crash', async () => {
  const ctx = await browser.newContext(); const p = await ctx.newPage(); const errors = [];
  p.on('pageerror', e => errors.push(e.message));
  for (const f of ['craft.html', 'fusion.html', 'inspect.html', 'hud.html']) await p.goto(base + f);
  eq(errors, [], 'errors');
});

await browser.close(); server.close();
for (const [s, n] of results) console.log(s === 'ok' ? '  ok   ' : '  FAIL ', n);
console.log(failed ? `\n${failed} FAILED` : `\nall ${results.length} page checks passed`);
process.exit(failed ? 1 : 0);
