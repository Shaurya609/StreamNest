(function(){
 'use strict';const fs=require('fs'),path=require('path'),root=process.env.STREAMNEST_SMOKE_ROOT;if(!root)return;
 const result={errors:[]};let done=false;const wait=ms=>new Promise(r=>setTimeout(r,ms));
 const assert=(v,m)=>{if(!v)throw Error(m);};function finish(ok){if(done)return;done=true;result.ok=ok;fs.writeFileSync(path.join(root,'result.json'),JSON.stringify(result,null,2));nw.App.quit();}
 window.addEventListener('error',e=>result.errors.push(e.message));window.addEventListener('unhandledrejection',e=>result.errors.push(String(e.reason)));
 (async()=>{try{
  const fixture=JSON.parse(fs.readFileSync(process.env.STREAMNEST_GROUP_HOME_INPUT,'utf8')),S=require('./butter-provider/streamnest-home-sync'),sync=require('./butter-provider/streamnest-sync');
  fs.writeFileSync(path.join(nw.App.dataPath,'streamnest-watch-sync.json'),JSON.stringify({deviceName:'Verification desktop'}));
  await sync.connect(fixture.code);const home=S.cached(fixture.code);assert(home,'Android shared Home was not downloaded');
  for(let n=0;n<200&&!document.querySelector('#filterbar-home');n++)await wait(250);App.vent.trigger('home:list');await wait(1800);
  const host=document.querySelector('.sn-home');assert(host,'Home did not render');let checked=0;
  for(const row of home.rows){if(!row.ids.length)continue;const heading=[...host.querySelectorAll('h2')].find(h=>h.textContent===row.name);assert(heading,'Shared row missing: '+row.name);const names=[...heading.nextElementSibling.querySelectorAll('.sn-label')].map(x=>x.textContent);assert(JSON.stringify(names)===JSON.stringify(row.ids.map(id=>home.titles[id].title)),'Shared row order differs: '+row.name);checked++;}
  assert(checked>=3,'Not enough shared rows tested');assert(home.rows.every(r=>!r.ids.includes(fixture.watchNext)),'Native watched title still recommended');result.sharedRows=checked;result.nativeWatchExcluded=true;
  sync.disconnect();App.vent.trigger('home:list');await wait(600);assert(!S.cached(require('./butter-provider/streamnest-sync-crypto').create()),'Another group saw cached recommendations');result.disconnected=true;finish(!result.errors.length);
 }catch(e){result.errors.push(String(e.stack||e));finish(false);}})();setTimeout(()=>finish(false),100000);
})();
