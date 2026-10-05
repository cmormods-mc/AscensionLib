const {chromium}=require('C:/Users/Facel/.cache/codex-runtimes/codex-primary-runtime/dependencies/node/node_modules/playwright');
const assert=require('node:assert/strict');
(async()=>{
  const browser=await chromium.launch({headless:true});
  const page=await browser.newPage({viewport:{width:780,height:940},colorScheme:'dark'});
  const errors=[];page.on('pageerror',e=>errors.push(e.message));
  await page.goto('file:///L:/Codex/CobbleAscend/work/capture-reveal-preview.html');
  const frame=page.frameLocator('iframe');
  await frame.locator('#ac-rarity-label').waitFor();
  assert.match(await frame.locator('#ac-rarity-label').innerText(),/Mythic/i);
  assert.equal(await frame.locator('.ac-affix').count(),6);
  await frame.locator('#ac-motion').check();
  for(const [rarity,count] of Object.entries({Common:1,Uncommon:2,Rare:3,Epic:4,Legendary:5,Mythical:6})){
    await frame.locator('#ac-rarity-select').selectOption(rarity);
    assert.equal(await frame.locator('.ac-affix').count(),count);
  }
  await frame.locator('#ac-inspect').click();
  assert.equal(await frame.locator('.ac-details dt').count(),6);
  await frame.locator('.ac-back-button').click();
  await frame.locator('#ac-continue').click();
  assert.equal(await frame.locator('.ac-complete').isVisible(),true);
  await frame.locator('#ac-again').click();
  await frame.locator('#ac-motion').uncheck();
  await frame.locator('.ac-top .ac-replay').click();
  assert.equal(await frame.locator('#ac-continue').innerText(),'Skip reveal');
  await frame.locator('#ac-continue').click();
  await page.waitForTimeout(1000);
  await page.screenshot({path:'L:/Codex/CobbleAscend/work/capture-reveal-desktop.png',fullPage:true});
  for(const width of [780,360,320]){
    await page.setViewportSize({width,height:980});
    const geometry=await frame.locator('#ascend-capture-preview').evaluate(root=>{
      const rows=[...root.querySelectorAll('.ac-affix')];
      const last=rows.at(-1).getBoundingClientRect();
      const footer=root.querySelector('.ac-card-foot').getBoundingClientRect();
      return {last:last.bottom,footer:footer.top,scroll:document.documentElement.scrollWidth,width:document.documentElement.clientWidth};
    });
    assert.ok(geometry.last<geometry.footer,JSON.stringify(geometry));
    assert.ok(geometry.scroll<=geometry.width+1,JSON.stringify(geometry));
  }
  await page.screenshot({path:'L:/Codex/CobbleAscend/work/capture-reveal-mobile.png',fullPage:true});
  assert.deepEqual(errors,[]);
  console.log('Passed: six rarities, inspect/back, continue/replay, animation skip, 780/360/320px layout, zero script errors.');
  console.log('Portrait loaded:',await frame.locator('.ac-portrait img').evaluate(img=>img.complete&&img.naturalWidth>0));
  await browser.close();
})().catch(e=>{console.error(e);process.exit(1)});
