const {chromium}=require('C:/Users/Facel/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true});const page=await browser.newPage({viewport:{width:780,height:1000},colorScheme:'dark'});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('file:///L:/Codex/CobbleAscend/design/affix-upgrades/preview.html');const f=page.frameLocator('iframe');await f.locator('.aw-review').waitFor();await page.waitForTimeout(1000);
 await page.screenshot({path:'L:/Codex/CobbleAscend/work/affix-ui-desktop.png',fullPage:true});
 await f.locator('.aw-review').click();await f.locator('.aw-cancel').click();assert.equal(await f.locator('.aw-credit strong').innerText(),'2');
 await f.locator('.aw-review').click();await f.locator('.aw-confirm').click();assert.equal(await f.locator('.aw-credit strong').innerText(),'1');assert.match(await f.locator('.aw-row').first().innerText(),/II/);assert.match(await f.locator('.aw-feedback').innerText(),/Affix upgraded/);
 const roll=Number((await f.locator('.aw-row-value').first().innerText()).replace(/\D/g,''));assert.ok(roll>=9&&roll<=12);
 await f.locator('.aw-review').click();await f.locator('.aw-confirm').press('Escape');assert.equal(await f.locator('.aw-credit strong').innerText(),'1');
 await f.locator('[data-mode="reforge"]').click();await f.locator('.aw-review').click();await f.locator('.aw-confirm').click();assert.equal(await f.locator('.aw-credit strong').innerText(),'1');assert.match(await f.locator('.aw-row').first().innerText(),/II/);assert.equal(await f.locator('.aw-wallet').innerText(),'30 Dust · 1 Facets');
 await f.locator('.aw-review').click();await f.locator('.aw-confirm').click();assert.equal(await f.locator('.aw-review').isDisabled(),true);
 await f.locator('.aw-later').click();await f.locator('.aw-reopen').click();assert.equal(await f.locator('.aw-credit strong').innerText(),'1');
 await f.locator('#aw-scenario').selectOption('empty');assert.equal(await f.locator('.aw-review').isDisabled(),true);
 await f.locator('#aw-scenario').selectOption('max');assert.equal(await f.locator('.aw-review').isDisabled(),true);await f.locator('[data-slot="1"]').click();assert.equal(await f.locator('.aw-review').isEnabled(),true);
 await f.locator('#aw-scenario').selectOption('pending');
 for(const width of [780,600,360,320]){await page.setViewportSize({width,height:1200});await page.waitForTimeout(100);const geo=await f.locator('#ca-affix-workshop').evaluate(root=>({width:document.documentElement.clientWidth,scroll:document.documentElement.scrollWidth,overlaps:[...root.querySelectorAll('.aw-row')].some(r=>r.querySelector('.aw-row-name').getBoundingClientRect().right>r.querySelector('.aw-row-value').getBoundingClientRect().left)}));assert.ok(geo.scroll<=geo.width+1,JSON.stringify(geo));assert.equal(geo.overlaps,false,JSON.stringify(geo));}
 await f.locator('.aw-review').click();await page.screenshot({path:'L:/Codex/CobbleAscend/work/affix-ui-mobile.png',fullPage:true});assert.deepEqual(errors,[]);
 console.log('PASS: upgrade roll/credit, cancel/Escape, rank-preserving reforge, exhausted materials, defer/reopen, no-credit and max-rank states, 780/600/360/320px layouts.');
 console.log('Sprite:',await f.locator('.aw-sprite img').evaluate(i=>i.complete&&i.naturalWidth>0));await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
