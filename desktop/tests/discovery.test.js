 'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict');
const D=require('../overlay/butter-provider/streamnest-discovery');
const E=require('../overlay/butter-provider/streamnest-episodes');
const row={season:1,episode:1,absolute:1,animeMapped:true,streamnestSeriesTitle:'Black Clover'};
const hash='a'.repeat(40);
const item={title:'[SubsPlease] Black Clover (01-170) (1080p) [Batch]',link:'https://nyaa.si/download/123.torrent','nyaa:trusted':'Yes','nyaa:seeders':'20','nyaa:infoHash':hash};
test('anime matching accepts numbered batches and rejects related series',()=>{
 assert.equal(D.match(item.title,'Black Clover',row),2);
 for(const name of ['Black Clover 2nd Season - 01 [1080p]','Mugyutto! Black Clover - 01 [1080p]','Black Clover Movie - 01 [1080p]','Black Clover - 02 [1080p]'])assert.equal(D.match(name,'Black Clover',row),0,name);
 assert.equal(D.match('[Erai] Black Clover (TV) - 01 ~ 53 [1080p]','Black Clover',row),2);
});
test('trusted independent candidates require seeds, hash and HTTPS torrent URL',()=>{
 assert.equal(D.candidates([item],'Black Clover',row).length,1);
 for(const patch of [{'nyaa:trusted':'No'},{'nyaa:seeders':'0'},{'nyaa:infoHash':'bad'},{link:'https://evil.example/download/123.torrent'}])assert.equal(D.candidates([Object.assign({},item,patch)],'Black Clover',row).length,0);
});
test('pack indexing requires exact member and matching torrent hash',()=>{
 const c=D.candidates([item],'Black Clover',row)[0],parsed={infoHash:hash,files:[{path:'pack/Black Clover - 02.mkv',length:500},{path:'pack/Black Clover - 01.mkv',length:400}]};
 assert.equal(D.indexed(c,parsed,row).fileIndex,1);
 assert.throws(()=>D.indexed(c,Object.assign({},parsed,{infoHash:'b'.repeat(40)}),row));
 assert.throws(()=>D.indexed(c,{infoHash:hash,files:[{path:'pack/Black Clover - 02.mkv',length:500}]},row));
});
test('single-file wrong episode cannot bypass matching',()=>{
 assert.throws(()=>E.selectFile([{name:'Show.S01E02.mkv',path:'Show.S01E02.mkv',length:50}],row));
});
test('independent fallback survives primary outage and reuses bounded result cache',async()=>{
 let calls=0;
 const d=D.create({get:async()=>{calls++;throw Error('offline');},text:async()=>'<rss/>',parseXML:async()=>({rss:{channel:{item}}}),bytes:async()=>Buffer.from('fixture'),parseTorrent:()=>({infoHash:hash,files:[{path:'Black Clover - 01.mkv',length:123}]})});
 assert.equal((await d.find('tt7441658',1,1,row))[0].provider,'Nyaa');
 await d.find('tt7441658',1,1,row);assert.equal(calls,1);
 d.invalidate();await d.find('tt7441658',1,1,row);assert.equal(calls,2);
});
test('provider errors remain retryable rather than cached as empty',async()=>{
 let calls=0;const d=D.create({get:async()=>{calls++;return {};}});
 await assert.rejects(d.find('tt123',1,1),/incomplete/);await assert.rejects(d.find('tt123',1,1),/incomplete/);assert.equal(calls,2);
});
test('safe empty responses are cached and unsafe RSS is rejected',async()=>{
 let calls=0;const d=D.create({get:async()=>{calls++;return {streams:[]};},text:async()=>'<!DOCTYPE rss><rss/>'});
 assert.deepEqual(await d.find('tt123',1,1),[]);await d.find('tt123',1,1);assert.equal(calls,1);
 await assert.rejects(d.find('tt7441658',1,1,row),/incomplete/);
});

test('retry limit preserves independent fallback without changing explicit quality',()=>{
 const rows=Array.from({length:8},(_,i)=>({url:'magnet:?xt=urn:btih:'+String(i).padStart(40,'a'),fileIndex:0,quality:'1080p',seed:100-i,provider:'Alternative'}));
 rows.push({url:'https://nyaa.si/download/1.torrent',fileIndex:0,quality:'720p',seed:1,provider:'Nyaa'});
 assert.equal(E.attempts(rows).length,6);assert.equal(E.attempts(rows)[5].provider,'Nyaa');
 assert.ok(E.attempts(rows,'1080p').every(s=>s.quality==='1080p'));
});
