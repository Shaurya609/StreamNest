const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),path=require('path'),os=require('os'),crypto=require('crypto'),vm=require('vm'),{Readable}=require('stream'),{EventEmitter}=require('events');
test('update downloader verifies cached and transferred bytes and removes a failed partial',async()=>{
 const folder=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-update-download-'));let bytes=Buffer.from('valid test installer'),calls=0;
 const item={url:'https://github.com/Shaurya609/StreamNest/releases/download/desktop-v0.12.0/Setup.exe',bytes:bytes.length,sha256:crypto.createHash('sha256').update(bytes).digest('hex')};
 const https={get:(url,options,cb)=>{const req=new EventEmitter();req.setTimeout=()=>{};req.destroy=e=>req.emit('error',e);calls++;queueMicrotask(()=>{const r=Readable.from([bytes]);r.statusCode=200;cb(r);});return req;}};
 const m={exports:{}};vm.runInNewContext(fs.readFileSync(path.resolve('desktop/standalone/update-download.js'),'utf8'),{module:m,Buffer,URL,Date,require:n=>n==='https'?https:n==='./update-policy'?require('../standalone/update-policy'):require(n)});
 try{
  const file=await m.exports.download(item,folder);assert.equal(fs.readFileSync(file).toString(),'valid test installer');await m.exports.download(item,folder);assert.equal(calls,1);
  fs.writeFileSync(file,'bad cache');bytes=Buffer.alloc(item.bytes,1);
  await assert.rejects(m.exports.download(item,folder),/checksum/);assert(!fs.existsSync(file+'.part'));assert(!fs.existsSync(file));
 }finally{fs.rmSync(folder,{recursive:true,force:true});}
});
