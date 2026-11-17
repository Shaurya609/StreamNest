import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import fs from 'node:fs';
import {createRequire} from 'node:module';
import worker from './worker.mjs';
const require=createRequire(import.meta.url),C=require('../desktop/overlay/butter-provider/streamnest-sync-crypto');
function environment(){const database=new DatabaseSync(':memory:');database.exec(fs.readFileSync(new URL('schema.sql',import.meta.url),'utf8'));return {database,DB:{prepare(sql){let values=[];return {bind(...v){values=v;return this;},async first(){return database.prepare(sql).get(...values)||null;},async all(){return {results:database.prepare(sql).all(...values)};},syncRun(){const result=database.prepare(sql).run(...values);return {meta:{changes:Number(result.changes)}};},async run(){return this.syncRun();}};},async batch(statements){database.exec('BEGIN IMMEDIATE');try{const result=statements.map(s=>s.syncRun());database.exec('COMMIT');return result;}catch(e){database.exec('ROLLBACK');throw e;}}},RATE:{limit:async()=>({success:true})}};}

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


test('extended diagnostic samples and safe failure locations survive server sanitization',async()=>{
 const {cleanData,cleanConfig,cleanEvent}=await import('./diagnostics.mjs');
 const data=cleanData({service:'player',operation:'playback',audioChannels:6,bufferedMs:10000,videoCodec:'hevc',audioState:'muted',playState:'buffering',frames:['js.streamnest-net:42','app.streamnest.CatalogHttp.get:18','C:/secret.js:4'],causeClass:'IOException',positionMs:123,title:'private',url:'https://private'});
 assert.deepEqual(data.frames,['js.streamnest-net:42','app.streamnest.CatalogHttp.get:18']);assert.equal(data.bufferedMs,10000);assert(!('positionMs' in data));assert(!JSON.stringify(data).includes('private'));assert(!JSON.stringify(data).includes('secret'));
 assert.deepEqual(cleanConfig({hardwareH264:true,hardwareHevc:false,hardwareAv1:true}),{hardwareH264:true,hardwareHevc:false,hardwareAv1:true});
 assert(cleanEvent({id:'a'.repeat(32),session:'b'.repeat(32),type:'player.sample',level:'info',at:Date.now(),data}));
});


test('owner can see separate current activity without exposing encrypted resume history',async()=>{
 const env=environment(),code=C.create(),other=C.create();env.ADMIN_TOKEN='o'.repeat(40);
 const send=(method,data,c=code)=>worker.fetch(new Request('https://sync.example/v1/activity',{method,headers:{Authorization:'Bearer '+C.auth(c)},body:JSON.stringify(data)}),env),body={deviceId:device.deviceId,sharing:true,activity:{title:'One Piece',kind:'animes',season:1,episode:4,state:'playing',url:'https://private',positionMs:123}};
 try{
  assert.equal((await send('PUT',body)).status,403);await worker.fetch(groupRequest(code,'PUT',{...device,groupName:'Fixture'}),env);
  assert.equal((await send('PUT',body,other)).status,403);assert.equal((await send('PUT',{...body,sharing:false})).status,400);
  assert.equal((await send('PUT',body)).status,200);
  const report=await(await worker.fetch(new Request('https://sync.example/admin/data',{headers:{Authorization:'Bearer '+env.ADMIN_TOKEN}}),env)).json(),row=report.groups[0];
  assert.deepEqual(row.activity,{title:'One Piece',kind:'animes',season:1,episode:4,state:'playing'});assert(!JSON.stringify(report).includes('https://private'));assert(!JSON.stringify(report).includes('positionMs'));
  assert.equal((await worker.fetch(new Request('https://sync.example/admin/data'),env)).status,401);
  await send('PUT',{...body,ageMs:3600000,activity:{...body.activity,title:'Old observation'}});assert.equal(JSON.parse(env.database.prepare('SELECT payload FROM watch_activity').get().payload).title,'One Piece');
  await send('DELETE',{deviceId:device.deviceId});assert.equal(env.database.prepare('SELECT count(*) n FROM watch_activity').get().n,0);
 }finally{env.database.close();}
});
test('owner group deletion is confirmed, transactional, isolated and permanently revokes codes',async()=>{
 const env=environment(),code=C.create(),other=C.create();env.ADMIN_TOKEN='o'.repeat(40);
 try{
  await worker.fetch(groupRequest(code,'PUT',{...device,groupName:'Fixture'}),env);await worker.fetch(request('PUT',C.auth(code),C.seal(code,{}),0),env);await worker.fetch(groupRequest(other,'PUT',{...device,groupName:'Keep'}),env);
  const id=env.database.prepare("SELECT id FROM sync_groups WHERE name='Fixture'").get().id;
  env.database.prepare('INSERT INTO watch_activity VALUES (?,?,?,?)').run(id,device.deviceId,'{}',Date.now());
  const url='https://sync.example/admin/groups?group='+id,del=(key,confirmName)=>new Request(url,{method:'DELETE',headers:{Authorization:'Bearer '+key},body:JSON.stringify({confirmName})});
  assert.equal((await worker.fetch(del('wrong','Fixture'),env)).status,401);assert.equal((await worker.fetch(del(env.ADMIN_TOKEN,'Wrong'),env)).status,400);
  const batch=env.DB.batch.bind(env.DB);env.DB.batch=statements=>batch([...statements,env.DB.prepare('DELETE FROM nonexistent_fixture_table')]);
  assert.equal((await worker.fetch(del(env.ADMIN_TOKEN,'Fixture'),env)).status,503);assert(env.database.prepare('SELECT id FROM sync_groups WHERE id=?').get(id));assert.equal(env.database.prepare('SELECT count(*) n FROM revoked_groups').get().n,0);
  env.DB.batch=batch;assert.equal((await worker.fetch(del(env.ADMIN_TOKEN,'Fixture'),env)).status,200);
  for(const table of ['rooms','sync_groups'])assert.equal(env.database.prepare('SELECT count(*) n FROM '+table+' WHERE id=?').get(id).n,0);
  for(const table of ['watch_activity','sync_devices','diagnostic_events','diagnostic_devices'])assert.equal(env.database.prepare('SELECT count(*) n FROM '+table+' WHERE room_id=?').get(id).n,0);
  assert.equal((await worker.fetch(groupRequest(code,'PUT',device),env)).status,410);assert.equal((await worker.fetch(request('GET',C.auth(code)),env)).status,410);assert.equal((await worker.fetch(request('PUT',C.auth(code),C.seal(code,{}),0),env)).status,410);
  assert.equal((await worker.fetch(groupRequest(other,'GET'),env)).status,200);assert.equal(env.database.prepare('SELECT count(*) n FROM sync_groups').get().n,1);
  assert.equal((await worker.fetch(del(env.ADMIN_TOKEN,'Fixture'),env)).status,200);
 }finally{env.database.close();}
});


test('in-flight writes cannot resurrect a group after owner deletion commits',async()=>{
 for(const target of ['progress','group','diagnostics','activity']){
  const env=environment(),code=C.create();env.ADMIN_TOKEN='o'.repeat(40);
  try{
   await worker.fetch(groupRequest(code,'PUT',{...device,groupName:'Race fixture'}),env);const id=env.database.prepare('SELECT id FROM sync_groups').get().id;
   const original=env.DB.prepare.bind(env.DB);let armed=true;const table={progress:'rooms',group:'sync_groups',diagnostics:'diagnostic_devices',activity:'watch_activity'}[target];
   env.DB.prepare=sql=>{const stmt=original(sql),run=stmt.run;stmt.run=async function(){if(armed&&sql.startsWith('INSERT')&&sql.includes('INTO '+table+'(')){armed=false;const deleted=await worker.fetch(new Request('https://sync.example/admin/groups?group='+id,{method:'DELETE',headers:{Authorization:'Bearer '+env.ADMIN_TOKEN},body:JSON.stringify({confirmName:'Race fixture'})}),env);assert.equal(deleted.status,200);}return run.call(this);};return stmt;};
   let req;if(target==='progress')req=request('PUT',C.auth(code),C.seal(code,{}),0);else if(target==='group')req=groupRequest(code,'PUT',device);else {const data=target==='activity'?{deviceId:device.deviceId,sharing:true,activity:{title:'Fixture',kind:'movies',state:'playing'}}:{deviceId:device.deviceId,schema:1,consent:true,events:[{id:'c'.repeat(32),session:'d'.repeat(32),at:Date.now(),type:'app.start',level:'info'}]};req=new Request('https://sync.example/v1/'+target,{method:target==='activity'?'PUT':'POST',headers:{Authorization:'Bearer '+C.auth(code)},body:JSON.stringify(data)});}
   const result=await worker.fetch(req,env);assert([200,410].includes(result.status));assert(!armed,'Race injection did not execute');
   assert.equal(env.database.prepare('SELECT count(*) n FROM sync_groups').get().n,0);assert.equal(env.database.prepare('SELECT count(*) n FROM rooms').get().n,0);for(const t of ['watch_activity','sync_devices','diagnostic_devices','diagnostic_events'])assert.equal(env.database.prepare('SELECT count(*) n FROM '+t).get().n,0,target+' resurrected '+t);
  }finally{env.database.close();}
 }
});
test('activity ages, expiry and invalid title reports cannot create misleading live status',async()=>{const {cleanActivity}=await import('./owner-controls.mjs');for(const title of ['https://private','magnet:?private','C:\\private','bad\nname'])assert.equal(cleanActivity({title,kind:'movies',state:'playing'}),null);const env=environment(),code=C.create();try{await worker.fetch(groupRequest(code,'PUT',device),env);const send=ageMs=>worker.fetch(new Request('https://sync.example/v1/activity',{method:'PUT',headers:{Authorization:'Bearer '+C.auth(code)},body:JSON.stringify({deviceId:device.deviceId,sharing:true,ageMs,activity:{title:'Fixture',kind:'movies',state:'playing'}})}),env);assert.equal((await send(-1)).status,400);assert.equal((await send(120000)).status,200);env.database.prepare('UPDATE watch_activity SET updated_at=1').run();await worker.scheduled({},env);assert.equal(env.database.prepare('SELECT count(*) n FROM watch_activity').get().n,0);}finally{env.database.close();}});
