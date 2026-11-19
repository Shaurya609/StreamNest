const crypto=require('crypto'),{performance}=require('perf_hooks'),H=require('../../desktop/overlay/butter-provider/streamnest-piece-hash');
const mode=process.argv[2];if(!['previous','cooperative'].includes(mode))throw Error('Use previous or cooperative');
const bytes=Buffer.alloc(128*1024*1024,37),expected=crypto.createHash('sha1').update(bytes).digest('hex');
const original=(b,cb)=>{const digest=crypto.createHash('sha1').update(b).digest('hex');process.nextTick(()=>cb(digest));};original.sync=b=>crypto.createHash('sha1').update(b).digest('hex');
const hash=mode==='cooperative'?H.create(original):original;let last=performance.now(),gap=0,ticks=0;
const beat=setInterval(()=>{const now=performance.now();gap=Math.max(gap,now-last);last=now;ticks++;},2);
setTimeout(()=>{const start=performance.now(),cpu=process.cpuUsage(),memory=process.memoryUsage();hash(bytes,digest=>{if(digest!==expected)throw Error('SHA1 mismatch');const elapsed=performance.now()-start,cost=process.cpuUsage(cpu);setTimeout(()=>{clearInterval(beat);console.log(JSON.stringify({mode,bytes:bytes.length,sha1:digest,elapsedMs:elapsed,cpuMs:(cost.user+cost.system)/1000,maxHeartbeatGapMs:gap,ticks,externalDelta:process.memoryUsage().external-memory.external}));},15);});},15);
