'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),os=require('os'),path=require('path');
const S=require('../overlay/butter-provider/streamnest-home-sync'),C=require('../overlay/butter-provider/streamnest-sync-crypto'),H=require('../overlay/butter-provider/streamnest-home');
const fixture=require('../../app/src/test/resources/group-home-vector.json');
const title=(id,kind='movies',genre='action')=>({imdb:id,title:id,kind,genres:[genre],year:2020});
const history={'movie:tt1':{positionMs:12000,durationMs:120000,updatedAt:100}};
test('Home namespace and encryption agree with the Android vector; progress stays compatible',()=>{
 assert.equal(C.homeCode(fixture.code),fixture.homeCode);assert.notEqual(C.auth(fixture.code),C.auth(fixture.homeCode));
 assert.deepEqual(C.openDocument(fixture.homeCode,fixture.encrypted),fixture.snapshot);assert.deepEqual(C.open(fixture.homeCode,fixture.encrypted),{});
 assert.equal(S.signature(fixture.history),fixture.snapshot.history);assert.throws(()=>C.openDocument(fixture.code,fixture.encrypted));
});
test('shared rows survive missing/different local catalogs, preserving local playback payloads',()=>{
 const home=S.create([title('tt1'),title('tt2'),title('tt3','shows')],history),pool=[{...title('tt2'),torrents:[{url:'local-source'}]}];
 const rows=S.apply(H.build(pool,history),pool,history,home);assert.deepEqual(rows.slice(1).map(r=>r.cards.map(c=>c.title.imdb)),home.rows.map(r=>r.ids));
 assert.equal(rows[1].cards[0].title.torrents[0].url,'local-source');assert.equal(rows[1].cards[1].title.kind,'shows');
 assert.deepEqual(S.apply([],[],{},null),[]);
});
test('watching changes picks; a stale device cannot forget newer watched titles',()=>{
 const pool=[title('tt1'),title('tt2'),title('tt3')],before=S.create(pool,history),newHistory={...history,'movie:tt2':{...history['movie:tt1'],updatedAt:200}};
 const after=S.create(pool,newHistory,before);assert.ok(after.rows.every(r=>!r.ids.includes('tt2')));
 const stale=S.create(pool,history,after);assert.equal(stale.history,after.history);assert.ok(stale.rows.every(r=>!r.ids.includes('tt2')));
 assert.equal(S.signature(history),S.signature({'movie:tt1':{...history['movie:tt1'],positionMs:60000,updatedAt:500}}));
});
test('unsafe metadata, foreign row names, duplicate IDs and oversized snapshots are bounded',()=>{
 const home=S.create([title('tt1'),{...title('tt2'),poster_big:'file:///secret',secret:'do not copy'}],history);
 assert.ok(!JSON.stringify(home).includes('secret'));home.rows.push({name:'foreign',ids:['tt2']});home.rows[0].ids=['tt2','tt2','tt999'];const clean=S.clean(home);assert.deepEqual(clean.rows[0].ids,['tt2']);assert.ok(clean.rows.every(r=>r.name!=='foreign'));
 assert.equal(S.clean({...home,noise:'x'.repeat(100000)}),null);assert.equal(S.clean({...home,createdAt:Date.now()+172800000}),null);
});
test('revision conflict adopts winner without re-ranking for another device; cached Home is scoped',async()=>{
 const dir=fs.mkdtempSync(path.join(os.tmpdir(),'sn-home-'));global.nw={App:{dataPath:dir}};
 try{
  const code=C.create(),derived=C.homeCode(code),winner=S.create([title('tt1'),title('tt8')],history);let writes=0;
  const result=await S.sync(code,async(key,payload)=>{assert.equal(key,derived);if(payload){writes++;return {conflict:true};}return writes?{revision:1,payload:C.seal(derived,winner)}:{revision:0,payload:null};},()=>code,history,()=>[title('tt1'),title('tt2')]);
  assert.deepEqual(result.rows,winner.rows);assert.equal(writes,1);assert.deepEqual(S.cached(code).rows,winner.rows);assert.equal(S.cached(C.create()),null);
  let reads=0;await S.sync(code,async()=>{reads++;throw Error('offline');},()=>C.create(),history,()=>[]);assert.equal(reads,0);
 }finally{delete global.nw;fs.rmSync(dir,{recursive:true,force:true});}
});

test('shared generator preserves anime identity over a remembered general-series duplicate',()=>{const home=S.create([title('tt2','animes'),title('tt2','shows')],{});assert.equal(home.titles.tt2.kind,'animes');assert.ok(home.rows.find(r=>r.name==='Anime').ids.includes('tt2'));});

test('routine Home polling is bounded without delaying a new group or viewing choice',()=>{const poll=S.createGate();assert.ok(poll('group','first',1000));assert.equal(poll('group','first',2000),false);assert.ok(poll('group','new-choice',2001));assert.equal(poll('group','new-choice',60000),false);assert.ok(poll('group','new-choice',62001));assert.ok(poll('other-group','new-choice',62002));});
