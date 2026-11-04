'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const E=require('../overlay/butter-provider/streamnest-episodes');
const C=require('../overlay/butter-provider/streamnest-cache');
const R=require('../overlay/butter-provider/streamnest-releases');
const fs=require('fs'),path=require('path'),os=require('os');
test('regular seasons precede Specials and long anime seasons do not collide',()=>{
 const rows=[{season:0,episode:1},{season:1,episode:101},{season:2,episode:1}];
 assert.equal(E.nextUnwatched(rows,[],'firstUnwatched').season,1);
 assert.deepEqual(E.nextUnwatched(rows,[{season:1,episode:101}],'firstUnwatched'),{season:2,episode:1});
 assert.ok(E.compareId('1:101','2:1')<0);assert.ok(E.compareId('0:1','23:24')>0);
});
test('anonymous XML-RPC encoder escapes values and decodes nested responses',()=>{
 const org=require('../overlay/butter-provider/streamnest-org');
 assert.ok(org.encode(['a&b', {imdbid:'123'}]).includes('a&amp;b'));
 assert.deepEqual(org.decode({struct:{member:[{name:'status',value:{string:'200 OK'}},{name:'data',value:{array:{data:{value:{struct:{member:{name:'ISO639',value:{string:'en'}}}}}}}}]}}),{status:'200 OK',data:[{ISO639:'en'}]});
});
test('episode Auto fallback and explicit resolution',()=>{
 const rows=[{url:'a',quality:'1080p',seed:0},{url:'b',quality:'720p',seed:3},{url:'c',quality:'480p',seed:20}];
 assert.equal(E.rank(rows)[0].url,'b');assert.equal(E.rank(rows,'1080p')[0].url,'a');assert.equal(E.rank(rows,'2160p').length,0);
 assert.equal(E.qualities(rows).Auto.url,'b');assert.equal(E.quality('DVDRip'),'SD');assert.equal(E.quality('1080p CAM'),'');
});
test('pack selects smaller requested episode and rejects wrong indexed filename',()=>{
 const files=[{name:'Show.S01E04.mkv',path:'Show/Show.S01E04.mkv',length:1000},{name:'Show.S01E05.mkv',path:'Show/Show.S01E05.mkv',length:100}];
 assert.equal(E.selectFile(files,{season:1,episode:5}),1);
 assert.equal(E.selectFile(files,{fileIndex:1,file_name:'Show.S01E05.mkv'}),1);
 assert.throws(()=>E.selectFile(files,{fileIndex:1,file_name:'Show.S01E04.mkv'}));
 assert.throws(()=>E.selectFile(files,{season:1,episode:6}));
 assert.throws(()=>E.selectFile(files,{fileIndex:10}));
});
test('indexed alternatives preserve file index and bounded retry data',()=>{
 const rows=E.parseStreams({streams:[{name:'Torrentio\n720p',title:'Show.S01E05.mkv\n👤 25',infoHash:'a'.repeat(40),fileIdx:4,behaviorHints:{filename:'Show.S01E05.mkv'}}]});
 assert.equal(rows[0].fileIndex,4);assert.equal(rows[0].seed,25);
 const attrs=E.nextAttempt({streamnestCandidates:[rows[0],{url:'next',fileIndex:5,file:'second.mkv'}],season:1,episode:5});
 assert.equal(attrs.torrent,'next');assert.equal(attrs.fileIndex,5);assert.equal(attrs.season,1);
 assert.equal(E.nextAttempt(attrs),null);
});
test('unavailable canonical episodes are retained, numbering is never inferred',()=>{
 const rows=E.merge([{season:1,episode:1,title:'One'},{season:1,episode:2,title:'Two'}],{'1':[{episode:1,items:[{}]}]},()=>[]);
 assert.equal(rows.length,2);assert.equal(rows[1].title,'Two');assert.deepEqual(rows[1].torrents,{});
 assert.equal(E.canonical({id:'tt1',videos:[{season:1,episode:5,name:'Five'}]})[0].absolute,undefined);
 assert.deepEqual(E.mapped({mappings:{imdb_id:'tt2'},episodes:{}},'tt1'),[]);
});
test('live anime release requires trusted individual release and explicit absolute mapping',()=>{
 const items=[{title:'[Group] One Piece - 1179 [720p]',link:'https://nyaa.si/download/123.torrent','nyaa:trusted':'Yes','nyaa:seeders':'30',pubDate:'2026-11-03'}];
 const releases=R.records(items);assert.equal(releases.length,1);
 const episodes=[{season:23,episode:24,absolute:1179,streamnestSources:[]}];
 R.attachRows(episodes,releases,'One Piece');assert.equal(episodes[0].torrents.Auto.quality,'720p');
 const wrong=[{absolute:1179,streamnestSources:[]}];R.attachRows(wrong,releases,'Another Piece');assert.equal(wrong[0].streamnestSources.length,0);
 assert.equal(R.records([Object.assign({},items[0],{title:'[Group] One Piece Complete Batch [720p]'})]).length,0);
});
test('episode cleanup removes prior pack folder while protecting unrelated files and same episode',()=>{
 const temp=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-test-'));
 assert.ok(path.resolve(temp).startsWith(path.resolve(os.tmpdir())+path.sep+'streamnest-test-'));
 try{
  const registry=path.join(temp,'registry.json'),base=path.join(temp,'videos');fs.mkdirSync(base);
  const personal=path.join(base,'keep.txt');fs.writeFileSync(personal,'keep');
  const first=C.prepare(base,'show:1:4','same-pack',registry);fs.writeFileSync(path.join(first,'episode4.mkv'),'video');
  const other=C.prepare(base,'show:1:4','other-source',registry);assert.ok(fs.existsSync(first));
  const next=C.prepare(base,'show:1:5','same-pack',registry);
  assert.ok(!fs.existsSync(first));assert.ok(!fs.existsSync(other));assert.ok(fs.existsSync(next));assert.equal(fs.readFileSync(personal,'utf8'),'keep');
  assert.throws(()=>C.removeOwned(base,personal));
 }finally{fs.rmSync(temp,{recursive:true,force:true});}
});
