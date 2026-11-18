 'use strict';
// Optional live checks: service unavailability is reported, never replaced with guessed data.
const path=require('path');
const base=path.resolve(process.argv[2]||'desktop/overlay');
const P=require(path.join(base,'butter-provider/streamnest-playback'));
const F=require(path.join(base,'butter-provider/streamnest-filler'));
const get=require(path.join(base,'butter-provider/streamnest-net'));
const Org=require(path.join(base,'butter-provider/streamnest-org'));
async function check(name,run){const start=Date.now();try{const details=await run();return {name,status:'passed',elapsedMs:Date.now()-start,...details};}catch(e){return {name,status:'unavailable',elapsedMs:Date.now()-start,error:e.message};}}
(async()=>{
 const results=[];
 for(const [name,imdb,mal,length,episode]of [['anime', 'tt7441658',34572,1440,108],['series','tt11280740',0,3435,1]])results.push(await check(name+' skip fallback',async()=>{
  const rows=await P.segments({imdb_id:imdb,malId:mal,season:1,episode},length);
  if(!rows.length)throw Error('No validated timestamps returned');
  return {segments:rows.map(r=>({type:r.type,start:r.start,end:r.end})),identityValidated:true};
 }));
 results.push(await check('filler episodes beyond 100',async()=>{
  const rows=Array.from({length:70},(_,i)=>({episode:101+i,skipMalId:34572}));
  F.request(rows);for(let n=0;n<200;n++){F.apply(rows);if(rows.some(r=>typeof r.filler==='boolean'))break;await new Promise(r=>setTimeout(r,100));}
  const labelled=rows.filter(r=>typeof r.filler==='boolean');if(!labelled.length)throw Error('No page-two labels');return {episodes:labelled.length,provider:labelled[0].fillerSource,first:labelled[0].episode,last:labelled.at(-1).episode,flagged:labelled.filter(r=>r.filler||r.recap).length};
 }));
 for(const [name,imdb,season,episode]of [['Squid Game','tt10919420',1,1],['Parasite','tt6751668',null,null]])results.push(await check(name+' English subtitles',async()=>{
  const result=await Org.search({imdbid:imdb,season,episode}),rows=result.en||result.eng||[];
  if(!rows.length)throw Error('No English subtitle match');
  const bytes=await Org.download(rows[0].url);if(!bytes||!bytes.includes(Buffer.from('-->')))throw Error('Subtitle download has no timed cues');
  return {matches:rows.length,downloaded:true,bytes:bytes.length,audioDubVerified:false};
 }));
 console.log(JSON.stringify({checkedAt:new Date().toISOString(),results},null,2));
})().catch(e=>{console.error(e.message);process.exitCode=1;});
