const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('fs'),vm=require('vm'),EventEmitter=require('events');
function service(responses){
 let now=1000,doc;const calls=[];
 const value=o=>({struct:{member:Object.entries(o).map(([name,v])=>({name,value:typeof v==='object'?value(v):{string:v}}))}});
 const https={request(url,options,callback){const req=new EventEmitter();req.setTimeout=()=>{};req.destroy=e=>req.emit('error',e);req.end=body=>{
 calls.push(body);queueMicrotask(()=>{const res=new EventEmitter();res.statusCode=200;res.resume=()=>{};callback(res);
 doc={methodResponse:{params:{param:{value:value(responses.shift()||{status:'200 OK',token:'renewed'})}}}};
 res.emit('data',Buffer.from('mock'));res.emit('end');});};return req;}};
 const module={exports:{}};const fakeDate={now:()=>now};
 vm.runInNewContext(fs.readFileSync(require.resolve('../overlay/butter-provider/streamnest-org'),'utf8'),{
 module,exports:module.exports,require:name=>name==='https'?https:name==='xml2js'?{parseString:(text,options,cb)=>cb(null,doc)}:require(name),Buffer,Date:fakeDate,Promise
 });return {api:module.exports,calls,advance:()=>now+=700000};
}
test('anonymous launch renewal is shared; expiry renews before the next request',async()=>{
 const s=service([{status:'200 OK',token:'one'},{status:'200 OK',token:'two'}]);
 await Promise.all([s.api.login(true),s.api.login(true)]);assert.equal(s.calls.length,1);
 await s.api.login();assert.equal(s.calls.length,1);s.advance();await s.api.login();assert.equal(s.calls.length,2);
});
test('server-invalidated anonymous session retries exactly once with a new token',async()=>{
 const s=service([{status:'200 OK',token:'one'},{status:'406 No session'},{status:'200 OK',token:'two'},{status:'200 OK'}]);
 await s.api.sessionRpc('SearchSubtitles',[[]]);assert.equal(s.calls.length,4);assert(s.calls[3].includes('two'));
});
test('persistent authentication failure is bounded and unrelated errors are not retried',async()=>{
 for(const status of ['406 No session','503 Service Unavailable']){
 const responses=[{status:'200 OK',token:'one'},{status},{status:'200 OK',token:'two'},{status}];
 const s=service(responses);await assert.rejects(s.api.sessionRpc('SearchSubtitles',[[]]));assert.equal(s.calls.length,status.startsWith('406')?4:2);
 }
});
