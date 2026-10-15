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
  { slotId: 'p1', category: 'prefix', name: 'Burn Boost', condition: 'Burns you inflict deal 50% more damage.', rank: 2, value: 12, bandMin: 10, bandMax: 14, nextMin: 15, nextMax: 19, upgradeBlock: '', refineBlock: '', reforgeBlock: '', reforgePool: 4, perfectBlock: 'Needs rank V', perfectCost: 0, floor: 10, overreachBlock: 'Needs rank V', sealed: false, overreachDust: 60, overreachFacets: 2, overreachChance: 45, overreachMin: 35, overreachMax: 40 },
  { slotId: 's1', category: 'suffix', name: 'Sturdy', condition: '', rank: 5, value: 32, bandMin: 28, bandMax: 32, nextMin: -1, nextMax: -1, upgradeBlock: 'Already at the top rank', refineBlock: 'Needs 20 Dust', reforgeBlock: '', reforgePool: 3, perfectBlock: '', perfectCost: 40, floor: 29, overreachBlock: '', sealed: false, overreachDust: 60, overreachFacets: 2, overreachChance: 45, overreachMin: 35, overreachMax: 40 },
];
const craftView = {
  pokemonId: '00000000-0000-0000-0000-000000000001', name: 'Mewtwo', speciesId: 'cobblemon:mewtwo', aspects: [], level: 52, rarityId: 'mythical', uniqueName: '777',
  pending: 1, nextMilestone: 60, profileRevision: 7, walletRevision: 8, dust: 1234, facets: 40, cores: 3, scouters: 5, marks: 6, refine: price, reforge: price,
  promotion: { toRarity: '', price, attunement: 90, attunementNeeded: 0, block: 'Already Mythical' },
  unique: { currentId: '777', catalysts: 1, fragments: 3, fragmentsNeeded: 5, block: '', options: [
    { id: '777', name: '777', benefit: 'More item rewards.', drawback: 'Lower defence.', current: true },
    { id: 'fury', name: 'Fury', benefit: 'Hits harder.', drawback: 'Takes more damage.', current: false } ] },
  slots, message: '', open: false,
};
const entry = (n, id, rarity = 'mythical', party = true, canHost = true, block = '') =>
  ({ id, name: n, speciesId: 'cobblemon:' + n.toLowerCase(), rarityId: rarity, uniqueName: 'Overmind', inParty: party, canHost, block });
const detail = (slotsExtra = []) => ({ level: 40, types: ['fire', 'flying'], baseStats: [90, 100, 80, 110, 80, 95], rarityId: 'epic', uniqueName: '777',
  slots: [{ category: 'prefix', name: 'Keen Edge', rank: 5, value: 16, min: 12, max: 16, condition: 'Crit chance.', theme: 'Predator' },
          { category: 'prefix', name: 'Momentum', rank: 2, value: 5, min: 3, max: 6, condition: 'Stacks on KOs.', theme: 'Predator' },
          { category: 'suffix', name: 'Wardstone', rank: 1, value: 6, min: 5, max: 10, condition: 'Status ward.', theme: 'Vitality' }, ...slotsExtra],
  resonance: [{ theme: 'Predator', pieces: 2, bonusPercent: 15 }, { theme: 'Vitality', pieces: 1, bonusPercent: 0 }] });

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
// shots are taken over a game-like green so a transparent page shows what the game would show through it
const shot = async (p, n) => {
  if (!shotsDir) return;
  await p.evaluate(() => { document.documentElement.dataset.bg = document.documentElement.style.background; document.documentElement.style.background = 'linear-gradient(#3b6aa8,#4f8a45)'; });
  await p.screenshot({ path: path.join(shotsDir, n + '.png') });
  await p.evaluate(() => { document.documentElement.style.background = document.documentElement.dataset.bg || ''; });
};
const noErrors = p => eq(p.errors, [], 'page errors');

// ---------------------------------------------------------------- craft
await check('craft: asks for its data, renders, upgrade flow, wallet, tabs', async () => {
  const p = await open(null, 'craft.html');
  eq((await sent(p)).map(m => m.channel), ['ascension_craft_ready'], 'first message');
  await emit(p, 'sprites', { mewtwo: PNG });
  await emit(p, 'craftView', craftView);
  eq(await p.textContent('#name'), 'Mewtwo', 'name');
  eq(await p.textContent('#pend'), '1', 'pending');
  eq(await p.locator('.tab').count(), 7, 'tabs');
  eq(await p.locator('#list .row').count(), 2, 'affix rows');
  yes((await p.textContent('#wallet')).includes('1,234'), 'wallet shows dust with separators');
  eq(await p.locator('#wallet img.dot').count(), 7, 'the craft wallet column shows the item icons');
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
  // Perfecting: only the rank V slot (Sturdy) can be polished; its price and the confirm request come from the server's numbers
  await p.locator('.tab', { hasText: 'Perfect' }).click();
  eq(await p.locator('#list .row.off').count(), 1, 'the rank II slot is dimmed for perfecting');
  await p.locator('#list .row', { hasText: 'Sturdy' }).click();
  yes((await p.textContent('#detail')).includes('+30%'), 'shows the new floor (29 -> 30)');
  yes((await p.textContent('#detail')).includes('40 Dust'), 'shows the server price');
  await p.locator('#detail button.primary').click();
  await p.click('#dOk');
  const pm = (await sent(p)).filter(x => x.channel === 'ascension_craft_confirm').pop();
  eq([pm.kind, pm.slotId], ['perfect', 's1'], 'perfect confirm');
  await emit(p, 'craftDone', { operationId: pm.operationId, ok: true, message: '', slotId: 's1', oldName: 'Sturdy', oldRank: 5, oldValue: 29, newName: 'Sturdy', newRank: 5, newValue: 30, replayed: false });
  yes((await p.textContent('.banner')).includes('Floor raised'), 'banner after perfecting');
  // Overreach: the gamble, offered only on a rank V slot at its band top; the risk is spelled out before the confirm
  await p.locator('.tab', { hasText: 'Overreach' }).click();
  eq(await p.locator('#list .row.off').count(), 1, 'the rank II slot is dimmed for overreach');
  await p.locator('#list .row', { hasText: 'Sturdy' }).click();
  yes((await p.textContent('#detail')).includes('45% to win'), 'shows the odds');
  yes((await p.textContent('#detail')).includes('+35–40%'), 'shows the range a win would give');
  await p.locator('#detail button.primary').click();
  yes((await p.textContent('#dBody')).includes('RISKY'), 'the dialog says it is risky');
  await p.click('#dOk');
  const om = (await sent(p)).filter(x => x.channel === 'ascension_craft_confirm').pop();
  eq([om.kind, om.slotId], ['overreach', 's1'], 'overreach confirm');
  await emit(p, 'craftDone', { operationId: om.operationId, ok: true, message: '', slotId: 's1', oldName: 'Sturdy', oldRank: 5, oldValue: 32, newName: 'Sturdy', newRank: 5, newValue: 37, replayed: false });
  yes((await p.textContent('.banner')).includes('Overreached'), 'a win is announced');
  await p.locator('.tab', { hasText: 'Overreach' }).click();
  await p.locator('#list .row', { hasText: 'Sturdy' }).click();
  await p.locator('#detail button.primary').click();
  await p.click('#dOk');
  const om2 = (await sent(p)).filter(x => x.channel === 'ascension_craft_confirm').pop();
  await emit(p, 'craftDone', { operationId: om2.operationId, ok: true, message: '', slotId: 's1', oldName: 'Sturdy', oldRank: 5, oldValue: 32, newName: 'Sturdy', newRank: 5, newValue: 20, replayed: false });
  yes((await p.textContent('.banner')).includes('failed'), 'a loss is announced');
  for (const t of ['Refine', 'Reforge', 'Promote', 'Unique']) { await p.locator('.tab', { hasText: t }).click(); }
  await shot(p, 'craft-unique');
  yes((await p.textContent('#detail')).includes('Benefit'), 'unique tab shows benefit');
  await p.locator('.tab', { hasText: 'Promote' }).click();
  yes((await p.textContent('#detail')).includes('highest rarity'), 'promote at top rarity says so');
  await p.click('#bank');
  yes((await sent(p)).some(x => x.channel === 'ascension_wallet_open'), 'the Wallet button opens the wallet page');
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

// ---------------------------------------------------------------- wallet
await check('wallet: balances with icons, withdraw and deposit requests, result banner', async () => {
  const p = await open(null, 'wallet.html');
  eq((await sent(p)).map(m => m.channel), ['ascension_wallet_ready'], 'first message');
  const lines = [['resonance_dust', 'Resonance Dust', '#d9a441', 1234, 0], ['facet', 'Facet', '#4f9ad9', 40, 64], ['ascension_core', 'Ascension Core', '#c9694f', 3, 0],
    ['unique_fragment', 'Unique Fragment', '#8c63c7', 0, 0], ['unique_catalyst', 'Unique Catalyst', '#b04fa0', 1, 0], ['scouter', 'Scouter', '#6f9a52', 5, 0], ['ascension_sigil', 'Ascension Sigil', '#ffd437', 0, 0]]
    .map(([id, name, color, amount, carried]) => ({ id, name, color, amount, carried }));
  await emit(p, 'walletView', { lines, freeSlots: 12 });
  eq(await p.locator('.line').count(), 7, 'a row per material');
  eq(await p.locator('.line img').count(), 7, 'an icon per material');
  yes((await p.textContent('#lines')).includes('1,234'), 'balance with separators');
  yes((await p.textContent('#free')).includes('12 free'), 'free slots shown');
  // the facet row: take 8
  const row = p.locator('.line', { hasText: 'Facet' }).first();
  await row.locator('input').fill('8');
  await row.locator('button', { hasText: 'Take' }).click();
  const w = (await sent(p)).find(x => x.channel === 'ascension_wallet_withdraw');
  eq([w.material, w.amount], ['facet', 8], 'withdraw request');
  await emit(p, 'walletResult', { ok: true, message: 'Withdrew 8 Facet as voucher items.' });
  yes((await p.textContent('#banner')).includes('Withdrew 8'), 'result shown');
  // only a row with vouchers offers Deposit; Deposit all is enabled
  eq(await p.locator('.line button', { hasText: 'Deposit' }).count(), 1, 'one deposit button');
  eq(await p.isDisabled('#depositAll'), false, 'deposit all enabled');
  await p.locator('.line button', { hasText: 'Deposit' }).click();
  const d = (await sent(p)).find(x => x.channel === 'ascension_wallet_deposit');
  eq(d.material, 'facet', 'deposit request names the material');
  await p.click('#depositAll');
  yes((await sent(p)).filter(x => x.channel === 'ascension_wallet_deposit').pop().material === undefined, 'deposit all names none');
  await emit(p, 'walletResult', { ok: false, message: 'You need 2 free inventory slots for that.' });
  yes((await p.textContent('#banner')).includes('free inventory'), 'a refusal is shown');
  noErrors(p);
});

await check('wallet: a bound material (Tower Mark) cannot be taken out', async () => {
  const p = await open(null, 'wallet.html');
  await emit(p, 'walletView', { feePercent: 3, catalystNeeded: 100, lines: [{ id: 'unique_fragment', name: 'Unique Fragment', color: '#8c63c7', amount: 63, carried: 0, tradable: true }, { id: 'facet', name: 'Facet', color: '#4f9ad9', amount: 9, carried: 0, tradable: true },
    { id: 'tower_mark', name: 'Tower Mark', color: '#e8e1c9', amount: 4, carried: 0, tradable: false }], freeSlots: 5 });
  const mark = p.locator('.line', { hasText: 'Tower Mark' }).first();
  eq(await mark.locator('button').first().isDisabled(), true, 'the Mark has no Take');
  yes((await mark.textContent()).includes('Bound'), 'it says Bound');
  eq(await p.locator('.line', { hasText: 'Facet' }).first().locator('button').first().isDisabled(), false, 'a tradable material can be taken');
  yes((await p.textContent('#cat')).includes('63 of 100'), 'the Catalyst progress shows fragments toward the next one');
  const fr = p.locator('.line', { hasText: 'Facet' }).first();
  await fr.locator('input').fill('9');
  yes((await fr.textContent()).includes('fee +1 = 10') && (await fr.textContent()).includes('you have 9'), 'the fee preview shows the total and warns when it is more than you have');
  await fr.locator('input').fill('5');
  yes((await fr.textContent()).includes('fee +1 = 6') && !(await fr.textContent()).includes('you have'), 'and no warning when it fits');
  noErrors(p);
});

// ---------------------------------------------------------------- completion
await check('completion: banner, pile counted up, crown rolled one at a time, collect closes', async () => {
  const p = await open(null, 'completion.html', 900, 700);
  eq((await sent(p)).map(m => m.channel), ['ascension_completion_ready'], 'asks for its data');
  const view = { tier: 'mythic', tierName: 'MYTHIC', score: 9, cycle: 1, cards: 5, flawless: true, attunement: 3, preview: false, tiers: [{ id: 'bronze', name: 'BRONZE', from: 0 }, { id: 'silver', name: 'SILVER', from: 2 }, { id: 'gold', name: 'GOLD', from: 5 }, { id: 'mythic', name: 'MYTHIC', from: 8 }],
    pile: [{ id: 'resonance_dust', name: 'Resonance Dust', color: '#d9a441', amount: 1800 }, { id: 'tower_mark', name: 'Tower Mark', color: '#e8e1c9', amount: 5 }],
    crowns: [{ kind: 'core', id: 'ascension_core', label: 'Ascension Core', color: '#c9694f', amount: 1, big: true },
      { kind: 'armor', id: '', label: 'Tower armor piece', color: '#c9d4e0', amount: 1, big: false }] };
  await emit(p, 'completionView', view);
  yes((await p.textContent('#banner')).includes('MYTHIC COMPLETION'), 'tier banner');
  yes((await p.textContent('#sub')).includes('Score 9'), 'score line');
  await p.waitForSelector('.pl.on', { timeout: 4000 });
  await p.waitForFunction(() => document.querySelectorAll('.pl').length === 2, null, { timeout: 4000 });
  await p.waitForFunction(() => document.querySelector('.pl .amt') && document.querySelector('.pl .amt').textContent === '+1,800', null, { timeout: 4000 });
  await p.waitForFunction(() => document.querySelectorAll('.cr').length === 2 && !document.querySelector('.cr.spin'), null, { timeout: 9000 });
  yes((await p.textContent('#crowns')).includes('Ascension Core'), 'the core is revealed');
  eq(await p.locator('.cr.big').count(), 1, 'only the headline item glows');
  yes((await p.textContent('#crowns')).includes('Handed over by the Tower'), 'a Tower-owned item says who hands it over');
  eq(await p.locator('#meter .seg').count(), 4, 'a meter segment per tier');
  eq(await p.locator('#meter .seg.on').count(), 4, 'a score of 9 lights them all');
  await p.click('#share');
  yes((await sent(p)).some(x => x.channel === 'ascension_completion_share'), 'Share asks the server for the chat line');
  eq(await p.isDisabled('#share'), true, 'and cannot be pressed twice');
  await p.click('#done');
  yes((await sent(p)).some(x => x.channel === 'close'), 'Collect closes the page');
  noErrors(p);
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
    core: 'c', clutch: 'cl', drawbackText: 'd', twistName: 'T', twistText: 'tt', riderText: 'r', hostType: 'psychic', donorType: 'psychic', price: [600, 30, 10, 1, 5], balances: [1, 2, 3, 4, 6],
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
  yes((await p.textContent('#reso')).includes('PREDATOR 2 · +15%'), 'resonance tier shown');
  yes((await p.textContent('#rows')).includes('MAX'), 'a perfected rank V slot is marked');
  yes((await p.textContent('#rows')).includes('Predator'), 'theme tag on the affix row');
  await shot(p, 'inspect-owned');
  yes((await p.textContent('#upgrade')).includes('Upgrade (2)'), 'upgrade label');
  await p.click('#upgrade');
  yes((await sent(p)).some(x => x.channel === 'ascension_craft_open' && x.pokemonId === 'a1'), 'upgrade opens craft');
  noErrors(p);
});

await check('inspect: a Pokemon with no profile offers an Ascension Sigil, armed by a first click and spent by a second', async () => {
  const p = await open(null, 'inspect.html');
  await emit(p, 'sprites', { arcanine: PNG });
  const extra = { speciesId: 'cobblemon:arcanine', aspects: [], origin: '', initialRarity: '', highestLevel: 0, milestones: 0, prefixCap: 0, suffixCap: 0, craftLocked: false };
  await emit(p, 'inspectOwned', { pokemonId: 'b2', name: 'Arcanine', species: 'Arcanine', pending: 0, attunement: 0, detail: null, extra, sigils: 0 });
  eq(await p.isVisible('#sigil'), true, 'the button shows for a bare Pokemon');
  eq(await p.isDisabled('#sigil'), true, 'and is disabled with no Sigil');
  eq(await p.isVisible('#upgrade'), false, 'no Upgrade without a profile');
  await emit(p, 'inspectOwned', { pokemonId: 'b2', name: 'Arcanine', species: 'Arcanine', pending: 0, attunement: 0, detail: null, extra, sigils: 2 });
  yes((await p.textContent('#sigil')).includes('(2)'), 'it shows how many Sigils you have');
  await p.click('#sigil');
  yes((await p.textContent('#sigil')).includes('Click again'), 'the first click only arms it');
  yes(!(await sent(p)).some(x => x.channel === 'ascension_inspect_sigil'), 'nothing is sent yet');
  await p.click('#sigil');
  const m = (await sent(p)).find(x => x.channel === 'ascension_inspect_sigil');
  eq(m && m.pokemonId, 'b2', 'the second click asks the server, naming the Pokemon');
  await emit(p, 'inspectOwned', { pokemonId: 'b2', name: 'Arcanine', species: 'Arcanine', pending: 0, attunement: 0, detail: detail(), extra, sigils: 1 });
  eq(await p.isVisible('#sigil'), false, 'once it has a profile the button is gone');
  noErrors(p);
});

await check('inspect: enemy scouting with a hidden and a revealed enemy', async () => {
  const p = await open(null, 'inspect.html');
  await emit(p, 'sprites', { growlithe: PNG });
  await emit(p, 'inspectScout', { encounterId: 'enc-1', scouters: 2, entries: [{ subjectId: 's1', name: 'Growlithe', speciesId: 'cobblemon:growlithe' }, { subjectId: 's2', name: 'Houndoom', detail: detail() }] });
  eq(await p.textContent('#name'), 'Growlithe', 'first enemy');
  yes(await p.locator('#art img').count() === 1, 'a scouted enemy shows its picture when the server sends the species');
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

await check('hud: bleed badges show side, stacks and turns, stay open while anyone bleeds, and clear', async () => {
  const p = await open(null, 'hud.html');
  await emit(p, 'hudBleed', [{ name: 'Growlithe', side: 'ally', stacks: 2, turnsLeft: 3 }, { name: 'Houndoom', side: 'foe', stacks: 1, turnsLeft: 1 }]);
  eq(await p.locator('.bleed').count(), 2, 'a badge per bleeding Pokemon');
  yes((await p.locator('.bleed').first().textContent()).includes('Growlithe') && (await p.locator('.bleed').first().textContent()).includes('BLEED x2'), 'name and stacks');
  yes((await p.locator('.bleed.foe').textContent()).includes('1 turn') && !(await p.locator('.bleed.foe').textContent()).includes('1 turns'), 'singular turn');
  await p.waitForTimeout(3200);
  yes(!(await sent(p)).some(m => m.channel === 'close'), 'the overlay stays open while a bleed is on');
  await emit(p, 'hudBleed', []);
  eq(await p.locator('.bleed').count(), 0, 'cleared');
  await p.waitForTimeout(3200);
  yes((await sent(p)).some(m => m.channel === 'close'), 'then it closes itself');
  noErrors(p);
});

await check('pages degrade without WebGUI (no window.webgui): no crash', async () => {
  const ctx = await browser.newContext(); const p = await ctx.newPage(); const errors = [];
  p.on('pageerror', e => errors.push(e.message));
  for (const f of ['craft.html', 'fusion.html', 'inspect.html', 'hud.html', 'wallet.html']) await p.goto(base + f);
  eq(errors, [], 'errors');
});

await browser.close(); server.close();
for (const [s, n] of results) console.log(s === 'ok' ? '  ok   ' : '  FAIL ', n);
console.log(failed ? `\n${failed} FAILED` : `\nall ${results.length} page checks passed`);
process.exit(failed ? 1 : 0);
