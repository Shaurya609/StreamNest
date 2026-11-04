const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('fs'),vm=require('vm'),os=require('os'),path=require('path'),zlib=require('zlib');
const deps=path.resolve('desktop/packaging/node_modules');
const load=require('module').createRequire(path.join(deps,'../package.json'));
const modulePath='../overlay/butter-provider/streamnest-subtitles';
function helper(){let m={exports:{}};vm.runInNewContext(fs.readFileSync(require.resolve(modulePath),'utf8'),{module:m,Buffer,require:n=>n.startsWith('.')?require(n):load(n),setTimeout,clearTimeout});return m.exports;}
const srt=Buffer.from('1\n00:00:01,000 --> 00:00:03,000\nVisible subtitle\n');
test('subtitle loader detects gzip by bytes and validates plain timed text',()=>{let api=helper();assert.equal(api.unpack(zlib.gzipSync(srt)).toString(),srt.toString());assert.throws(()=>api.unpack(Buffer.from('<html>blocked</html>')),/timings/);});
test('zip SRT entry is read without extracting unrelated archive paths',()=>{const Zip=load('adm-zip'),zip=new Zip();zip.addFile('nested/episode.srt',srt);zip.addFile('unrelated.txt',Buffer.from('ignore'));assert.equal(helper().unpack(zip.toBuffer()).toString(),srt.toString());});
test('subtitle loopback server binds dynamically and serves only registered formats',async()=>{const app={},m={App:app},errors=[];vm.runInNewContext(fs.readFileSync(path.resolve('desktop/overlay/lib/subtitle/server.js'),'utf8'),{window:m,require,win:{error:e=>errors.push(e)}});let dir=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-sub-test-')),file=path.join(dir,'test.vtt');fs.writeFileSync(file,'WEBVTT\n\n00:00:01.000 --> 00:00:02.000\nVisible\n');try{await new Promise((resolve,reject)=>app.SubtitlesServer.start({vtt:file},e=>e?reject(e):resolve()));let url=app.SubtitlesServer.url('vtt');assert.match(url,/127\.0\.0\.1:\d+\/data.vtt/);let r=await fetch(url);assert.equal(r.headers.get('content-type'),'text/vtt; charset=utf-8');assert.match(await r.text(),/Visible/);assert.equal((await fetch(url.replace('.vtt','.bad'))).status,404);}finally{app.SubtitlesServer.stop();fs.rmSync(dir,{recursive:true,force:true});}assert.equal(errors.length,0);});

test('VIP placeholder is an actionable service error, not a successful subtitle',()=>{assert.throws(()=>helper().unpack(Buffer.from('1\n00:00:00,001 --> 04:00:00,003\nBecome OpenSubtitles.org VIP Member')),/VIP notice/);});
