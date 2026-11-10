'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const fs=require('fs'),path=require('path'),vm=require('vm');
const playback=require('../overlay/butter-provider/streamnest-playback');
const pairing=require('../overlay/butter-provider/streamnest-pairing');
test('MSI clipboard variants normalize before short-code validation',()=>{
 for(const input of ['K7MP 4RXT','K7MP​4RXT','Ｋ７ＭＰ４ＲＸＴ','K7MP–4RXT'])
  assert.equal(pairing.normalize(input),'K7MP4RXT');
 assert.throws(()=>pairing.normalize('K7MP4RX'),/eight-character/);
});
function playerHarness(auto=true){
 let tick,now=1000,position=10,paused=false;
 const nodes=[];
 function element(){
  const node={attrs:{},handlers:{},style:{},value:'',visible:true};
  for(const method of ['append','after','find','add','prop','addClass','toggleClass'])node[method]=()=>node;
  node.css=value=>{node.style=value;return node;};
  node.attr=(key,value)=>{
   if(typeof key==='object')Object.assign(node.attrs,key);
   else if(value===undefined)return node.attrs[key];
   else node.attrs[key]=value;
   return node;
  };
  node.on=(name,fn)=>{node.handlers[name]=fn;return node;};
  node.text=value=>{node.value=value;return node;};
  node.toggle=value=>{node.visible=value;return node;};
  node.hide=()=>{node.visible=false;return node;};
  nodes.push(node);
  return node;
 }
 const App={View:{}},settings={streamnestAutoSkip:auto};
 const context={window:{App},Marionette:{View:{extend:value=>value}},Settings:settings,
  document:{createTextNode:value=>value},$:()=>element(),Date:{now:()=>now},
  setInterval:fn=>{tick=fn;return 1;},require:()=>playback};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../overlay/lib/views/player.js'),'utf8'),context);
 const attrs={type:'episode',season:1,episode:1,auto_play_data:[{season:1,episode:1},{season:1,episode:2}]};
 const view={model:{get:()=>({attributes:attrs,get:key=>attrs[key]})},isDestroyed:()=>false,
  _streamnestSkipRequested:true,
  player:{el:()=>({}),currentTime:value=>{if(value!==undefined)position=value;return position;}},
  video:{currentTime:()=>position,duration:()=>100,paused:()=>paused}};
 App.View.Player.setupEpisodeControls.call(view);
 view._streamnestSegments=[{type:'intro',start:10,end:20}];
 return {view,nodes,settings,step(ms=1000){now+=ms;tick();},pause(value){paused=value;},get position(){return position;}};
}
test('actual desktop skip control waits five playing seconds and pauses its countdown',()=>{
 const h=playerHarness();
 h.step();
 assert.match(h.view._streamnestSkip.value,/Skip Intro.*5/);
 for(let i=0;i<4;i++)h.step();
 assert.equal(h.position,10);
 h.pause(true);
 for(let i=0;i<6;i++)h.step();
 assert.equal(h.position,10);
 h.pause(false);h.step();
 assert.equal(h.position,20);
 assert(h.nodes.some(node=>node.attrs['aria-label']==='Next episode'&&node.value===''));
});
test('settings-disabled auto-skip preserve manual skipping',()=>{
 for(const auto of [true,false]){
  const h=playerHarness(auto);h.step();
  if(auto)h.settings.streamnestAutoSkip=false;
  for(let i=0;i<8;i++)h.step();
  assert.equal(h.position,10);
  h.view._streamnestSkip.handlers.click();
  assert.equal(h.position,20);
 }
});

test('a pause during an active upload queues the latest timestamp',async()=>{
 const C=require('../overlay/butter-provider/streamnest-sync-crypto');
 const EventEmitter=require('events'),code=C.create();
 let rows={'episode:tt123:1:1':{positionMs:100000,durationMs:1500000,updatedAt:100}};
 let remote=null,revision=0,releaseFirst,puts=0;
 const https={request(url,options,callback){
  const req=new EventEmitter();
  req.setTimeout=()=>{};
  req.end=bytes=>{
   const res=new EventEmitter();res.statusCode=200;
   const finish=doc=>{callback(res);setImmediate(()=>{res.emit('data',Buffer.from(JSON.stringify(doc)));res.emit('end');});};
   if(options.method==='GET')finish({revision,payload:remote});
   else {
    remote=JSON.parse(bytes);revision++;puts++;
    if(puts===1)releaseFirst=()=>finish({revision});
    else finish({revision});
   }
  };
  return req;
 }};
 const module={exports:{}};
 const context={module,exports:module.exports,Buffer,setTimeout,clearTimeout,__dirname:"/adapter",
  nw:{App:{dataPath:'/isolated'}},require:name=>{
   if(name==='fs')return {readFileSync:file=>JSON.stringify(file.endsWith('streamnest-watch-sync.json')?{code}:{endpoint:'https://sync.example'})};
   if(name==='path')return path;
   if(name==='https')return https;
   if(name==='./streamnest-sync-crypto')return C;
   if(name==='./streamnest-progress')return {records:()=>rows,merge:incoming=>{rows=C.merge(rows,incoming);}};
   throw Error(name);
  }};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../overlay/butter-provider/streamnest-sync.js'),'utf8'),context);
 const S=module.exports,first=S.request();
 for(let i=0;i<20&&!releaseFirst;i++)await new Promise(resolve=>setImmediate(resolve));
 assert.equal(puts,1);
 rows={'episode:tt123:1:1':{positionMs:200000,durationMs:1500000,updatedAt:200}};
 S.request(true);releaseFirst();await first;
 for(let i=0;i<20&&puts<2;i++)await new Promise(resolve=>setImmediate(resolve));
 assert.equal(puts,2);
 assert.equal(C.open(code,remote)['episode:tt123:1:1'].positionMs,200000);
});

test('rewinding a completed paused episode saves the intended position',()=>{
 const App={View:{}},saved=[];let uploads=0;
 const context={window:{App},Marionette:{View:{extend:value=>value}},
  require:()=>({request:()=>uploads++})};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../overlay/lib/views/player.js'),'utf8'),context);
 const view={_progressEnded:true,video:{currentTime:()=>45,duration:()=>1500},
  persistProgress(){if(!this._progressEnded)saved.push(this.video.currentTime());}};
 App.View.Player.onProgressSeeked.call(view);
 assert.deepEqual(saved,[45]);
 assert.equal(uploads,1);
});
