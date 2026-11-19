const {test}=require('node:test'),assert=require('node:assert/strict'),crypto=require('crypto'),H=require('../overlay/butter-provider/streamnest-piece-hash');
const sync=b=>crypto.createHash('sha1').update(b).digest('hex');
function original(b,cb){process.nextTick(()=>cb(sync(b)));}original.sync=sync;
test('large piece hashing yields and preserves exact SHA1 and callback accounting',async()=>{
 const data=crypto.randomBytes(1048609),queue=[],budget={pendingBytes:0,peakPendingBytes:0};let result,calls=0;
 const hash=H.create(original,{budget,schedule:f=>queue.push(f)});hash(data,h=>{result=h;calls++;});
 assert.equal(result,undefined);assert.equal(budget.pendingBytes,data.length);let steps=0;
 while(queue.length){queue.shift()();steps++;}
 assert.equal(steps,5);assert.equal(result,sync(data));assert.equal(calls,1);assert.equal(budget.pendingBytes,0);assert.equal(hash.sync,original.sync);
});
test('small data retains original path and hash changes when bytes are corrupted',async()=>{
 let calls=0;const small=(b,cb)=>{calls++;cb(sync(b));};small.sync=sync;const hash=H.create(small);
 hash(Buffer.from('abc'),h=>assert.equal(h,sync('abc')));assert.equal(calls,1);
 const b=Buffer.alloc(1048576,7),expected=sync(b);b[100]^=1;
 assert.notEqual(await new Promise(resolve=>hash(b,resolve)),expected);
});
test('hash backlog participates in verified-write request backpressure',()=>{
 const t={store:{put(){}},_request(){return true;},pieceLength:1048576};require('../overlay/butter-provider/streamnest-transfer-budget').install(t);
 const old=H.state.pendingBytes;try{H.state.pendingBytes=64*1024*1024;assert.equal(t._request(),false);H.state.pendingBytes=0;assert.equal(t._request(),true);}finally{H.state.pendingBytes=old;}
});
