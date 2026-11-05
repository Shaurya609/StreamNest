const {test}=require('node:test'),assert=require('node:assert/strict'),P=require('../overlay/butter-provider/streamnest-playback');
const episodes=[{season:0,episode:1},{season:2,episode:1,title:'Second season'},{season:1,episode:2,title:'Second'},{season:1,episode:1,title:'First'},{season:1,episode:2}];
const attrs={imdb_id:'tt123',tvdb_id:'tt123',season:1,episode:2,auto_play:true,auto_play_data:episodes,streamnestQuality:'Auto'};
test('canonical neighbours cross season boundaries but keep specials separate',()=>{assert.equal(P.neighbour(attrs,1).season,2);assert.equal(P.neighbour(attrs,-1).episode,1);assert.equal(P.neighbour({...attrs,season:1,episode:1},-1),null);assert.equal(P.neighbour({...attrs,season:2,episode:1},1),null);assert.equal(P.neighbour({...attrs,season:0,episode:1},1),null);assert.equal(P.neighbour({...attrs,episode:99},1),null);});
test('source resolution preserves pack member, settings and queue without mutating canonical data',async()=>{const original=JSON.stringify(episodes);const next=await P.resolve(attrs,1,async()=>[{url:'magnet:?xt=urn:btih:test',quality:'720p',fileIndex:34,file:'episode.mkv',seed:10}]);assert.equal(next.season,2);assert.equal(next.fileIndex,34);assert.equal(next.streamnestFromBeginning,true);assert.equal(next.imdb_id,'tt123');assert.equal(next.auto_play_data,episodes);assert.equal(next.streamnestQuality,'Auto');assert.equal(JSON.stringify(episodes),original);});
test('unavailable or future adjacent episode is not silently skipped',async()=>{await assert.rejects(P.resolve(attrs,1,async()=>[]),/No source/);await assert.rejects(P.resolve({...attrs,auto_play_data:[...episodes.filter(e=>e.season!==2),{season:2,episode:1,airdate:'2999-01-01'}]},1,async()=>[]),/not aired/);});
test('completion excludes pause, startup and truncated streams',()=>{assert(P.completed(1199,1200,true,false));assert(!P.completed(300,1200,true,false));assert(!P.completed(1199,1200,true,true));assert(!P.completed(1199,1200,false,false));assert(!P.completed(0,Infinity,true,false));});
test('timestamp identity, confidence and range validation',()=>{const doc={imdb_id:'tt123',season:1,episode:2,intro:{start_ms:30000,end_ms:120000,confidence:0.9,submission_count:3}};assert.equal(P.introdb(doc,attrs,1440).length,1);assert.equal(P.introdb({...doc,imdb_id:'tt999'},attrs,1440).length,0);assert.equal(P.introdb({...doc,season:2},attrs,1440).length,0);assert.equal(P.introdb({...doc,intro:{...doc.intro,confidence:0.2}},attrs,1440).length,0);assert.equal(P.range('intro',NaN,100,1440),null);assert.equal(P.range('outro',1300,1600,1440),null);});
test('credits skip retains post-credit content and interval controls disappear',()=>{const s=P.range('outro',1200,1300,1440);assert.equal(s.end,1300);assert.equal(P.active([s],1199),null);assert.equal(P.active([s],1200),s);assert.equal(P.active([s],1300),null);});
test('AniSkip rejects mismatched release duration and unsupported segment types',()=>{const doc={found:true,results:[{skipType:'op',episodeLength:1440,interval:{startTime:30,endTime:120}}]};assert.equal(P.aniskip(doc,1440).length,1);assert.equal(P.aniskip(doc,1600).length,0);});
test('timestamp service failure leaves playback usable',async()=>{assert.deepEqual(await P.segments(attrs,1440,async()=>{throw Error('offline');}),[]);});
test('actual player ended handler advances only once and rejects early EOF',()=>{
 const vm=require('vm'),fs=require('fs'),path=require('path'),App={View:{}},context={window:{App},Marionette:{View:{extend:x=>x}},require:s=>s.includes('streamnest-progress')?{save:()=>{}}:require(path.resolve(__dirname,'../overlay',s))};vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../overlay/lib/views/player.js'),'utf8'),context);
 let advanced=0,closed=0;const tm={get:k=>({type:'episode',auto_play:true}[k]),attributes:attrs};const player={model:{get:k=>k==='torrentModel'?tm:null},video:{currentTime:()=>1200,duration:()=>1200},_streamnestPlayed:true,navigateEpisode:()=>advanced++,closePlayer:()=>closed++};App.View.Player.onPlayerEnded.call(player);App.View.Player.onPlayerEnded.call(player);assert.equal(advanced,1);assert.equal(closed,0);
 player._streamnestEnded=false;player.video.currentTime=()=>300;App.View.Player.onPlayerEnded.call(player);assert.equal(advanced,1);
});

test('actual player renders navigation with autoplay off and skips only the active segment',()=>{
 const vm=require('vm'),fs=require('fs'),path=require('path'),App={View:{}};let timer,seekTo;const created=[];
 function node(){return {attrs:{},props:{},handlers:{},find(){return node();},append(){return this;},attr(k,v){if(typeof k==='object')Object.assign(this.attrs,k);else if(v!==undefined)this.attrs[k]=v;else return this.attrs[k];return this;},prop(k,v){this.props[k]=v;return this;},on(k,v){this.handlers[k]=v;return this;},addClass(){return this;},add(){return this;},toggle(v){this.visible=v;return this;}};}
 const $=value=>{if(value&&value.attrs)return value;const n=node();if(typeof value==='string'&&value.startsWith('<button'))created.push(n);return n;};
 const context={window:{App},Marionette:{View:{extend:x=>x}},require:s=>s.includes('streamnest-progress')?{save:()=>{}}:require(path.resolve(__dirname,'../overlay',s)),$,Settings:{streamnestSkipButtons:false},setInterval:cb=>{timer=cb;return 1;}};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../overlay/lib/views/player.js'),'utf8'),context);
 const data={...attrs,episode:1,auto_play:false},tm={get:k=>k==='type'?'episode':data[k],attributes:data};const view={model:{get:k=>k==='torrentModel'?tm:null},player:{el:()=>({}),currentTime:p=>seekTo=p},video:{currentTime:()=>45,duration:()=>1440,paused:()=>false},isDestroyed:()=>false,navigateEpisode:()=>{}};
 App.View.Player.setupEpisodeControls.call(view);assert.equal(created.length,3);assert.equal(created[0].props.disabled,true);assert.equal(created[1].props.disabled,false);
 view._streamnestSegments=[{type:'intro',start:30,end:120}];timer();assert.equal(view._streamnestPlayed,true);assert.equal(view._streamnestSkip.visible,true);assert.equal(view._streamnestSkip.attrs['aria-label'],'Skip intro');view._streamnestSkip.handlers.click();assert.equal(seekTo,120);
 App.View.Player.setupEpisodeControls.call(view);assert.equal(created.length,3);
});

test('MAL skip identity is attached only to explicit matching first-season episode mappings',()=>{const E=require('../overlay/butter-provider/streamnest-episodes');const rows=E.mapped({mappings:{imdb_id:'tt123',mal_id:21},episodes:{'1':{seasonNumber:1,episodeNumber:1},'2':{seasonNumber:2,episodeNumber:1}}},'tt123');assert.equal(rows[0].skipMalId,21);assert.equal(rows[1].skipMalId,0);});

test('paused player does not rewrite stale progress; a rewind and completion are saved',()=>{
 const vm=require('vm'),fs=require('fs'),path=require('path'),App={View:{}},saved=[];
 const progress={identity:()=> 'episode:tt123:1:1',save:(a,p,d)=>saved.push({p,d})};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../overlay/lib/views/player.js'),'utf8'),{window:{App},Marionette:{View:{extend:x=>x}},require:()=>progress});
 let position=125;const view={_progressPlayed:true,model:{get:()=>({attributes:attrs})},video:{duration:()=>1500,currentTime:()=>position}};
 const persist=App.View.Player.persistProgress;persist.call(view,false);persist.call(view,false);
 assert.equal(saved.length,1);position=45;persist.call(view,false);assert.equal(saved[1].p,45);
 persist.call(view,true);assert.equal(saved[2].p,1500);
});
