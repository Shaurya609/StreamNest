 'use strict';
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const reserve=256*1024*1024;
function identity(a){return a&&a.imdb_id+':'+a.season+':'+a.episode;}
function directory(a,base,downloadOnly=false){
 if(downloadOnly||!a||a.type!=='episode'&&!(Number(a.episode)>0))return base;
 const key=crypto.createHash('sha256').update(identity(a)+'|'+String(a.torrent)+'|'+a.fileIndex).digest('hex');
 const dir=path.join(base,'.streamnest-episodes',key);fs.mkdirSync(path.join(dir,'TorrentCache'),{recursive:true});return dir;
}
function remove(dir){
 const target=path.resolve(dir),base=path.dirname(target);
 if(path.basename(base)!=='.streamnest-episodes'||!/^[a-f0-9]{64}$/.test(path.basename(target)))throw Error('Unsafe episode cache target');
 if(!fs.existsSync(target))return;
 if(fs.realpathSync(base)!==base||fs.lstatSync(target).isSymbolicLink())throw Error('Unsafe episode cache link');
 const walk=file=>{const stat=fs.lstatSync(file);if(stat.isDirectory()&&!stat.isSymbolicLink()){for(const entry of fs.readdirSync(file))walk(path.join(file,entry));fs.rmdirSync(file);}else fs.unlinkSync(file);};walk(target);
}
function space(dir){return new Promise((resolve,reject)=>{
 if(process.platform!=='win32'){if(!fs.statfs)return reject(Error('Free storage check unavailable'));return fs.statfs(dir,(e,s)=>e?reject(e):resolve(s.bavail*s.bsize));}
 const script="$ErrorActionPreference='Stop';([IO.DriveInfo]::new([IO.Path]::GetPathRoot('"+path.resolve(dir).replace(/'/g,"''")+"'))).AvailableFreeSpace";
 require('child_process').execFile('powershell.exe',['-NoProfile','-NonInteractive','-EncodedCommand',Buffer.from(script,'utf16le').toString('base64')],{windowsHide:true,timeout:8000},(e,out)=>{const free=Number(out&&out.trim());if(e||!Number.isFinite(free)||free<0)reject(Error('Unable to check free storage'));else resolve(free);});
});}
function create(options={}){
 const resolve=options.resolve||((a,d)=>require('./streamnest-playback').resolve(a,d));
 const storage=options.space||space,erase=options.remove||remove;
 let slot=null,attempted='',retired=[],activeDir='',lastError='';const closing=new Set();
 const destroy=state=>new Promise(done=>{if(state.timer)clearInterval(state.timer);if(!state.client||state.client.destroyed)return done();state.client.destroy(()=>done());});
 const discard=dir=>{if(!dir||dir===activeDir||slot&&slot.dir===dir)return;try{erase(dir);}catch(e){lastError='Episode cache cleanup will retry';}};
 async function cancel(keep=false){const state=slot;slot=null;attempted='';if(!state)return Promise.all(Array.from(closing,s=>s.closing));state.cancelled=true;state.closing=destroy(state).then(()=>{if(!keep)discard(state.dir);}).finally(()=>closing.delete(state));closing.add(state);return state.closing;}
 function consider(a,torrent,index,enabled,base){
  if(!enabled){if(slot)cancel().catch(()=>{});return;}
  const file=torrent&&torrent.files&&torrent.files[index];
  if(!file||!(file.length>0)||!(file.progress>=1)||!torrent.ready||closing.size)return;
  const key=identity(a)+'|'+String(a.torrent);if(attempted===key)return;
  if(slot)return;
  attempted=key;lastError='';const state={cancelled:false};slot=state;
  state.promise=(async()=>{try{
   const next=await resolve(a,1);if(slot!==state||state.cancelled)return;
   state.attrs=next;state.dir=directory(next,base);
   if(await storage(state.dir)<512*1024*1024)throw Error('Not enough storage to preload');
   if(slot!==state||state.cancelled)return;
   const Client=options.Client||require('webtorrent');state.client=new Client({maxConns:16});
   const fail=error=>{if(slot!==state)return;lastError=error.message||'Next episode preload unavailable';cancel().catch(()=>{});attempted=key;};
   state.client.on('error',fail);
   state.torrent=state.client.add(next.torrent,{path:state.dir},async t=>{
    try{
     if(slot!==state||state.cancelled)return;
     t.deselect(0,t.pieces.length-1,false);
     const selected=require('./streamnest-episodes').selectFile(t.files,next),f=t.files[selected];
     if(await storage(state.dir)<f.length+reserve)throw Error('Not enough storage to preload this episode');
     if(slot!==state||state.cancelled)return;
     f.select();state.file=f;
    }catch(e){fail(e);}
   });
   state.torrent.on('error',fail);
   const deadline=Date.now()+120000;
   state.timer=setInterval(async()=>{try{if(slot!==state)return;if(!state.file&&Date.now()>deadline)throw Error('Preload metadata timed out');if(await storage(state.dir)<reserve)throw Error('Preloading stopped to keep storage available');}catch(e){fail(e);}},15000);
  }catch(e){lastError=e.message;if(slot===state){await cancel();attempted=key;}}})();
 }
 async function next(a,d){const row=require('./streamnest-playback').neighbour(a,d);if(d===1&&slot&&slot.attrs&&identity(slot.attrs)===identity(Object.assign({},row,{imdb_id:a.imdb_id})))return Object.assign({},slot.attrs,{streamnestPrepared:true});return resolve(a,d);}
 async function take(a){const state=slot;if(!state)return;const keep=state.attrs&&identity(state.attrs)===identity(a)&&String(state.attrs.torrent)===String(a.torrent);await cancel(keep);}
 function retire(a,torrent,client){if(!torrent||!torrent.path||!a||!(Number(a.episode)>0))return;const dir=path.resolve(torrent.path);if(path.basename(path.dirname(dir))!=='.streamnest-episodes')return;retired.push({dir,client,torrent,released:false});}
 function cleanup(){retired=retired.filter(item=>{if(!item.released||!activeDir||item.dir===activeDir)return true;try{erase(item.dir);return false;}catch(e){lastError='Episode cache cleanup will retry';return true;}});}
 function releasedTorrent(torrent){for(const item of retired)if(item.torrent===torrent)item.released=true;cleanup();}
 function released(client){for(const item of retired)if(item.client===client)item.released=true;cleanup();}
 function playing(dir){if(!dir)return;activeDir=path.resolve(dir);cleanup();}
 return {consider,next,take,cancel,retire,released,releasedTorrent,playing,directory,get state(){return slot;},get message(){return lastError||slot&&slot.file&&(slot.file.progress>=1?'Next episode downloaded':'Preloading next episode')||'';}};
}
module.exports=Object.assign(create(),{create,directory,identity,remove,space});
