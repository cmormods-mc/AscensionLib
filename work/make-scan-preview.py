from pathlib import Path
import re
src=Path('L:/Codex/CobbleAscend/design/inspect/preview.html').read_text(encoding='utf-8')
css=re.search(r'<style>(.*?)</style>',src,re.S)[1]
css=css.replace(':root','#ascension-scan').replace('html{background:var(--page-bg)}','').replace('body{','#ascension-scan{')
css=re.sub(r'(^|})(\s*)([^@{}][^{}]*)(\{)',lambda m:m[1]+m[2]+', '.join(s.strip() if '#ascension-scan' in s or s.strip().startswith(('from','to','0%','50%','15%','60%','100%')) else '#ascension-scan '+s.strip() for s in m[3].split(','))+m[4],css)
body=re.search(r'<body>(.*?)</body>',src,re.S)[1]
body=body.replace('id="app"','id="ascension-scan"')
body=re.sub(r'  <h1>.*?</p>','',body,count=1,flags=re.S)
body=re.sub(r'  <div class="entry".*?<div class="game">','  <div class="game">',body,count=1,flags=re.S)
body=re.sub(r'  <p class="legend">.*?</p>','',body,flags=re.S)
body=body.replace('<label><input type="checkbox" id="reduce">','<label><input type="checkbox" id="sound"> Sound</label><label><input type="checkbox" id="reduce">')
body=body.replace("var state={scenario:'own'","var state={scenario:'wild'")
body=body.replace("var $=function(id){return document.getElementById(id)};","var root=document.getElementById('ascension-scan'); var $=function(id){return root.querySelector('#'+id)};")
body=body.replace('document.querySelectorAll(', 'root.querySelectorAll(').replace('document.querySelector(', 'root.querySelector(').replace("document.addEventListener('click'","root.addEventListener('click'")
body=body.replace('document.body.classList','root.classList')
body=body.replace("$('foe-name').textContent=SCENARIOS[state.foe].name", "void 0")
body=body.replace("$('scenario').value=state.scenario;render()", "$('scenario').value=state.scenario;render()")
body=body.replace("var scouted=s.kind==='own'||state.scouted[key];","var scouted=s.kind==='own'||(state.scouted[key]&&!scanning);")
body=body.replace("(scoutable&&state.scouted[key]);","(scoutable&&state.scouted[key]&&!scanning);")
body=body.replace("scoutable&&state.scouted[key]?'<div", "scoutable&&state.scouted[key]&&!scanning?'<div")
body=body.replace("var src=SOURCE[key];", "if(scanning)return '<div class=\"actions\"><button class=\"btn primary\" id=\"skip\">Skip scan</button><span class=\"note\" id=\"scan-phase\">Locking signal…</span></div>'; var src=SOURCE[key];")
body=body.replace("var out='<div class=\"scan\">';", "var out='<div class=\"scan'+(scanning?' scanning':'')+'\"><div class=\"scan-beam\" aria-hidden=\"true\"></div>';")
body=body.replace("state.scouted[k]=true;state.reveal=!reduced();state.open=null;", "state.scouted[k]=true;state.open=null;startScan();return;")
body=body.replace("if(t.id==='forget')", "if(t.id==='skip'){finishScan();return} if(t.id==='replay'){startScan();return} if(t.id==='forget')")
body=body.replace("'<button class=\"btn link\" id=\"forget\">Reset scouting</button>'", "'<button class=\"btn link\" id=\"replay\">Replay scan</button>'")
body=body.replace("if(e.key==='Escape'&&!state.closed){", "if(e.key==='Escape'&&scanning){finishScan();return} if(e.key==='Escape'&&!state.closed){")
body=body.replace("state.scenario=this.value;", "cancelScan();state.scenario=this.value;")
body=body.replace("root.classList.toggle('reduced',this.checked);", "root.classList.toggle('reduced',this.checked);if(this.checked&&scanning)finishScan();")
body=body.replace("if(t.id==='close'){state.closed=true;", "if(t.id==='close'){cancelScan();state.closed=true;")
body=body.replace("'<div class=\"sprite\"><img src=\"assets/'+s.sprite+'.png\"", "'<div class=\"sprite\"><img src=\"assets/'+s.sprite+'.png\"")
# Resolve only the actual sprite resource expression to a pinned upstream CDN.
body=body.replace("assets/'+s.sprite+'.png", "https://cdn.jsdelivr.net/gh/Howlite-UI/CobblemonCards@c02aafb50d5915b83dafa9af28ddebd58be24b5e/common/src/main/resources/assets/cobblemon-cards/textures/item/cards/pokemon/entity_icon/'+String(s.dex).padStart(4,'0')+'_'+s.sprite+'/'+s.sprite+'.png")
extra='''
var scanning=false,timer=0,started=0,audio=null,voices=[],lastStage=-1;
function chime(pitch){if(!$('sound').checked)return;try{audio=audio||new(window.AudioContext||window.webkitAudioContext)();audio.resume();var o=audio.createOscillator(),g=audio.createGain();o.type='sine';o.frequency.value=660*pitch;g.gain.setValueAtTime(.0001,audio.currentTime);g.gain.exponentialRampToValueAtTime(.12,audio.currentTime+.012);g.gain.exponentialRampToValueAtTime(.0001,audio.currentTime+.32);o.connect(g);g.connect(audio.destination);o.start();o.stop(audio.currentTime+.34);voices.push(o)}catch(e){}}
function cancelScan(){clearTimeout(timer);scanning=false;voices.forEach(function(o){try{o.stop()}catch(e){}});voices=[]}
function finishScan(){cancelScan();state.reveal=!reduced();render();}
function startScan(){cancelScan();if(reduced()){render();return}scanning=true;started=performance.now();lastStage=-1;render();chime(.65);step();}
function step(){if(!scanning)return;var p=(performance.now()-started)/2600;if(p>=1){finishScan();chime(1.6);return}var n=p<.28?0:p<.60?1:p<.86?2:3;var label=$('scan-phase');if(label)label.textContent=['Locking signal…','Reading ascension…','Resolving modifiers…','Scan complete…'][n];var beam=root.querySelector('.scan-beam');if(beam)beam.style.top=(8+Math.floor(p*56)*2)+'px';if(n!==lastStage){if(n>0)chime(.8+n*.25);lastStage=n}timer=setTimeout(step,32);}
'''
body=body.replace("var state={",extra+"\nvar state={")
body=body.replace('render();\n})();',"$('scenario').value='wild';render();\n})();")
css+='''
#ascension-scan {background:transparent;padding:0;}
#ascension-scan .scan::after {animation:none;display:none;}
#ascension-scan .scan-beam {display:none;position:absolute;left:8px;right:8px;height:2px;background:#dbffff;box-shadow:0 -6px #8fe9f022,0 -12px #8fe9f011;z-index:2;pointer-events:none;}
#ascension-scan .scanning .scan-beam {display:block;}
#ascension-scan .motes i,#ascension-scan .sprite,#ascension-scan .embers i,#ascension-scan .power::before,#ascension-scan .badge.pulse {animation:none;}
#ascension-scan .game {padding:12px 8px;}
@media(max-width:400px){#ascension-scan .row{grid-template-columns:1fr auto;}#ascension-scan .row .tag{grid-column:1/-1;justify-self:start;}#ascension-scan .panel .body{padding:0 6px;}#ascension-scan .scan{margin:0;}#ascension-scan .head{padding:8px;}#ascension-scan .name{font-size:22px;}#ascension-scan .unique h4{flex-wrap:wrap;}}
'''
dest=Path('C:/Users/Facel/.codex/visualizations/2026/10/03/01a10321-bfea-72f2-b10d-262342e9708b/inspection-scan.html')
dest.write_text('<style>'+css+'</style>\n'+body,encoding='utf-8')
print(dest)
