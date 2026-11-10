 'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),{EventEmitter}=require('node:events');
const P=require('../overlay/butter-provider/streamnest-preload');
const fs=require('fs'),os=require('os'),path=require('path');
const attrs={type:'episode',imdb_id:'tt1',season:1,episode:1,torrent:'magnet:one',auto_play_data:[{season:1,episode:1},{season:1,episode:2}]};
const next={...attrs,episode:2,torrent:'magnet:two'};
const full={ready:true,files:[{length:100,progress:1}]};
const turn=()=>new Promise(r=>setImmediate(r));
function harness(space=10**10){
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-preload-')),clients=[],removed=[];
 class Client extends EventEmitter{constructor(){super();clients.push(this);}add(url,options,ready){this.dir=options.path;const file={name:'S01E02.mp4',path:'S01E02.mp4',length:100,progress:0.5,select(){this.selected=true;}};this.torrent=new EventEmitter();Object.assign(this.torrent,{pieces:[1,2],files:[file],deselect(){}});setImmediate(()=>ready(this.torrent));return this.torrent;}destroy(done){this.destroyed=true;setImmediate(done);}}
 const manager=P.create({Client,space:async()=>space,remove:dir=>removed.push(dir),resolve:async()=>next});
 return {root,clients,removed,manager,async cleanup(){await manager.cancel();fs.rmSync(root,{recursive:true,force:true});}};
}
test('preloading is opt-in and waits for a fully verified selected file',async()=>{const h=harness();try{
 h.manager.consider(attrs,full,0,false,h.root);assert.equal(h.manager.state,null);
 h.manager.consider(attrs,{...full,files:[{length:100,progress:0.8}]},0,true,h.root);assert.equal(h.manager.state,null);
 h.manager.consider(attrs,full,0,true,h.root);await turn();await turn();assert.equal(h.clients.length,1);assert(h.manager.state.file.selected);
 h.manager.consider(attrs,full,0,true,h.root);assert.equal(h.clients.length,1);
 }finally{await h.cleanup();}});
test('partial preload is released and reused when navigating to its episode',async()=>{const h=harness();try{
 h.manager.consider(attrs,full,0,true,h.root);await turn();await turn();const dir=h.manager.state.dir;
 assert.equal((await h.manager.next(attrs,1)).episode,2);await h.manager.take(next);
 assert(h.clients[0].destroyed);assert.equal(h.manager.state,null);assert(!h.removed.includes(dir));
 }finally{await h.cleanup();}});
test('previous cache survives until both a decoded video and downloader release are confirmed',()=>{
 const removed=[],m=P.create({remove:dir=>removed.push(dir)}),client={};
 const old=path.resolve('/cache/.streamnest-episodes/'+'a'.repeat(64)),current=path.resolve('/cache/.streamnest-episodes/'+'b'.repeat(64));
 m.retire(attrs,{path:old},client);m.released(client);assert.deepEqual(removed,[]);
 m.playing(current);assert.deepEqual(removed,[old]);
 const m2=P.create({remove:dir=>removed.push(dir)});m2.retire(attrs,{path:current},client);m2.playing(old);assert.equal(removed.length,1);m2.released(client);assert.equal(removed.length,2);
});
test('low storage skips preload without touching the active episode',async()=>{const h=harness(1);try{
 h.manager.consider(attrs,full,0,true,h.root);await turn();await turn();assert.equal(h.clients.length,0);assert.equal(h.manager.state,null);assert.match(h.manager.message,/storage/);
 }finally{await h.cleanup();}});
test('disabling stops the speculative client and deletes only its cache',async()=>{const h=harness();try{
 h.manager.consider(attrs,full,0,true,h.root);await turn();await turn();const dir=h.manager.state.dir;
 h.manager.consider(attrs,full,0,false,h.root);await turn();await turn();assert(h.clients[0].destroyed);assert.deepEqual(h.removed,[dir]);
 }finally{await h.cleanup();}});
test('cleanup rejects arbitrary folders and never follows a nested link',()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-preload-safe-')),outside=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-kept-'));
 try{const target=path.join(root,'.streamnest-episodes','a'.repeat(64));fs.mkdirSync(target,{recursive:true});fs.writeFileSync(path.join(outside,'keep'),'keep');fs.symlinkSync(outside,path.join(target,'linked'),process.platform==='win32'?'junction':'dir');assert.throws(()=>P.remove(outside),/Unsafe/);P.remove(target);assert(fs.existsSync(path.join(outside,'keep')));}finally{fs.rmSync(root,{recursive:true,force:true});fs.rmSync(outside,{recursive:true,force:true});}
});

test('a second cancellation waits for an already closing preload before exit cleanup',async()=>{
 const h=harness();try{
  h.manager.consider(attrs,full,0,true,h.root);await turn();await turn();
  let finish;h.clients[0].destroy=done=>{h.clients[0].destroyed=true;finish=done;};
  const first=h.manager.cancel();let released=false;const second=h.manager.cancel().then(()=>{released=true;});
  await turn();assert.equal(released,false);finish();await first;await second;assert.equal(released,true);
 }finally{await h.cleanup();}
});
