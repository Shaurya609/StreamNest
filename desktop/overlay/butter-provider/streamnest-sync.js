'use strict';
const fs=require('fs'),path=require('path'),https=require('https'),C=require('./streamnest-sync-crypto');
const root=typeof nw!=='undefined'?nw.App.dataPath:require('os').tmpdir(),configPath=path.join(root,'streamnest-watch-sync.json');
let followup=false,inFlight=null,last=0,status='Not connected';
function config(){try{const value=JSON.parse(fs.readFileSync(configPath,'utf8'));return value&&typeof value==='object'?value:{};}catch(e){return {};}}
function endpoint(){const url=JSON.parse(fs.readFileSync(path.join(__dirname,'watch-sync.json'),'utf8')).endpoint;if(!/^https:\/\/[A-Za-z0-9.-]+$/.test(url))throw Error('Sync service is not configured yet');return url;}
function call(method,code,payload,revision){return new Promise((resolve,reject)=>{
 const bytes=payload?Buffer.from(JSON.stringify(payload)):null;
 const headers={Authorization:'Bearer '+C.auth(code),Accept:'application/json'};if(bytes){headers['Content-Type']='application/json';headers['Content-Length']=bytes.length;headers['If-Match']=String(revision);}
 const req=https.request(endpoint()+'/v1/progress',{method,headers},res=>{
  const chunks=[];let size=0;res.on('data',b=>{size+=b.length;if(size>180000)req.destroy(Error('Sync response too large'));else chunks.push(b);});res.on('error',reject);
  res.on('end',()=>{if(res.statusCode===409)return resolve({conflict:true});if(res.statusCode!==200)return reject(Error('Sync service HTTP '+res.statusCode));try{resolve(JSON.parse(Buffer.concat(chunks)));}catch(e){reject(e);}});
 });req.setTimeout(10000,()=>req.destroy(Error('Sync timed out')));req.on('error',reject);req.end(bytes);
});}
let group=null,groupError='',lastGroup=0,groupQueue=Promise.resolve();
function saveConfig(value){fs.writeFileSync(configPath,JSON.stringify(value),{mode:0o600});}
function device(){let value=config();if(!value.deviceId){value.deviceId=require('crypto').randomBytes(16).toString('hex');saveConfig(value);}return {deviceId:value.deviceId,deviceName:value.deviceName||require('os').hostname().slice(0,64)||'Windows'};}
function groupUpdate(name){const code=config().code;groupQueue=groupQueue.catch(()=>{}).then(()=>performGroupUpdate(name,code));return groupQueue;}
async function performGroupUpdate(name,code){if(!code||config().code!==code)return;const data=device();if(name!==undefined)data.groupName=name;try{const response=await require('./streamnest-group').call(endpoint(),C.auth(code),'PUT',data);if(config().code===code){group=response;groupError='';lastGroup=Date.now();}return response;}catch(e){groupError='Group details unavailable — watch sync still works';throw e;}}
function refreshGroup(){if(Date.now()-lastGroup<60000)return;lastGroup=Date.now();groupUpdate().catch(()=>{});}
async function sync(code){
 const progress=require('./streamnest-progress');
 for(let i=0;i<3;i++){
  if(config().code!==code)return;
  const remote=await call('GET',code),incoming=remote.payload?C.open(code,remote.payload):{};
  if(config().code!==code)return;
  progress.merge(incoming);const result=await call('PUT',code,C.seal(code,progress.records()),remote.revision);if(!result.conflict){try{await require('./streamnest-manual-watched').sync(code,(derived,payload,revision)=>call(payload?'PUT':'GET',derived,payload,revision),()=>config().code);}catch(e){/* Independent watched preferences retry on next sync. */}try{await require('./streamnest-home-sync').sync(code,(derived,payload,revision)=>call(payload?'PUT':'GET',derived,payload,revision),()=>config().code,progress.records(),()=>require('./streamnest-home').load());}catch(e){/* Home retries independently; progress already synced. */}return;}
 }throw Error('Another device is syncing; retry shortly');
}
async function request(force=false){
 if(inFlight){if(force)followup=true;return inFlight;}
 const code=config().code;if(!code||!force&&Date.now()-last<10000)return;
 last=Date.now();inFlight=sync(code).then(()=>{if(config().code===code){status='Synced';refreshGroup();}},()=>{status='Offline — progress stays on this device';}).finally(()=>{inFlight=null;if(followup){followup=false;request(true);}});return inFlight;
}
async function beforeSelection(){let timer;try{await Promise.race([request(true),new Promise(resolve=>{timer=setTimeout(resolve,2000);})]);}finally{clearTimeout(timer);}}

async function connect(code){const P=require('./streamnest-pairing');code=P.input(code);if(code.length===8){code=P.open(code,(await P.call(endpoint(),code,'POST')).payload);}else if(code.length!==36)throw Error('Enter an eight-character pairing code or the complete recovery key');code=C.format(C.parse(code));if(config().code!==code)require('./streamnest-diagnostics').reset();saveConfig({...config(),code});require('./streamnest-diagnostics').connected().catch(()=>{});require('./streamnest-home-sync').changed();group=null;lastGroup=0;status='Connecting…';return request(true);}
function disconnect(){require('./streamnest-diagnostics').reset();const previous=config();if(previous.code&&previous.deviceId)groupQueue=groupQueue.catch(()=>{}).then(()=>require('./streamnest-group').call(endpoint(),C.auth(previous.code),'DELETE',{deviceId:previous.deviceId})).catch(()=>{});saveConfig({deviceId:previous.deviceId,deviceName:previous.deviceName});require('./streamnest-home-sync').changed();group=null;status='Not connected';}
function show(win){
 const d=win.document;let old=d.getElementById('streamnest-sync-panel');if(old)old.remove();
 const panel=d.createElement('div');panel.id='streamnest-sync-panel';panel.setAttribute('role','dialog');panel.setAttribute('aria-modal','true');panel.setAttribute('aria-label','Resume across devices');panel.style.cssText='position:fixed;top:8%;right:12%;bottom:8%;left:12%;box-sizing:border-box;overflow:auto;z-index:99999;background:#20232b;color:#f3f4f6;padding:24px;border:1px solid #697080;border-radius:10px;font:16px/1.5 sans-serif';d.body.appendChild(panel);
 const heading=d.createElement('h2');heading.textContent='Resume across devices';panel.appendChild(heading);
 const note=d.createElement('p');note.textContent='Enter the eight-character code shown on your other device. Watching progress is encrypted before syncing. No StreamNest account is needed.';panel.appendChild(note);
 const input=d.createElement('input');input.setAttribute('aria-label','Private pairing code');input.spellcheck=false;input.autocomplete='off';input.placeholder='Eight-character pairing code';input.value='';input.style.cssText='box-sizing:border-box;width:100%;min-height:44px;padding:12px;color:#101827;background:#ffffff;border:1px solid #8794a8;border-radius:6px;font:16px monospace';panel.appendChild(input);
 let error=null,expiryTimer;const label=d.createElement('p');label.textContent=status;panel.appendChild(label);
 const button=(text,action)=>{const b=d.createElement('button');b.type='button';b.textContent=text;b.style.cssText='appearance:none;display:inline-block;position:static;float:none;width:auto;height:auto;min-height:42px;padding:10px 14px;margin:8px 8px 8px 0;color:#ffffff;background:#303b4c;border:1px solid #8794a8;border-radius:6px;font:600 14px/20px sans-serif;text-shadow:none;opacity:1;cursor:pointer';b.onfocus=()=>{b.style.outline='2px solid #99c5ff';b.style.outlineOffset='2px';};b.onblur=()=>{b.style.outline='';};b.onclick=async()=>{try{error=null;await action();label.textContent=status;}catch(e){error=e.message;label.textContent=error;}};panel.appendChild(b);return b;};
 const groupName=d.createElement('input');groupName.placeholder='Sync group name';groupName.maxLength=64;groupName.setAttribute('aria-label','Sync group name');groupName.style.cssText=input.style.cssText;panel.appendChild(groupName);
 const roster=d.createElement('p');panel.appendChild(roster);
 const create=button('Create a sync group',async()=>{if(config().code)throw Error('Disconnect before creating another group');const name=groupName.value.trim();if(!name)throw Error('Enter a group name');await connect(C.create());await groupUpdate(name);input.value='';});
 button('Save group name',()=>groupUpdate(groupName.value.trim()));
 const deviceName=d.createElement('input');deviceName.placeholder='This device name';deviceName.maxLength=64;deviceName.value=config().deviceName||require('os').hostname();deviceName.setAttribute('aria-label','This device name');deviceName.style.cssText=input.style.cssText;panel.appendChild(deviceName);
 button('Save device name',async()=>{const name=deviceName.value.trim();if(!name||name.length>64)throw Error('Enter a device name');saveConfig({...config(),deviceName:name});await groupUpdate();});
 button('Connect with this code',()=>connect(input.value));
 const close=()=>{clearTimeout(expiryTimer);panel.remove();const opener=d.getElementById('streamnestSyncOpen');if(opener)opener.focus();};
 button('Show pairing code',async()=>{
  const recovery=config().code;if(!recovery)throw Error('Create a sync group first');
  const P=require('./streamnest-pairing'),short=P.create();
  await P.call(endpoint(),short,'PUT',P.seal(short,recovery),C.auth(recovery));
  input.value=short.slice(0,4)+' '+short.slice(4);error='Pairing code expires in 5 minutes and works once.';
  clearTimeout(expiryTimer);const displayed=input.value;
  expiryTimer=setTimeout(()=>{if(panel.isConnected&&input.value===displayed){input.value='';error='Pairing code expired. Select Show pairing code again.';}},300000);
 });
 button('Show recovery key',()=>{input.value=config().code||'';});
 button('Sync now',()=>request(true));button('Disconnect this device',()=>{disconnect();input.value='';});button('Close',close);
 panel.addEventListener('keydown',event=>{if(event.key==='Escape'){event.preventDefault();event.stopPropagation();close();}});
 const qr=d.createElement('img');qr.style.cssText='display:none;width:220px;height:220px;background:white;padding:10px;margin:10px';panel.appendChild(qr);
 let shown='';const timer=setInterval(()=>{if(!panel.isConnected){clearInterval(timer);return;}label.textContent=error||status;const connected=!!config().code;create.style.display=connected?'none':'inline-block';heading.textContent=connected?'Sync group: '+(group?group.name:'Sync group'):'Resume across devices';roster.textContent=connected?(group?group.devices.length+' devices · '+group.devices.map(x=>x.name).join(', '):groupError||'Loading group details…'):'';if(group&&d.activeElement!==groupName)groupName.value=group.name;if(input.value!==shown){shown=input.value;try{if(shown.replace(/[\s-]/g,'').length===8)require('./streamnest-pairing').normalize(shown);else C.parse(shown);const image=require('qrcode-generator')(0,'M');image.addData(shown.replace(/[\s-]/g,'').length===8?endpoint()+'/pair#'+encodeURIComponent(shown):'streamnest-watch://pair/'+encodeURIComponent(shown));image.make();qr.src=image.createDataURL(4);qr.style.display='block';}catch(e){qr.style.display='none';}}},700);
 const privacy=d.createElement('p');privacy.textContent='Group and device names are visible to the service owner; watch history stays encrypted. Keep the code private: it connects devices to your watch history. Generate a temporary code when you want to pair another device. Local progress works offline; inactive cloud backups are cleared after 180 days.';panel.appendChild(privacy);
 if(config().code)groupUpdate().catch(()=>{});
 input.focus();
}
module.exports={groupUpdate,get group(){return group;},request,beforeSelection,connect,disconnect,show,config,get status(){return status;}};
