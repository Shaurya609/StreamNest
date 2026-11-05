(function(){
 'use strict';
 const fs=require('fs'),path=require('path'),https=require('https'),root=process.env.STREAMNEST_SMOKE_ROOT;
 if(!root)return;
 const result={errors:[],version:nw.App.manifest.version};let done=false,pairing='',S;
 window.addEventListener('error',event=>result.errors.push(String(event.message)));
 function deleteGroup(code){return new Promise((resolve,reject)=>{const endpoint=require('./butter-provider/watch-sync.json').endpoint;
  const request=https.request(endpoint+'/v1/progress',{method:'DELETE',headers:{Authorization:'Bearer '+require('./butter-provider/streamnest-sync-crypto').auth(code)}},response=>{response.resume();response.on('end',()=>response.statusCode===200?resolve():reject(Error('Cleanup HTTP '+response.statusCode)));});request.setTimeout(8000,()=>request.destroy(Error('Cleanup timed out')));request.on('error',reject);request.end();
 });}
 async function finish(ok){if(done)return;done=true;try{if(pairing){await deleteGroup(pairing);result.disposableGroupDeleted=true;}if(S)S.disconnect();}catch(error){ok=false;result.errors.push(String(error));}result.ok=ok&&!result.errors.length;fs.writeFileSync(path.join(root,'result.json'),JSON.stringify(result,null,2));nw.App.quit();}
 function assert(value,message){if(!value)throw Error(message);}
 function visible(panel){const r=panel.getBoundingClientRect();return r.width>300&&r.height>200&&r.top>=0&&r.left>=0&&r.bottom<=innerHeight+1&&r.right<=innerWidth+1;}
 function button(panel,text){return Array.from(panel.querySelectorAll('button')).find(b=>b.textContent===text);}
 const timer=setInterval(()=>{if(!window.App||!App.settings||!App.Trakt||!window.Settings)return;clearInterval(timer);
 (async()=>{try{
  S=require('./butter-provider/streamnest-sync');App.vent.trigger('settings:show');await new Promise(r=>setTimeout(r,300));
  const opener=document.getElementById('streamnestSyncOpen');assert(opener,'Settings sync button missing');opener.click();let panel=document.getElementById('streamnest-sync-panel');assert(panel&&visible(panel),'Sync dialog is outside the visible viewport');result.settingsButtonOpenedVisibleDialog=true;
  assert(panel.getAttribute('role')==='dialog','Dialog semantics missing');const input=panel.querySelector('input'),qr=panel.querySelector('img');assert(getComputedStyle(qr).display==='none','Empty pairing displays a blank QR');
  input.value='invalid-code';await button(panel,'Connect with this code').onclick();assert(panel.innerText.includes('Enter the complete pairing code'),'Invalid pairing failed silently');result.invalidCodeReported=true;
  await button(panel,'Create a sync group').onclick();pairing=S.config().code;await button(panel,'Show pairing code').onclick();assert(pairing&&S.status==='Synced','Pairing did not connect to live service');await new Promise(r=>setTimeout(r,900));assert(qr.src.startsWith('data:image/')&&getComputedStyle(qr).display!=='none','Pairing QR is not visible');result.livePairingAndQr=true;
  result.controls=Array.from(panel.querySelectorAll('button')).map(b=>{const style=getComputedStyle(b);assert(style.color!==style.backgroundColor&&b.getBoundingClientRect().height>=40,'Unreadable or undersized sync control');return b.textContent;});
  await button(panel,'Sync now').onclick();assert(S.status==='Synced','Sync now failed');
  await button(panel,'Disconnect this device').onclick();await new Promise(r=>setTimeout(r,900));assert(!S.config().code&&input.value===''&&getComputedStyle(qr).display==='none','Disconnect did not clear pairing and QR');result.disconnectClearedPairing=true;
  await button(panel,'Close').onclick();assert(!document.getElementById('streamnest-sync-panel'),'Close did not dismiss dialog');opener.click();panel=document.getElementById('streamnest-sync-panel');assert(panel&&visible(panel),'Reopening sync dialog failed');result.closeAndReopen=true;await finish(true);
 }catch(error){result.errors.push(String(error.stack||error));await finish(false);}})();
 },250);setTimeout(()=>finish(false),60000);
})();
