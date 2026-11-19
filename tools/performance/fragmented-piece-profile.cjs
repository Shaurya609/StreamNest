const fs=require('fs'),path=require('path'),crypto=require('crypto'),{performance}=require('perf_hooks');
const load=require('module').createRequire(path.resolve('desktop/upstream/package.json')),Piece=load('torrent-piece'),H=require('../../desktop/overlay/butter-provider/streamnest-piece-hash');
require('../../desktop/overlay/butter-provider/streamnest-piece-memory').install(Piece);
const mode=process.argv[2];if(!['previous','cooperative'].includes(mode))throw Error('Use previous or cooperative');
const length=128*1024*1024,piece=new Piece(length),expected=crypto.createHash('sha1');
for(let n=0;n<length/16384;n++){const block=Buffer.alloc(16384,n%251);expected.update(block);piece.set(n,block,{});}
const before=process.memoryUsage(),start=performance.now(),bytes=piece.flush(),assembled=process.memoryUsage();
const wanted=expected.digest('hex'),original=load('simple-sha1'),hash=mode==='cooperative'?H.create(original):original;
let last=performance.now(),gap=0;const beat=setInterval(()=>{const now=performance.now();gap=Math.max(gap,now-last);last=now;},2);
setTimeout(()=>{const cpu=process.cpuUsage();hash(bytes,digest=>{if(digest!==wanted)throw Error('Hash mismatch');const cost=process.cpuUsage(cpu);setTimeout(()=>{clearInterval(beat);console.log(JSON.stringify({mode,length,sha1:digest,assemblyExtraExternal:assembled.external-before.external,elapsedMs:performance.now()-start,cpuMs:(cost.user+cost.system)/1000,maxHeartbeatGapMs:gap}));},15);});},15);
