 'use strict';
const net=require('./streamnest-net');
function parse(doc,imdb,season,episode){
 const wanted=imdb.replace(/^tt/,'');if(String(doc.imdb_id).replace(/^0+/,'')!==wanted.replace(/^0+/,'')||!Array.isArray(doc.torrents))throw Error('Independent series identity mismatch');
 const out=[],seen=new Set();
 for(const row of doc.torrents){if(!row||String(row.imdb_id).replace(/^0+/,'')!==wanted.replace(/^0+/,'')||Number(row.season)!==season||Number(row.episode)!==episode)continue;
  const title=String(row.title||row.filename||''),m=title.match(/(?:^|[^a-z0-9])s(\d{1,2})e(\d{1,4})(?!\d)/i),hash=String(row.hash||'').toLowerCase(),quality=require('./streamnest-episodes').quality(title);
  if(!m||Number(m[1])!==season||Number(m[2])!==episode||/s\d{1,2}e\d{1,4}(?:e\d|[-+]e?\d)/i.test(title)||!quality||!/^[a-f0-9]{40}$/.test(hash)||seen.has(hash)||!(Number(row.seeds)>0))continue;
  seen.add(hash);let url='magnet:?xt=urn:btih:'+hash;
  try{const old=new URL(row.magnet_url);if(old.protocol==='magnet:')for(const tracker of old.searchParams.getAll('tr').slice(0,12))if(/^(https?|udp):\/\/\S+$/.test(tracker))url+='&tr='+encodeURIComponent(tracker);}catch(e){}
  out.push({url,quality,title,seed:Number(row.seeds),peer:Number(row.peers)||0,size:Number(row.size_bytes)||0,fileIndex:-1,file:'',provider:'EZTV',source:''});
 }return out;
}
function create(get=net){const scans=new Map();return {async find(imdb,season,episode,deadline=Date.now()+6000){
 if(!/^tt\d+$/.test(imdb)||season<0||episode<=0)return [];
 let scan=scans.get(imdb);if(!scan||Date.now()-scan.time>300000){scan={time:Date.now(),pages:new Map(),next:2};scans.set(imdb,scan);while(scans.size>8)scans.delete(scans.keys().next().value);}
 const fetchPage=async page=>{if(!scan.pages.has(page)){const doc=await get('https://eztvx.to/api/get-torrents?imdb_id='+imdb.slice(2)+'&limit=100&page='+page,deadline);parse(doc,imdb,season,episode);if(Number(doc.page)!==page||!Number.isInteger(Number(doc.torrents_count))||Number(doc.torrents_count)<0||doc.torrents.length>100)throw Error('Invalid independent series page');scan.pages.set(page,doc);while(scan.pages.size>6){const victim=[...scan.pages.keys()].find(p=>p!==1&&p!==page);scan.pages.delete(victim);}}return scan.pages.get(page);};
 const first=await fetchPage(1),total=Math.max(1,Math.min(100,Math.ceil(Number(first.torrents_count||0)/100))),pages=[1];scan.lastPage=total;
 if(total>1)pages.push(total);if(total>2){if(scan.next>=total)scan.next=2;pages.push(scan.next++);}
 for(const page of pages)await fetchPage(page);
 const unique=new Map();for(const doc of scan.pages.values())for(const row of parse(doc,imdb,season,episode))unique.set(row.url,row);
 if(!unique.size&&scan.pages.size<total)throw Error('Independent source scan incomplete; retry to search more pages');return [...unique.values()];
 },retry(){for(const scan of scans.values()){scan.pages.delete(1);scan.pages.delete(scan.lastPage);}},invalidate(){scans.clear();}};}
module.exports=Object.assign(create(),{create,parse});
