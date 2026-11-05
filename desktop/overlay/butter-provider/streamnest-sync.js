'use strict';
const fs=require('fs'),path=require('path'),https=require('https'),C=require('./streamnest-sync-crypto');
const root=typeof nw!=='undefined'?nw.App.dataPath:require('os').tmpdir(),configPath=path.join(root,'streamnest-watch-sync.json');
let pending=false,last=0,status='Not connected';
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
async function sync(code){
 const progress=require('./streamnest-progress');
 for(let i=0;i<3;i++){
  if(config().code!==code)return;
  const remote=await call('GET',code),incoming=remote.payload?C.open(code,remote.payload):{};
  if(config().code!==code)return;
  progress.merge(incoming);const result=await call('PUT',code,C.seal(code,progress.records()),remote.revision);if(!result.conflict)return;
 }throw Error('Another device is syncing; retry shortly');
}
async function request(force=false){
 const code=config().code;if(!code||pending||!force&&Date.now()-last<30000)return;
 pending=true;last=Date.now();try{await sync(code);if(config().code===code)status='Synced';}catch(e){status='Offline — progress stays on this device';}finally{pending=false;}
}
function connect(code){code=C.format(C.parse(code));fs.writeFileSync(configPath,JSON.stringify({code}),{mode:0o600});status='Connecting…';return request(true);}
function disconnect(){fs.writeFileSync(configPath,'{}',{mode:0o600});status='Not connected';}
function show(win){
 const d=win.document;let old=d.getElementById('streamnest-sync-panel');if(old)old.remove();
 const panel=d.createElement('div');panel.id='streamnest-sync-panel';panel.style.cssText='position:fixed;inset:12%;overflow:auto;z-index:99999;background:#20232b;color:white;padding:28px;border:1px solid #697080;font:16px sans-serif';d.body.appendChild(panel);
 const heading=d.createElement('h2');heading.textContent='Resume across devices';panel.appendChild(heading);
 const note=d.createElement('p');note.textContent='Pair devices using the same private code. Watching progress is encrypted before syncing. No StreamNest account is needed.';panel.appendChild(note);
 const input=d.createElement('input');input.spellcheck=false;input.autocomplete='off';input.placeholder='Pairing code from another device';input.value=config().code||'';input.style.cssText='width:95%;padding:12px;color:black';panel.appendChild(input);
 let error=null;const label=d.createElement('p');label.textContent=status;panel.appendChild(label);
 const button=(text,action)=>{const b=d.createElement('button');b.textContent=text;b.style.cssText='padding:10px;margin:8px';b.onclick=async()=>{try{error=null;await action();label.textContent=status;}catch(e){error=e.message;label.textContent=error;}};panel.appendChild(b);};
 button('Create a sync group',async()=>{const code=C.create();input.value=code;await connect(code);});
 button('Connect with this code',()=>connect(input.value));
 button('Sync now',()=>request(true));button('Disconnect this device',()=>{disconnect();input.value='';});button('Close',()=>panel.remove());
 const qr=d.createElement('img');qr.style.cssText='display:block;width:220px;height:220px;background:white;padding:10px;margin:10px';panel.appendChild(qr);
 let shown='';const timer=setInterval(()=>{if(!panel.isConnected){clearInterval(timer);return;}label.textContent=error||status;if(input.value!==shown){shown=input.value;try{C.parse(shown);const image=require('qrcode-generator')(0,'M');image.addData('streamnest-watch://pair/'+shown);image.make();qr.src=image.createDataURL(4);qr.style.display='block';}catch(e){qr.style.display='none';}}},700);
 const privacy=d.createElement('p');privacy.textContent='Keep the code private: it connects devices to your watch history. Keep a copy if you want to pair another device later. Local progress works offline; inactive cloud backups are cleared after 180 days.';panel.appendChild(privacy);
}
module.exports={request,connect,disconnect,show,config,get status(){return status;}};
