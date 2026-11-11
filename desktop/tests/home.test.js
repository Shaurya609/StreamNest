 'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),H=require('../overlay/butter-provider/streamnest-home'),Z=require('../overlay/butter-provider/streamnest-eztv');
function title(id,kind,genres=['drama']){return {imdb:id,title:id,kind,genres,year:2020,rating:8};}
const history={
 'episode:tt1:1:1':{positionMs:120000,durationMs:1200000,updatedAt:1},
 'episode:tt1:1:2':{positionMs:0,durationMs:1200000,updatedAt:2,completed:true},
 'movie:tt2':{positionMs:60000,durationMs:1200000,updatedAt:3}
};
test('home collapses episodes by latest event, retains unfinished series and orders progress',()=>{
 const rows=H.build([title('tt1','shows'),title('tt2','movies')],history);assert.equal(rows[0].cards.length,2);assert.equal(rows[0].cards[1].progress.continueSeries,true);assert.equal(H.id(rows[0].cards[0].title),'tt2');
 const rewind={...history,'episode:tt1:1:1':{positionMs:30000,durationMs:1200000,updatedAt:4}};assert.equal(H.build([title('tt1','shows'),title('tt2','movies')],rewind)[0].cards[0].historyKey,'episode:tt1:1:1');
});
test('new user discovery is diverse, bounded and excludes future movies',()=>{
 const pool=[title('tt1','movies'),title('tt2','shows'),title('tt3','animes'),{...title('tt4','movies'),year:3000}];const rows=H.build(pool,{},3);assert.equal(rows[1].name,'Discover something new');assert.deepEqual(rows[1].cards.map(c=>c.title.kind),['movies','shows','animes']);assert.ok(rows.every(r=>r.cards.length<=3));assert.ok(!JSON.stringify(rows).includes('tt4'));
});
test('genre affinity stays local and excludes watched recommendations',()=>{
 const pool=[title('tt1','movies',['action']),title('tt2','movies',['romance']),title('tt3','movies',['action'])];const rows=H.build(pool,{'movie:tt1':{positionMs:20000,durationMs:120000,updatedAt:1}});assert.equal(rows[1].name,'Picked for you');assert.equal(H.id(rows[1].cards[0].title),'tt3');assert.ok(rows[1].cards.every(c=>H.id(c.title)!=='tt1'));
});
test('anime identity wins over duplicate general-series record',()=>{const rows=H.build([title('tt1','animes'),title('tt1','shows')]);assert.equal(rows[1].cards[0].title.kind,'animes');});
const torrent={imdb_id:'11280740',season:'1',episode:'1',hash:'a'.repeat(40),title:'Severance S01E01 1080p WEB x264',seeds:5};
test('independent series validates identity, season, episode, quality and reported seeds',()=>{
 const doc={imdb_id:'11280740',torrents:[torrent]};assert.equal(Z.parse(doc,'tt11280740',1,1).length,1);
 for(const patch of [{imdb_id:'999'},{episode:'2'},{title:'Severance S01E02 1080p'},{title:'Severance S01E01 1080p HDCAM'},{title:'Severance S01E01E02 1080p'},{seeds:0}])assert.equal(Z.parse({...doc,torrents:[{...torrent,...patch}]},'tt11280740',1,1).length,0);
 assert.throws(()=>Z.parse({...doc,imdb_id:'999'},'tt11280740',1,1));
});
test('independent series scan is bounded and retry advances middle pages',async()=>{
 const pages=[];const z=Z.create(async url=>{pages.push(Number(new URL(url).searchParams.get('page')));return {imdb_id:'11280740',page:Number(new URL(url).searchParams.get('page')),torrents_count:1000,torrents:[]};});
 await assert.rejects(z.find('tt11280740',1,1),/incomplete/);assert.deepEqual(pages,[1,10,2]);await assert.rejects(z.find('tt11280740',1,1),/incomplete/);assert.deepEqual(pages,[1,10,2,3]);z.retry();await assert.rejects(z.find('tt11280740',1,1),/incomplete/);assert.deepEqual(pages.slice(-3),[1,10,4]);
});

test('anime reverse mapping requires exact TV identity and matching title',()=>{
 const E=require('../overlay/butter-provider/streamnest-episodes');const doc={mappings:{imdb_id:'tt123',type:'TV'},titles:{en:'My Hero Academia','x-jat':'Boku no Hero Academia'}};
 assert.ok(E.trustedReverseMapping(doc,'tt123','My Hero Academia (TV Series 2016–)'));
 assert.ok(!E.trustedReverseMapping({...doc,mappings:{imdb_id:'tt123',type:'OVA'}},'tt123','My Hero Academia'));
 assert.ok(!E.trustedReverseMapping(doc,'tt123','Another anime'));assert.ok(!E.trustedReverseMapping(doc,'tt999','My Hero Academia'));
});

test('confirmed absence prevents fallback from inventing a skip segment',async()=>{
 const P=require('../overlay/butter-provider/streamnest-playback');let calls=0;
 const rows=await P.segments({imdb_id:'tt123',season:1,episode:1,malId:1},120,async()=>{calls++;return {imdb_id:'tt123',season:1,episode:1,no_intro:true,no_outro:true};});assert.deepEqual(rows,[]);assert.equal(calls,1);
});
test('series lookup remains available when the primary index fails',async()=>{
 const D=require('../overlay/butter-provider/streamnest-discovery');const d=D.create({get:async()=>{throw Error('offline');},series:async()=>[{url:'magnet:?xt=urn:btih:'+'a'.repeat(40),quality:'720p',fileIndex:-1,provider:'EZTV',seed:3}]});assert.equal((await d.find('tt123',1,1))[0].provider,'EZTV');
});

test('optional filler labels reject another metadata page and keep only explicit booleans',()=>{
 const F=require('../overlay/butter-provider/streamnest-filler');assert.deepEqual(F.clean([{mal_id:1,filler:true},{mal_id:101,filler:true,recap:false},{mal_id:102,filler:'yes'}],2),[{mal_id:101,filler:true,recap:false},{mal_id:102}]);
});

test('resolution dimensions are not mistaken for episode numbers',()=>{const E=require('../overlay/butter-provider/streamnest-episodes');assert.equal(E.selectFile([{name:'Video.1920x1080.mkv',path:'Video.1920x1080.mkv',length:100}],{season:1,episode:1}),0);});

test('a first-season anime mapping enriches rather than truncates the full series',()=>{
 const E=require('../overlay/butter-provider/streamnest-episodes'),mapped=[{season:1,episode:1,title:'Origin',first_aired:100,animeMapped:true,absolute:1,skipMalId:1}],all=[{season:1,episode:1,title:'Origin',first_aired:100},{season:2,episode:1,title:'Next season',first_aired:200}];
 const rows=E.augmentMappings(mapped,all);assert.equal(rows.length,2);assert.equal(rows[0].skipMalId,1);assert.equal(rows[1].skipMalId,undefined);
 assert.equal(E.augmentMappings([{...mapped[0],title:'Other episode',first_aired:300000}],all,true)[0].skipMalId,undefined);
});

test('completed episode offers the next known episode and completed finale disappears',()=>{
 const t={...title('tt1','animes'),homeEpisodeOrder:['1:1','1:2','2:1']},completed={'episode:tt1:1:2':{positionMs:0,durationMs:1200000,updatedAt:2,completed:true}};
 const cards=H.build([t],completed)[0].cards;assert.equal(cards.length,1);assert.equal(cards[0].historyKey,'episode:tt1:2:1');assert.equal(cards[0].progress.nextEpisode,true);
 assert.equal(H.build([t],{...completed,'episode:tt1:1:1':{...completed['episode:tt1:1:2'],updatedAt:1},'episode:tt1:2:1':{positionMs:0,durationMs:1200000,updatedAt:3,completed:true}})[0].cards.length,0);
});
