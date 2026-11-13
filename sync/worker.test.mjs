import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import fs from 'node:fs';
import {createRequire} from 'node:module';
import worker from './worker.mjs';
const require=createRequire(import.meta.url),C=require('../desktop/overlay/butter-provider/streamnest-sync-crypto');
function environment(){const database=new DatabaseSync(':memory:');database.exec(fs.readFileSync(new URL('schema.sql',import.meta.url),'utf8'));return {database,DB:{prepare(sql){let values=[];return {bind(...v){values=v;return this;},async first(){return database.prepare(sql).get(...values)||null;},async all(){return {results:database.prepare(sql).all(...values)};},async run(){const result=database.prepare(sql).run(...values);return {meta:{changes:Number(result.changes)}};}};}},RATE:{limit:async()=>({success:true})}};}
function request(method,auth,data,revision){const headers={Authorization:'Bearer '+auth};if(revision!==undefined)headers['If-Match']=String(revision);return new Request('https://sync.example/v1/progress',{method,headers,body:data?JSON.stringify(data):undefined});}
test('private groups are separated and server stores only opaque encrypted history',async()=>{
 const env=environment(),code=C.create(),other=C.create(),auth=C.auth(code),data={'episode:tt123:1:1':{positionMs:120000,durationMs:1500000,updatedAt:Date.now()}};
 try{
  assert.equal((await worker.fetch(new Request('https://sync.example/v1/progress'),env)).status,401);
  const first=await worker.fetch(request('PUT',auth,C.seal(code,data),0),env);assert.equal(first.status,200);
  const read=await(await worker.fetch(request('GET',auth),env)).json();assert.deepEqual(C.open(code,read.payload),data);
  const unrelated=await(await worker.fetch(request('GET',C.auth(other)),env)).json();assert.equal(unrelated.payload,null);
  const stored=env.database.prepare('SELECT payload FROM rooms').get().payload;assert(!stored.includes('episode:tt123'));assert(!stored.includes(code));
 }finally{env.database.close();}
});
test('optimistic revisions reject stale writers instead of overwriting another device',async()=>{
 const env=environment(),code=C.create(),auth=C.auth(code),body=C.seal(code,{});
 try{assert.equal((await worker.fetch(request('PUT',auth,body,0),env)).status,200);
 const answers=await Promise.all([worker.fetch(request('PUT',auth,body,1),env),worker.fetch(request('PUT',auth,body,1),env)]);assert.deepEqual(answers.map(r=>r.status).sort(),[200,409]);
 assert.equal((await worker.fetch(request('PUT',auth,body),env)).status,400);
 }finally{env.database.close();}
});
test('rate limits and payload limits fail without altering stored history',async()=>{
 const env=environment(),code=C.create();try{
  env.RATE.limit=async()=>({success:false});assert.equal((await worker.fetch(request('GET',C.auth(code)),env)).status,429);
  env.RATE.limit=async()=>({success:true});const huge=new Request('https://sync.example/v1/progress',{method:'PUT',headers:{Authorization:'Bearer '+C.auth(code),'If-Match':'0'},body:'x'.repeat(180001)});assert.equal((await worker.fetch(huge,env)).status,413);
  assert.equal(env.database.prepare('SELECT COUNT(*) AS total FROM rooms').get().total,0);
 }finally{env.database.close();}
});
test('pairing rejects typos; encrypted progress rejects a wrong key and tampering',()=>{
 const code=C.create(),other=C.create(),value=C.seal(code,{});assert.equal(C.auth(code).length,43);assert.throws(()=>C.parse(code.slice(0,-1)+(code.endsWith('0')?'1':'0')));assert.throws(()=>C.open(other,value));
 const modified={...value,body:Buffer.alloc(Buffer.from(value.body,'base64').length).toString('base64')};assert.throws(()=>C.open(code,modified));
});

test('short pairing is encrypted, expires and can be consumed only once',async()=>{
 const env=environment(),P=require('../desktop/overlay/butter-provider/streamnest-pairing');
 const recovery=C.create(),short=P.create(),id=P.lookup(short),payload=P.seal(short,recovery);
 const req=method=>new Request('https://sync.example/v1/pair/'+id,{method,headers:{Authorization:'Bearer '+C.auth(recovery)},body:method==='PUT'?payload:undefined});
 try{
  assert.equal((await worker.fetch(req('PUT'),env)).status,200);
  assert.equal((await worker.fetch(req('PUT'),env)).status,409);
  const results=await Promise.all([worker.fetch(req('POST'),env),worker.fetch(req('POST'),env)]);
  assert.deepEqual(results.map(r=>r.status).sort(),[200,404]);
  const doc=await results.find(r=>r.status===200).json();
  assert.equal(P.open(short,doc.payload),recovery);
  assert.throws(()=>P.open(P.create(),doc.payload));
  assert.equal((await worker.fetch(req('PUT'),env)).status,200);
  env.database.prepare('UPDATE pairing_tickets SET expires_at=0').run();
  assert.equal((await worker.fetch(req('POST'),env)).status,404);
 }finally{env.database.close();}
});

const groupRequest=(code,method,data)=>new Request('https://sync.example/v1/group',{method,headers:{Authorization:'Bearer '+C.auth(code)},body:data?JSON.stringify(data):undefined});
const device={deviceId:'a'.repeat(32),deviceName:'TheBeast'};
test('existing group identity/history survive naming and device registration',async()=>{
 const env=environment(),code=C.create();try{
  const payload=C.seal(code,{'episode:tt1:1:4':{positionMs:12345}});
  await worker.fetch(request('PUT',C.auth(code),payload,0),env);
  assert.equal((await worker.fetch(groupRequest(code,'PUT',{...device,groupName:'Home'}),env)).status,200);
  let read=await(await worker.fetch(groupRequest(code,'PUT',{deviceId:'b'.repeat(32),deviceName:'MSI'}),env)).json();
  assert.equal(read.name,'Home');assert.equal(read.devices.length,2);
  assert.equal((await(await worker.fetch(groupRequest(C.create(),'GET'),env)).json()).devices.length,0);
  const progress=await(await worker.fetch(request('GET',C.auth(code)),env)).json();assert.deepEqual(C.open(code,progress.payload),C.open(code,payload));assert.equal(progress.revision,1);
  await worker.fetch(groupRequest(code,'PUT',{...device,groupName:'Family'}),env);
  read=await(await worker.fetch(groupRequest(code,'GET'),env)).json();assert.equal(read.name,'Family');assert.equal(read.devices.length,2);
  await worker.fetch(groupRequest(code,'DELETE',device),env);
  assert.equal((await(await worker.fetch(groupRequest(code,'GET'),env)).json()).devices.length,1);
  assert.equal((await(await worker.fetch(request('GET',C.auth(code)),env)).json()).revision,1);
 }finally{env.database.close();}
});
test('metadata validates names, payloads and device IDs without touching history',async()=>{
 const env=environment(),code=C.create();try{
  for(const body of [{...device,groupName:''},{...device,deviceName:'x'.repeat(65)},{...device,deviceId:'bad'},{...device,groupName:'bad\nname'}])assert.equal((await worker.fetch(groupRequest(code,'PUT',body),env)).status,400);
  assert.equal((await worker.fetch(new Request('https://sync.example/v1/group'),env)).status,401);
  assert.equal((await worker.fetch(groupRequest(code,'PUT',{...device,extra:'x'.repeat(5000)}),env)).status,413);
  assert.equal(env.database.prepare('SELECT COUNT(*) AS total FROM sync_devices').get().total,0);
 }finally{env.database.close();}
});
test('private owner data denies missing/wrong keys and excludes encrypted history and credentials',async()=>{
 const env=environment(),code=C.create();env.ADMIN_TOKEN='owner-secret-'.repeat(4);try{
  await worker.fetch(groupRequest(code,'PUT',{...device,groupName:'Home'}),env);
  const req=token=>new Request('https://sync.example/admin/data',{headers:token?{Authorization:'Bearer '+token}:{}});
  assert.equal((await worker.fetch(req(),env)).status,401);assert.equal((await worker.fetch(req('wrong'),env)).status,401);
  const answer=await worker.fetch(req(env.ADMIN_TOKEN),env);assert.equal(answer.status,200);const text=await answer.text();assert(text.includes('TheBeast'));assert(!text.includes(code));assert(!text.includes(C.auth(code)));assert(!text.includes('payload'));
 }finally{env.database.close();}
});

test('disconnect and expiry remove device metadata without confusing it with online presence',async()=>{
 const env=environment(),code=C.create();try{
  await worker.fetch(groupRequest(code,'PUT',{...device,groupName:'Home'}),env);
  env.database.prepare('UPDATE sync_devices SET seen_at=?').run(Date.now()-91*86400000);
  assert.equal((await(await worker.fetch(groupRequest(code,'GET'),env)).json()).devices.length,0);
  await worker.scheduled({},env);
  assert.equal(env.database.prepare('SELECT COUNT(*) AS total FROM sync_devices').get().total,0);
  await worker.fetch(groupRequest(code,'PUT',device),env);
  await worker.fetch(request('DELETE',C.auth(code)),env);
  assert.equal(env.database.prepare('SELECT COUNT(*) AS total FROM sync_devices').get().total,0);
  assert.equal(env.database.prepare('SELECT COUNT(*) AS total FROM sync_groups').get().total,0);
 }finally{env.database.close();}
});


test('diagnostic consent, isolation, privacy, owner authorization and deletion',async()=>{
 const env=environment(),code=C.create(),other=C.create();env.ADMIN_TOKEN='x'.repeat(40);
 const endpoint=(method,body,c=code)=>new Request('https://sync.example/v1/diagnostics',{method,headers:{Authorization:'Bearer '+C.auth(c)},body:JSON.stringify(body)});
 const event={id:'b'.repeat(32),session:'c'.repeat(32),type:'player.video_ready',level:'info',at:Date.now(),data:{elapsedMs:1400,title:'Private title',password:'secret',path:'C:/private',magnet:'magnet:foo'}};
 const body={schema:1,consent:true,deviceId:device.deviceId,config:{platform:'Windows',model:'Laptop',password:'secret',gpu:'https://private',totalRamMb:8192},events:[event]};
 try{
  assert.equal((await worker.fetch(endpoint('POST',body),env)).status,403);
  await worker.fetch(groupRequest(code,'PUT',device),env);
  assert.equal((await worker.fetch(endpoint('POST',{...body,consent:false}),env)).status,400);
  assert.equal((await worker.fetch(endpoint('POST',body,other),env)).status,403);
  assert.equal((await worker.fetch(endpoint('POST',body),env)).status,200);
  const stored=env.database.prepare('SELECT * FROM diagnostic_events').get(),group=stored.room_id;
  const url='https://sync.example/admin/diagnostics?group='+group+'&device='+device.deviceId;
  assert.equal((await worker.fetch(new Request(url),env)).status,401);
  const req=method=>new Request(url,{method,headers:{Authorization:'Bearer '+env.ADMIN_TOKEN}});
  const report=await(await worker.fetch(req('GET'),env)).json();
  assert.deepEqual(report.config,{platform:'Windows',model:'Laptop',totalRamMb:8192});assert.deepEqual(report.events[0].data,{elapsedMs:1400});
  assert.equal((await worker.fetch(endpoint('POST',body),env)).status,429);
  assert.equal((await worker.fetch(req('DELETE'),env)).status,200);
  assert.equal(env.database.prepare('SELECT count(*) AS n FROM diagnostic_events').get().n,0);
  await worker.fetch(endpoint('POST',body),env);
  await worker.fetch(groupRequest(code,'DELETE',device),env);
  assert.equal(env.database.prepare('SELECT count(*) AS n FROM diagnostic_events').get().n,0);
 }finally{env.database.close();}
});
test('diagnostics expiry, bounded batches and unknown events',async()=>{
 const env=environment(),code=C.create();try{
  await worker.fetch(groupRequest(code,'PUT',device),env);
  const send=events=>worker.fetch(new Request('https://sync.example/v1/diagnostics',{method:'POST',headers:{Authorization:'Bearer '+C.auth(code)},body:JSON.stringify({schema:1,consent:true,deviceId:device.deviceId,events})}),env),event={id:'b'.repeat(32),session:'c'.repeat(32),type:'app.start',level:'info',at:Date.now()};
  assert.equal((await send(Array(26).fill(event))).status,400);assert.equal((await send([{...event,type:'private.title'}])).status,400);assert.equal((await send([event])).status,200);
  env.database.prepare('UPDATE diagnostic_events SET received_at=1').run();env.database.prepare('UPDATE diagnostic_devices SET updated_at=1').run();
  await worker.scheduled({},env);assert.equal(env.database.prepare('SELECT count(*) AS n FROM diagnostic_events').get().n,0);assert.equal(env.database.prepare('SELECT count(*) AS n FROM diagnostic_devices').get().n,0);
 }finally{env.database.close();}
});
