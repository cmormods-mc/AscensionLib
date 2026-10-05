const {chromium}=require('C:/Users/Facel/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{
 const browser=await chromium.launch({headless:true});
 const page=await browser.newPage({viewport:{width:780,height:900},colorScheme:'dark'});
 const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.goto('file:///L:/Codex/CobbleAscend/design/reveal/preview.html');
 const frame=page.frameLocator('iframe');
 await frame.locator('.px-row').first().waitFor();
 await page.waitForTimeout(1200);
 assert.equal(await frame.locator('.px-row').count(),6);
 await page.screenshot({path:'L:/Codex/CobbleAscend/work/pixel-reveal-desktop.png',fullPage:true});
 await frame.locator('#px-motion').check();
 for(const [tier,count] of Object.entries({Common:1,Uncommon:2,Rare:3,Epic:4,Legendary:5,Mythical:6})){
  await frame.locator('#px-tier').selectOption(tier);
  assert.equal(await frame.locator('.px-row.px-shown').count(),count);
  assert.equal(await frame.locator('.px-game').getAttribute('data-phase'),'settled');
 }
 await frame.locator('.px-row button').first().click();
 assert.equal(await frame.locator('.px-row-detail').first().isVisible(),true);
 await frame.locator('.px-row button').first().click();
 await frame.locator('#px-motion').uncheck();
 await frame.locator('.px-replay').click();
 assert.equal(await frame.locator('.px-game').getAttribute('data-phase'),'gather');
 await frame.locator('.px-primary').click();
 assert.equal(await frame.locator('.px-game').getAttribute('data-phase'),'settled');
 await frame.locator('.px-replay').click();
 await page.waitForTimeout(3300);
 assert.equal(await frame.locator('.px-game').getAttribute('data-phase'),'settled');
 assert.equal(await frame.locator('.px-row.px-shown').count(),6);
 await frame.locator('.px-primary').click();
 assert.equal(await frame.locator('.px-game').getAttribute('data-phase'),'complete');
 await frame.locator('.px-primary').click();
 await frame.locator('.px-primary').press('Escape');
 assert.equal(await frame.locator('.px-game').getAttribute('data-phase'),'settled');
 for(const width of [780,560,360,320]){
  await page.setViewportSize({width,height:1100});await page.waitForTimeout(400);
  const geometry=await frame.locator('#ca-pixel-reveal').evaluate(root=>{
   const card=root.querySelector('.px-window').getBoundingClientRect(),game=root.querySelector('.px-game').getBoundingClientRect();
   return {scroll:document.documentElement.scrollWidth,width:document.documentElement.clientWidth,cardRight:card.right,gameRight:game.right,overlaps:[...root.querySelectorAll('.px-row button')].some(b=>b.querySelector('.px-row-name').getBoundingClientRect().right>b.querySelector('.px-rank').getBoundingClientRect().left)};
  });
  assert.ok(geometry.scroll<=geometry.width+1,JSON.stringify(geometry));assert.ok(geometry.cardRight<=geometry.gameRight,JSON.stringify(geometry));assert.equal(geometry.overlaps,false);
 }
 await page.screenshot({path:'L:/Codex/CobbleAscend/work/pixel-reveal-mobile.png',fullPage:true});
 assert.deepEqual(errors,[]);
 console.log('PASS: six tiers; affix details; complete/replay; Escape/skip; full timed sequence; reduced motion; 780/560/360/320px geometry; no runtime errors.');
 console.log('Sprite loaded:',await frame.locator('.px-portrait img').evaluate(i=>({loaded:i.complete&&i.naturalWidth>0,width:i.naturalWidth,height:i.naturalHeight})));
 console.log('Font loaded:',await frame.locator('#ca-pixel-reveal').evaluate(()=>document.fonts.check('14px "Pixelify Sans"')));
 await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
