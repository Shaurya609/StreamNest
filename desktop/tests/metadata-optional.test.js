"use strict";
const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('vm'),fs=require('fs'),E=require('events');
function client(status){let calls=0;const reports=[],module={exports:{}};const https={get(url,options,done){calls++;const req=new E();req.setTimeout=()=>{};req.destroy=()=>{};queueMicrotask(()=>{const response=new E();response.statusCode=status;response.headers={};response.resume=()=>{};done(response);req.emit('close');});return req;}};vm.runInNewContext(fs.readFileSync('desktop/overlay/butter-provider/streamnest-net.js','utf8'),{module,URL,Date,Map,setTimeout,clearTimeout,require:n=>n==='https'?https:{failure:(...a)=>reports.push(a)}});return {get:module.exports,reports,calls:()=>calls};}
test('optional missing artwork metadata is deduplicated and cached without hiding required failures',async()=>{const c=client(404),url='https://api.tvmaze.com/lookup/shows?imdb=tt123';assert.deepEqual(await Promise.all([c.get(url,undefined,{allowNotFound:true}),c.get(url,undefined,{allowNotFound:true})]),[null,null]);assert.equal(await c.get(url,undefined,{allowNotFound:true}),null);assert.equal(c.calls(),1);assert.equal(c.reports.length,0);await assert.rejects(c.get(url),/HTTP 404/);assert.equal(c.calls(),2);assert.equal(c.reports[0][4].metadataProvider,'tvmaze');});
test('optional lookup still reports network/service failures with safe provider context',async()=>{const c=client(503);await assert.rejects(c.get('https://v3-cinemeta.strem.io/meta/series/tt123.json',undefined,{allowNotFound:true}),/HTTP 503/);assert.equal(c.reports.length,1);assert.equal(c.reports[0][4].metadataProvider,'cinemeta');});

test('required concurrent metadata shares transport while callers keep independent results',async()=>{
 let calls=0;const module={exports:{}};
 const https={get(url,options,done){calls++;const req=new E();req.setTimeout=()=>{};req.destroy=()=>{};setTimeout(()=>{const response=new E();response.statusCode=200;response.headers={};response.setEncoding=()=>{};done(response);response.emit('data','{"meta":{"id":"tt123"}}');response.emit('end');req.emit('close');},10);return req;}};
 vm.runInNewContext(fs.readFileSync('desktop/overlay/butter-provider/streamnest-net.js','utf8'),{module,URL,Date,Map,JSON,setTimeout,clearTimeout,require:n=>n==='https'?https:{failure(){}}});
 const [a,b]=await Promise.all([module.exports('https://fixture.example/meta'),module.exports('https://fixture.example/meta')]);assert.equal(calls,1);a.meta.id='changed';assert.equal(b.meta.id,'tt123');
 await module.exports('https://fixture.example/meta');assert.equal(calls,2);
});
test('required request deadlines remain independent',async()=>{
 const c=client(503),url='https://fixture.example/meta';await Promise.allSettled([c.get(url,Date.now()+1000),c.get(url,Date.now()+5000)]);assert.equal(c.calls(),2);
});
