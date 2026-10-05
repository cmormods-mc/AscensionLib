const {chromium}=require('C:/Users/Facel/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:860,height:1000},colorScheme:'light'});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));page.on('console',m=>{if(m.type()==='error')errors.push(m.text())});
 await page.goto('file:///L:/Codex/CobbleAscend/design/inspect/preview.html');
 await page.waitForSelector('.win');await page.waitForTimeout(500);
 const text=async s=>page.locator(s).innerText();
 const shot=async n=>{await page.waitForTimeout(1300);await page.screenshot({path:'L:/Codex/CobbleAscend/work/inspect-'+n+'.png',fullPage:true})};
 const asc='.panel.asc';
 // own
 assert.equal(await text('.name'),'Arcanine');assert.match(await text('.rarity'),/Rare/);
 assert.equal(await page.locator(asc+' .row').count(),3);
 assert.match(await text(asc),/2 upgrades ready/);
 assert.equal(await page.locator(asc+' .row').first().locator('.pips i.on').count(),2);
 assert.match(await text('.dex'),/No\. 059/);
 await shot('own');
 // tower enemy unscouted
 await page.selectOption('#scenario','trial');
 assert.equal(await text('.name'),'Gyarados');assert.match(await text('.rarity'),/Rarity \?/);assert.equal(await text('.lv'),'Lv ??');
 assert.equal(await page.locator('.row.q').count(),3);
 assert.match(await text('.panel:nth-child(1)'),/\?\?/);assert.doesNotMatch(await text('.panel:nth-child(1)'),/Water|Flying|125/);
 assert.match(await text(asc),/Granted by: Tower modifier/);
 assert.equal(await page.locator('.stamp').count(),0);
 await shot('trial-unscouted');
 await page.click('#scout');
 assert.match(await text('.rarity'),/Epic/);assert.equal(await text('.lv'),'Lv 55');
 assert.equal(await page.locator(asc+' .row').count(),4);assert.equal(await page.locator('.row.q').count(),0);
 assert.match(await text('.panel:nth-child(1)'),/Water/);assert.match(await text('.panel:nth-child(1)'),/125/);
 assert.equal(await page.locator('.power').count(),1);
 assert.match(await text('.stamp'),/tower modifier/i);
 await page.locator(asc+' .row').first().click();assert.match(await text('.detail'),/inactive in this build/);
 await shot('trial-scouted');
 await page.click('#forget');assert.equal(await page.locator('.row.q').count(),3);
 // raid boss: same placeholder count (no slot-count leak), unique after scouting
 await page.selectOption('#scenario','boss');
 assert.equal(await page.locator('.row.q').count(),3);assert.equal(await page.locator('.unique').count(),0);
 await page.click('#scout');
 assert.match(await text('.rarity'),/Legendary · Unique/);
 assert.equal(await page.locator(asc+' .row').count(),5);assert.equal(await page.locator('.unique').count(),1);
 assert.match(await text('.unique'),/Ashen Heart/);assert.match(await text('.unique'),/direct move damage is reduced/i);
 assert.equal(await page.locator('.power').count(),3);assert.match(await text('.stamp'),/raid scouting/i);
 await shot('boss-scouted');
 // wild with Scouters
 await page.selectOption('#scenario','wild');
 assert.equal(await text('.name'),'Gengar');assert.match(await text('.rarity'),/Rarity \?/);
 assert.match(await text('.panel:nth-child(1)'),/Ghost/);assert.equal(await text('.lv'),'Lv 22');
 assert.match(await text(asc),/Scouters in bag: 2/);assert.equal(await page.locator('#scout').isEnabled(),true);
 await shot('wild-unscouted');
 await page.click('#scout');
 assert.match(await text('.rarity'),/Rare \(if caught\)/);
 assert.equal(await page.locator(asc+' .row').count(),3);
 for(const row of await page.locator(asc+' .row').all())assert.equal(await row.locator('.pips i.on').count(),1,'wild slots are all rank I');
 assert.match(await text(asc),/If you catch it/);assert.match(await text(asc),/2 upgrades ready/);
 assert.match(await text('.stamp'),/scouter/i);
 assert.equal(await page.inputValue('#bag'),'1');
 await shot('wild-scouted');
 // no Scouter -> disabled
 await page.click('#forget');await page.fill('#bag','0');await page.press('#bag','Tab');
 assert.equal(await page.locator('#scout').isDisabled(),true);assert.match(await text('#scout'),/No Scouter/);
 await page.fill('#bag','2');await page.press('#bag','Tab');
 // pvp
 await page.selectOption('#scenario','pvp');
 assert.match(await text(asc),/switched off in player battles/);assert.equal(await page.locator('#scout').count(),0);
 // entry buttons, escape
 await page.selectOption('#scenario','boss');
 await page.click('#tile-foe .mag');assert.equal(await text('.name'),'Houndoom');
 await page.locator('.mag[data-open="own"]').first().click();assert.equal(await text('.name'),'Arcanine');
 await page.keyboard.press('Escape');assert.equal(await page.locator('.win .head').count(),0);
 await page.click('#reopen');assert.equal(await page.locator('.win .head').count(),1);
 await page.click('#close');assert.equal(await page.locator('.win .head').count(),0);await page.click('#reopen');
 // reduced motion: no reveal class and no idle animation
 await page.check('#reduce');await page.selectOption('#scenario','trial');
 if(await page.locator('#scout').count())await page.click('#scout');else{await page.click('#forget');await page.click('#scout')}
 assert.equal(await page.locator('.reveal').count(),0);
 assert.equal(await page.evaluate(()=>getComputedStyle(document.querySelector('.sprite')).animationName),'none');
 await page.click('#forget');await page.uncheck('#reduce');await page.click('#scout');
 assert.equal(await page.locator('.reveal').count(),1);
 const sprites=await page.locator('img').evaluateAll(i=>i.map(x=>x.complete&&x.naturalWidth>0));assert.ok(sprites.length>=1&&sprites.every(Boolean));
 await page.selectOption('#scenario','boss');if(await page.locator('#scout').count())await page.click('#scout');
 for(const width of [860,600,360,320]){
   await page.setViewportSize({width,height:1200});await page.waitForTimeout(100);
   const geo=await page.evaluate(()=>({w:document.documentElement.clientWidth,s:document.documentElement.scrollWidth,
     overlap:[...document.querySelectorAll('.row')].some(r=>{const a=r.querySelector('.rname'),b=r.querySelector('.val');return a&&b&&a.getBoundingClientRect().right>b.getBoundingClientRect().left+1})}));
   assert.ok(geo.s<=geo.w+1,'horizontal scroll at '+width+' '+JSON.stringify(geo));assert.equal(geo.overlap,false,'overlap at '+width);
 }
 await shot('mobile');
 await page.setViewportSize({width:860,height:1000});await page.emulateMedia({colorScheme:'dark'});await shot('dark');
 assert.deepEqual(errors,[]);
 console.log('PASS');
 await browser.close();
})().catch(e=>{console.error('FAIL',e.message);process.exit(1)});
