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
function space(dir,options={}){return new Promise((resolve,reject)=>{
 const platform=options.platform||process.platform,statfs=options.statfs||fs.statfs;let settled=false;
 const timer=setTimeout(()=>finish(Error('Free storage check timed out')),8000);
 function finish(error,free){if(settled)return;settled=true;clearTimeout(timer);if(error||!Number.isFinite(free)||free<0)reject(error||Error('Unable to check free storage'));else resolve(free);}
 const legacy=()=>{
  if(platform!=='win32')return finish(Error('Free storage check unavailable'));
  const script="$ErrorActionPreference='Stop';([IO.DriveInfo]::new([IO.Path]::GetPathRoot('"+path.resolve(dir).replace(/'/g,"''")+"'))).AvailableFreeSpace";
  (options.execFile||require('child_process').execFile)('powershell.exe',['-NoProfile','-NonInteractive','-EncodedCommand',Buffer.from(script,'utf16le').toString('base64')],{windowsHide:true,timeout:8000},(error,out)=>finish(error,Number(out&&out.trim())));
 };
 if(!statfs)return legacy();
 statfs(dir,(error,s)=>{if(settled)return;if(error){if(['ENOSYS','ENOTSUP'].includes(error.code))return legacy();return finish(error);}finish(null,s.bavail*s.bsize);});
});}
function create(options={}){
 const resolve=options.resolve||((a,d)=>require('./streamnest-playback').resolve(a,d));
 const storage=options.space||space,erase=options.remove||remove;
 let slot=null,attempted='',retired=[],activeDir='',lastError='',generation=0,prepared=null;const closing=new Set(),every=options.interval||setInterval,clearTimer=options.clearInterval||clearInterval;
 const destroy=state=>{if(state.timer)clearTimer(state.timer);if(!state.client)return Promise.resolve();return require('./streamnest-recovery').track(done=>state.client.destroy(done),options.closeMs||6000);};
 const discard=dir=>{if(!dir||dir===activeDir||slot&&slot.dir===dir)return;try{erase(dir);}catch(e){lastError='Episode cache cleanup will retry';}};
 async function cancel(keep=false){++generation;prepared=null;const state=slot;slot=null;attempted='';if(!state)return Promise.all(Array.from(closing,s=>s.closing));state.cancelled=true;state.closing=destroy(state);closing.add(state);(state.closing.completion||state.closing).then(()=>{if(!keep)discard(state.dir);closing.delete(state);}).catch(()=>{});return state.closing;}
 function consider(a,torrent,index,enabled,base,settings={}){
  if(!enabled){if(slot||prepared||closing.size)cancel().catch(()=>{});return;}
  const file=torrent&&torrent.files&&torrent.files[index];
  if(!file||!(file.length>0)||!(file.progress>=1)||!torrent.ready||closing.size)return;
  const key=identity(a)+'|'+String(a.torrent);if(attempted===key)return;
  if(slot)return;
  const seed=prepared&&prepared.key===key?prepared.attrs:null;prepared=null;attempted=key;lastError='';const state={cancelled:false};slot=state;
  state.promise=(async()=>{try{
   const next=seed||await resolve(a,1);if(slot!==state||state.cancelled)return;
   state.attrs=next;state.dir=directory(next,base);
   if(await storage(state.dir)<512*1024*1024)throw Error('Not enough storage to preload');
   if(slot!==state||state.cancelled)return;
   const forced=settings.trackers&&settings.trackers.forced||[],Client=options.Client||require('webtorrent');state.client=new Client({maxConns:16,dht:{concurrency:parseInt(settings.maxUdpReqLimit,10)||16},secure:settings.protocolEncryption||false,tracker:{announce:forced}});
   const fail=error=>{if(slot!==state)return;const alternate=require('./streamnest-episodes').nextAttempt(state.attrs);lastError=error.message||'Next episode preload unavailable';const draining=cancel(),ticket=generation;attempted=key;draining.then(()=>{if(generation!==ticket)return;if(alternate){prepared={key,attrs:{...alternate,streamnestFromBeginning:true}};attempted='';lastError='Trying another source for next episode';}}).catch(()=>{lastError='Preload cache is still closing';});};
   state.client.on('error',fail);
   state.torrent=state.client.add(next.torrent,{path:state.dir,maxConns:16,storeCacheSlots:1,secure:settings.protocolEncryption||false,announce:forced,tracker:forced},async t=>{
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
   const deadline=Date.now()+(options.metadataMs||30000);
   state.timer=every(async()=>{try{if(slot!==state)return;if(!state.file&&Date.now()>deadline)throw Error('Preload metadata timed out');if(await storage(state.dir)<reserve)throw Error('Preloading stopped to keep storage available');}catch(e){fail(e);}},15000);
  }catch(e){lastError=e.message;if(slot===state){await cancel();attempted=key;}}})();
 }
 async function next(a,d){const row=require('./streamnest-playback').neighbour(a,d);if(d===1&&slot&&slot.attrs&&identity(slot.attrs)===identity(Object.assign({},row,{imdb_id:a.imdb_id})))return Object.assign({},slot.attrs,{streamnestPrepared:true,streamnestFromBeginning:true});return resolve(a,d);}
 async function take(a){const state=slot;if(!state)return;const keep=state.attrs&&identity(state.attrs)===identity(a)&&String(state.attrs.torrent)===String(a.torrent);await cancel(keep);}
 function retire(a,torrent,client){if(!torrent||!torrent.path||!a||!(Number(a.episode)>0))return;const dir=path.resolve(torrent.path);if(path.basename(path.dirname(dir))!=='.streamnest-episodes')return;retired.push({dir,client,torrent,released:false});}
 function cleanup(){retired=retired.filter(item=>{if(!item.released||!activeDir||item.dir===activeDir)return true;try{erase(item.dir);return false;}catch(e){lastError='Episode cache cleanup will retry';return true;}});}
 function release(matches){for(const item of retired)if(matches(item)){item.released=true;item.client=null;item.torrent=null;}const seen=new Set();retired=retired.filter(item=>{if(!item.released)return true;if(seen.has(item.dir))return false;seen.add(item.dir);return true;});cleanup();}
 function releasedTorrent(torrent){release(item=>item.torrent===torrent);}
 function released(client){release(item=>item.client===client);}
 function playing(dir){if(!dir)return;activeDir=path.resolve(dir);cleanup();}
 return {consider,next,take,cancel,retire,released,releasedTorrent,playing,directory,get state(){return slot;},get message(){return lastError||slot&&slot.file&&(slot.file.progress>=1?'Next episode downloaded':'Preloading next episode')||'';}};
}
module.exports=Object.assign(create(),{create,directory,identity,remove,space});
