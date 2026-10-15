// Draws what each reskinned item's tooltip looks like (a mock of Minecraft's tooltip, with the item art, name, rarity line and lore) from design/currency-items.json.
// usage: node tools/tooltip_preview.mjs out.png
// Needs the same playwright-core and headless Chromium as tools/page_harness.mjs. A preview aid only; nothing in the mod reads it.
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const here = path.dirname(fileURLToPath(import.meta.url));
const root = path.resolve(here, '..');
const require = createRequire(path.resolve(root, '../../CobbleTowers/web/') + path.sep);
const { chromium } = require('playwright-core');
const design = JSON.parse(fs.readFileSync(path.join(root, 'design/currency-items.json'), 'utf8'));
const icons = path.join(root, 'ascensionlib/src/main/resources/web/ascensionlib/icons');
const font = fs.readFileSync(path.join(root, 'ascensionlib/src/main/resources/web/ascensionlib/fonts/Monocraft-latin.woff')).toString('base64');
const b64 = n => fs.readFileSync(path.join(icons, n + '.png')).toString('base64');

const entries = [...Object.entries(design.items), ['ascension_dex', design.dex]];
const card = ([id, spec]) => {
  const r = design.rarities[spec.rarity];
  const lines = [
    `<div class="n" style="color:${spec.color}">${spec.name}</div>`,
    `<div><b style="color:${r.color}">${r.label}</b><span class="k"> · ${spec.kind}</span></div>`,
    ...spec.lore.map(l => `<div class="l">${l}</div>`)
  ].join('');
  return `<div class="cell"><img src="data:image/png;base64,${b64(id)}"><div class="tip">${lines}</div></div>`;
};
const html = `<html><head><style>
@font-face { font-family: M; src: url(data:font/woff;base64,${font}) format('woff'); }
body { margin: 0; padding: 18px; background: #5b7d4a; font: 13px/1.35 M, monospace; width: 1180px; }
.grid { display: grid; grid-template-columns: repeat(3, 1fr); gap: 16px; }
.cell { display: flex; gap: 10px; align-items: flex-start; }
.cell img { width: 64px; height: 64px; image-rendering: pixelated; }
.tip { background: #100010f0; border: 2px solid #3a0a6e; box-shadow: 0 0 0 2px #100010; padding: 6px 9px; color: #fff; min-width: 190px; }
.n { font-size: 14px; } .k { color: #555; } .l { color: #aaa; } .d { color: #555; }
</style></head><body><div class="grid">${entries.map(card).join('')}</div></body></html>`;

const browserDir = path.join(root, 'work/preview-browsers');
const exe = (() => { try { return chromium.executablePath(); } catch { return undefined; } })();
const browser = await chromium.launch({ executablePath: exe && fs.existsSync(exe) ? exe : undefined, headless: true });
const page = await browser.newPage({ viewport: { width: 1220, height: 420 } });
await page.setContent(html);
await page.waitForTimeout(300);
await page.screenshot({ path: process.argv[2] || 'tooltips.png', fullPage: true });
await browser.close();
