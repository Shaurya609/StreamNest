import {test} from 'node:test';
import assert from 'node:assert/strict';
import vm from 'node:vm';
import fs from 'node:fs';
import dashboard from './dashboard.mjs';
import {createDiagnosticsLanguage} from './diagnostic-explanations.mjs';
const L=createDiagnosticsLanguage(),base=Date.now();
const event=(type,data={},at=base,session='one',level='info')=>({type,data,at,session,level});

test('USB capacity does not hide an internal-space storage failure',()=>{
 const explained=L.event(event('cache.failed',{reason:'storage',internalFreeMb:98,cacheFreeMb:14933}));
 assert.match(explained.title,/Internal app storage/);assert.match(explained.meaning,/98 MB/);assert.match(explained.meaning,/USB/);assert.doesNotMatch(explained.next,/another source/);
 const summary=L.summary({events:[event('player.sample',{internalFreeMb:512,cacheFreeMb:14933,playState:'playing'})]},base);
 assert.match(summary.evidence.join(' '),/Internal app storage free/);assert.match(summary.evidence.join(' '),/Selected video-cache storage free/);
});
test('episode selection and storage failures explain uncertainty without inventing a cause',()=>{
 const episode=L.event(event('torrent.failed',{frames:['app.streamnest.EpisodeFiles.select:49']}));
 assert.match(episode.title,/match this episode/);assert.match(episode.meaning,/filename\/numbering mismatch/);assert.match(episode.meaning,/does not identify the filename/);
 const cache=L.event(event('torrent.failed',{frames:['app.streamnest.CacheLocation.writable:102']}));assert.match(cache.title,/cache folder/);assert.match(cache.meaning,/does not distinguish/);
});
test('unconfirmed death is not described as completed cleanup',()=>{
 assert.match(L.event(event('torrent.stage',{workerStopMode:'unconfirmed'})).title,/not been confirmed stopped/);
 assert.match(L.event(event('torrent.stage',{workerStopMode:'self_abort'})).meaning,/does not by itself fix/);
 assert.match(L.event(event('torrent.failed',{errorClass:'WorkerSlotsBusyException'})).title,/earlier download/);
});
test('a recovered latest sample outranks an earlier failure and preserves historical context',()=>{
 const s=L.summary({events:[event('torrent.failed',{errorClass:'IOException',frames:['app.streamnest.EpisodeFiles.select:49']},base-30000,'one','error'),event('player.sample',{playState:'playing',totalFrames:914,downloadKbps:3231,peers:5,bufferPercent:100,percent:2},base)]},base);
 assert.match(s.headline,/reported playing/);assert.equal(s.lastProblem.earlier,true);assert.match(s.evidence.join(' '),/not the amount buffered/);assert.match(s.evidence.join(' '),/does not.*guarantee/);
});
test('summaries use the newest session and explicitly mark stale snapshots',()=>{
 const s=L.summary({events:[event('torrent.failed',{errorClass:'WorkerSlotsBusyException'},base-1000,'old','error'),event('app.start',{},base,'new')]},base+180000);
 assert.equal(s.headline,'App opened');assert.equal(s.lastProblem,null);assert.equal(s.stale,true);
});
test('buffering with active downloading does not assert that required pieces are available',()=>{
 const e=L.event(event('player.sample',{playState:'buffering',downloadKbps:4155,peers:5}));assert.match(e.meaning,/4.1 MB\/s/);assert.match(e.meaning,/does not prove/);
 assert.match(L.event(event('torrent.stage',{frames:['org.libtorrent4j.TorrentHandle.havePiece:185']})).meaning,/does not prove.*frozen/);
});
test('nonadvancing frame counters are a possibility, not proof, and paused samples are excluded',()=>{
 const a=event('player.sample',{attemptId:'a',playState:'playing',totalFrames:10},base-30000),b=event('player.sample',{attemptId:'a',playState:'playing',totalFrames:10},base);
 assert.match(L.summary({events:[a,b]},base).headline,/may be stuck/);b.data.playState='paused';assert.match(L.summary({events:[a,b]},base).headline,/paused/);
 b.data.playState='playing';b.data.totalFrames=30;assert.match(L.summary({events:[a,b]},base).headline,/reported playing/);
});
test('metadata 404 does not diagnose streaming failure and arbitrary fields are not repeated',()=>{
 const e=L.event(event('catalog.failed',{status:404,frames:['app.streamnest.HomeLibrary.enrich:50'],password:'secret'}));assert.match(e.title,/extra title details/);assert.match(e.meaning,/does not by itself mean/);assert(!JSON.stringify(e).includes('secret'));
});
test('the embedded browser script compiles, keeps raw details and does not render report HTML',()=>{
 const script=dashboard.match(/<script>([\s\S]*)<\/script>/)[1];new vm.Script(script);const language=vm.runInNewContext(script.split('const el=id')[0]+';language');assert.match(language.event({type:'torrent.failed',data:{frames:['app.streamnest.EpisodeFiles.select:49']}}).title,/match this episode/);assert.match(fs.readFileSync(new URL('./wrangler.toml',import.meta.url),'utf8'),/keep_names\s*=\s*false/);
 assert.match(dashboard,/What this device last reported/);assert.match(dashboard,/Technical details/);assert.match(dashboard,/newest session/);assert(!dashboard.includes('innerHTML'));
});

test('general app errors retain severity without inventing a cause',()=>{const e=L.event(event('app.error',{},base,'one','error'));assert.equal(e.tone,'error');assert.match(e.title,/reported a problem/);assert.equal(L.event(event('unknown',{},base,'one','warn')).tone,'warn');});
