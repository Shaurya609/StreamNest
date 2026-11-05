import {test} from 'node:test';
import assert from 'node:assert/strict';
import {DatabaseSync} from 'node:sqlite';
import fs from 'node:fs';
import {createRequire} from 'node:module';
import worker from './worker.mjs';
const require=createRequire(import.meta.url),C=require('../desktop/overlay/butter-provider/streamnest-sync-crypto');
function environment(){const database=new DatabaseSync(':memory:');database.exec(fs.readFileSync(new URL('schema.sql',import.meta.url),'utf8'));return {database,DB:{prepare(sql){let values=[];return {bind(...v){values=v;return this;},async first(){return database.prepare(sql).get(...values)||null;},async run(){const result=database.prepare(sql).run(...values);return {meta:{changes:Number(result.changes)}};}};}},RATE:{limit:async()=>({success:true})}};}
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
