"use strict";
const Fresh=require('./streamnest-fresh'),Hindi=require('./streamnest-hindi');let snapshot,refreshed=0,inflight;
const seeds=m=>Math.max(0,...(m.items||[]).map(t=>Number(t.torrent_seeds)||0));
function compare(sort){return (a,b)=>{let n=0;switch(sort){case 'title':n=a.title.toLowerCase()<b.title.toLowerCase()?-1:a.title.toLowerCase()>b.title.toLowerCase()?1:0;break;case 'year':n=Number(b.year)-Number(a.year);break;case 'rating':n=Number(b.rating)-Number(a.rating);break;case 'last added':n=(b.date_uploaded_unix||0)-(a.date_uploaded_unix||0);break;case 'popularity':n=(a.popularityRank||2147483647)-(b.popularityRank||2147483647)||seeds(b)-seeds(a);break;default:n=seeds(b)-seeds(a);}return n||(a.imdb<b.imdb?-1:a.imdb>b.imdb?1:0);};}
try {const doc=require('./streamnest-resilience').catalog('movies')||require('./movies-hindi-v1.json');snapshot=doc.movies.map(Fresh.normalize).filter(m=>m.language==='hi'&&m.items.length);refreshed=doc.saved;} catch(e){}
async function build() {
 const all=new Map();let failure,responding=0;
 for(const sort of ['date_added','seeds','download_count'])try{
  const rows=await Fresh.page(sort,'');responding++;
  rows.forEach((row,i)=>{if(!['en','hi','ko'].includes(row.language))return;const m=all.get(row.imdb)||row;if(sort==='download_count')m.popularityRank=i+1;all.set(m.imdb,m);});
 }catch(e){failure=e;}
 if(!responding)throw failure||Error('Movie catalog unavailable');
 for(const row of snapshot||[])if(row.language==='hi'&&row.items.length&&!all.has(row.imdb))all.set(row.imdb,row);
 let regional=[];
 for(let p=1;p<=6;p++)try{regional.push(...(await Hindi.list({page:p},false)).rows.filter(m=>!all.has(m.imdb)));}catch(e){failure=e;}
 try{for(const row of await Fresh.available(regional))if(row.language==='hi'&&row.items.length&&!all.has(row.imdb))all.set(row.imdb,row);}catch(e){failure=e;}
 snapshot=Array.from(all.values());refreshed=Date.now();return snapshot;
}
async function pool(){
 if(snapshot&&snapshot.length){
  if(Date.now()-refreshed>=21600000&&!inflight)inflight=build().catch(()=>snapshot).finally(()=>inflight=null);
  return snapshot;
 }
 if(!inflight)inflight=build().finally(()=>inflight=null);
 return inflight;
}
function select(records,filters,language,pageSize=24){const ids=new Set(),quality=filters.type||'All';let rows=records.filter(m=>{if(ids.has(m.imdb))return false;ids.add(m.imdb);return ['en','hi'].includes(m.language)&&(language==='all'||m.language===language);}).filter(m=>!filters.keywords||m.title.toLowerCase().includes(filters.keywords.toLowerCase())||m.imdb===filters.keywords).filter(m=>!filters.genre||filters.genre==='All'||(m.genres||[]).some(g=>g.toLowerCase().replace(/science[ -]fiction/g,'sci-fi')==filters.genre.toLowerCase().replace(/science[ -]fiction/g,'sci-fi'))).map(m=>Object.assign({},m,{items:(m.items||[]).filter(t=>quality==='All'||quality===t.quality)})).filter(m=>m.items.length);const sort=filters.sorter||'trending',direction=Number(filters.order|| (sort==='title'?1:-1)),normal=sort==='title'?1:-1;rows.sort((a,b)=>(direction===normal?1:-1)*compare(sort)(a,b));const start=Math.max(0,Number(filters.page||1)-1)*pageSize;return {rows:rows.slice(start,start+pageSize),hasMore:start+pageSize<rows.length};}
const sessions=new Map();
async function regionalRecords(filters,language){const key=JSON.stringify(Object.assign({},filters,{page:0,language}));let rows=sessions.get(key);if(!rows||Number(filters.page||1)===1){rows=(await pool()).slice();sessions.set(key,rows);while(sessions.size>4)sessions.delete(sessions.keys().next().value);}return rows;}
function interleave(english,hindi){const rows=[];let h=0;english.forEach((m,i)=>{rows.push(m);if((i+1)%3===0&&h<hindi.length)rows.push(hindi[h++]);});while(h<hindi.length)rows.push(hindi[h++]);return rows;}
async function list(filters,language){
 if(language==='hi')return select(await regionalRecords(filters,language),filters,language);
 const P=require('./streamnest-paged');
 if(language==='all'&&(filters.sorter||'trending')==='trending'&&!filters.keywords){
  const english=await P.list(filters,'en',false,undefined,18),hindi=select(await regionalRecords(filters,'mixed'),filters,'hi',6);
  return {rows:interleave(english.rows,hindi.rows),hasMore:english.hasMore||hindi.hasMore};
 }
 return P.list(filters,language);
}
module.exports={list,select,compare,pool,interleave};
