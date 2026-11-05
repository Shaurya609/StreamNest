const {test}=require('node:test'),assert=require('node:assert/strict'),C=require('../overlay/butter-provider/streamnest-sync-crypto'),P=require('../overlay/butter-provider/streamnest-progress');
test('episode selection matches shared Android vectors',()=>{
 const vectors=require('../../app/src/test/resources/resume-episodes-vector.json');
 for(const row of vectors.cases){const chosen=P.chooseEpisode(vectors.imdb,vectors.episodes,row.history,vectors.now);assert.equal(chosen?chosen.season+':'+chosen.episode:null,row.expected,row.name);}
});
test('encrypted completion advances selection while a newer rewind clears completion',()=>{
 const code=C.create(),done={'episode:tt123:1:1':{positionMs:0,durationMs:1500000,updatedAt:100,completed:true}};
 const restored=C.open(code,C.seal(code,done));assert.equal(restored['episode:tt123:1:1'].completed,true);
 const legacy=JSON.parse(JSON.stringify(done));delete legacy['episode:tt123:1:1'].completed;assert.equal(C.merge(legacy,restored)['episode:tt123:1:1'].completed,true);
 assert.equal(C.merge(restored,{'episode:tt123:1:1':{positionMs:12000,durationMs:1500000,updatedAt:200}})['episode:tt123:1:1'].completed,undefined);
});
test('actual detail selector uses resume history and does not overwrite a later manual choice',async()=>{
 const fs=require('fs'),path=require('path'),vm=require('vm'),App={View:{}};let release,selected=null;
 const episodes=[{season:1,episode:1},{season:1,episode:2},{season:2,episode:1}],history={'episode:tt123:1:2':{positionMs:0,durationMs:1500000,updatedAt:100,completed:true}};
 let wait=Promise.resolve(),after=Promise.resolve();const context={window:{App},Marionette:{View:{extend:value=>value}},Database:{getEpisodesWatched:async()=>[]},AdvSettings:{get:()=>null},_:{each:(rows,fn)=>rows.forEach(fn)},$:selector=>({length:1,selector,parent(){return this;}}),require:name=>name.includes('streamnest-sync')?{beforeSelection:()=>wait,request:()=>after}:name.includes('streamnest-progress')?{chooseEpisode:(id,rows)=>P.chooseEpisode(id,rows,history,1793880000000)}:require(path.resolve(__dirname,'../overlay',name))};
 vm.runInNewContext(fs.readFileSync(path.join(__dirname,'../overlay/lib/views/show_detail.js'),'utf8'),context);
 const view={_resumeGeneration:1,model:{get:key=>({imdb_id:'tt123',tvdb_id:'123',episodes}[key])},isDestroyed:()=>false,markWatched:()=>{},selectSeason:()=>{},selectEpisode:row=>{selected=row.selector;},isElementVisible:()=>true};
 await App.View.ShowDetail.selectNextEpisode.call(view);assert.equal(selected,'#watched-2-1');
 selected=null;wait=new Promise(resolve=>release=resolve);const pending=App.View.ShowDetail.selectNextEpisode.call(view);view._manualResumeChoice=true;release();await pending;assert.equal(selected,null);
 view._manualResumeChoice=false;wait=Promise.resolve();let finishSync;after=new Promise(resolve=>finishSync=resolve);selected=null;
 const late=App.View.ShowDetail.selectNextEpisode.call(view);await new Promise(resolve=>setImmediate(resolve));assert.equal(selected,'#watched-2-1');
 history['episode:tt123:1:1']={positionMs:12000,durationMs:1500000,updatedAt:300};finishSync();await late;assert.equal(selected,'#watched-1-1');

});
test('resume identities survive source/quality changes and separate movies and episodes',()=>{
 assert.equal(P.identity({imdb_id:'tt123',torrent:'1080p'}),P.identity({imdb_id:'tt123',torrent:'4k'}));
 assert.notEqual(P.identity({imdb_id:'tt123',season:1,episode:1}),P.identity({imdb_id:'tt123',season:1,episode:2}));
 assert.equal(P.resumable(900,1000),900);assert.equal(P.resumable(990,1000),0);
});
test('merging sync history respects intentional rewinds, completion and unrelated episodes',()=>{
 const a={'movie:tt123':{positionMs:90000,durationMs:1000000,updatedAt:1000}},b={'movie:tt123':{positionMs:30000,durationMs:1000000,updatedAt:2000},'episode:tt456:1:1':{positionMs:10000,durationMs:100000,updatedAt:2000}};
 assert.equal(C.merge(a,b)['movie:tt123'].positionMs,30000);assert(C.merge(a,b)['episode:tt456:1:1']);b['movie:tt123'].positionMs=0;assert.equal(C.merge(a,b)['movie:tt123'].positionMs,0);
});
