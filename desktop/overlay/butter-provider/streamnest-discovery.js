 'use strict';
const net=require('./streamnest-net');
function match(release,title,row){
 const text=release.replace(/^(?:\[[^\]]+\]\s*)+/, '');
 if(!text.toLowerCase().startsWith(title.toLowerCase()))return 0;
 let tail=text.slice(title.length);if(!/^(?:\s|\[|\(|$)/.test(tail))return 0;
 tail=tail.replace(/^\s*\((?:TV|[12][0-9]{3})\)/i,'');
 const single=tail.match(/^\s*-\s*(\d{1,4})(?:v\d+)?(?=\s|\[|\(|\.|$)/);
 if(single&&!/^\s*[-~]/.test(tail.slice(single[0].length)))return row.absolute>0&&Number(single[1])===Number(row.absolute)?1:0;
 const range=tail.match(/^\s*(?:-\s*)?[\[(]?(\d{1,4})\s*[-~]\s*(\d{1,4})(?=\s|[)\]]|$)/);
 return range&&row.absolute>0&&Number(range[1])<=row.absolute&&row.absolute<=Number(range[2])?2:0;
}
function candidates(items,title,row){const E=require('./streamnest-episodes');return items.map(item=>{
 const kind=match(String(item.title||''),title,row),url=String(item.link||''),hash=String(item['nyaa:infoHash']||'').toLowerCase(),quality=E.quality(item.title),seed=Number(item['nyaa:seeders']);
 return String(item['nyaa:trusted']).toLowerCase()==='yes'&&kind&&quality&&seed>0&&/^https:\/\/nyaa\.si\/download\/\d+\.torrent$/.test(url)&&/^[a-f0-9]{40}$/.test(hash)?{url,hash,title:item.title,quality,seed,pack:kind===2}:null;
 }).filter(Boolean).sort((a,b)=>b.seed-a.seed);}
function indexed(candidate,parsed,row){
 if(!parsed||String(parsed.infoHash).toLowerCase()!==candidate.hash)throw Error('Torrent identity mismatch');
 const E=require('./streamnest-episodes'),files=parsed.files.map(f=>Object.assign({},f,{name:require('path').basename(f.path)}));
 const index=E.selectFile(files,Object.assign({},row,{streamnestStrictEpisode:candidate.pack}));
 return {url:candidate.url,quality:candidate.quality,title:candidate.title,seed:candidate.seed,peer:0,size:files[index].length,fileIndex:index,file:files[index].name,provider:'Nyaa',source:''};
}
function key(s){const hash=String(s.url||'').match(/(?:xt=urn:btih:)([a-f0-9]{40})/i);return (hash?hash[1].toLowerCase():s.url)+'|'+s.fileIndex;}
function create(options={}){
 const get=options.get||net,text=options.text||((url,deadline)=>net.text(url,0,deadline)),bytes=options.bytes||net.bytes;
 const parseXML=options.parseXML||(xml=>new Promise((yes,no)=>require('xml2js').parseString(xml,{explicitArray:false,trim:true},(e,v)=>e?no(e):yes(v))));
 const parseTorrent=options.parseTorrent||(b=>require('parse-torrent')(b)),cache=new Map(),pending=new Map();
 async function anime(title,row,deadline){
  const query='"'+title.replace(/"/g,'')+'" '+String(row.absolute).padStart(2,'0');
  const xml=await text('https://nyaa.si/?page=rss&c=1_2&f=2&q='+encodeURIComponent(query),deadline);
  if(/<!DOCTYPE|<!ENTITY/i.test(xml)||xml.includes('\0'))throw Error('Unsafe XML');
  const doc=await parseXML(xml);
  if(!doc.rss||!doc.rss.channel)throw Error('Invalid anime RSS');
  const raw=doc.rss.channel.item,items=raw?(Array.isArray(raw)?raw:[raw]):[],found=[];let failure;
  for(const c of candidates(items,title,row).slice(0,2))try{found.push(indexed(c,parseTorrent(await bytes(c.url,deadline,8388608)),row));}catch(e){failure=e;}
  if(!found.length&&failure)throw failure;return found;
 }
 async function search(id,season,episode,context){
  const deadline=Date.now()+18000,all=[];let failure;
  try{const doc=await get('https://torrentio.strem.fun/stream/series/'+id+':'+season+':'+episode+'.json',Math.min(deadline,Date.now()+6000));if(!Array.isArray(doc.streams))throw Error('Invalid index response');all.push(...require('./streamnest-episodes').parseStreams(doc));}catch(e){failure=e;}
  if(!all.length&&context.kitsuId>0&&context.kitsuEpisode>0)try{const doc=await get('https://torrentio.strem.fun/stream/series/kitsu:'+context.kitsuId+':'+context.kitsuEpisode+'.json',Math.min(deadline,Date.now()+4000));if(!Array.isArray(doc.streams))throw Error('Invalid index response');all.push(...require('./streamnest-episodes').parseStreams(doc));}catch(e){failure=e;}
  if(context.animeMapped===true&&season>0&&context.absolute>0&&context.streamnestSeriesTitle)try{all.push(...await anime(context.streamnestSeriesTitle,Object.assign({},context,{season,episode}),deadline));}catch(e){failure=e;}
  const unique=new Map();for(const s of all)if(!unique.has(key(s)))unique.set(key(s),s);
  if(!unique.size&&failure)throw Error('Source lookup incomplete. A provider is unavailable; retry source lookup.');
  return [...unique.values()];
 }
 async function find(id,season,episode,context={}){
  if(!/^tt\d+$/.test(id)||!Number.isInteger(Number(season))||season<0||!Number.isInteger(Number(episode))||episode<=0)return [];
  const idKey=[id,season,episode,context.kitsuId,context.kitsuEpisode,context.absolute,context.animeMapped,context.streamnestSeriesTitle].join(':'),old=cache.get(idKey);
  if(old&&Date.now()-old.time<(old.rows.length?300000:30000))return old.rows.slice();
  if(pending.has(idKey))return pending.get(idKey);
  if(pending.size>=4)throw Error('Source lookup busy. Please retry shortly.');
  const task=search(id,Number(season),Number(episode),context).then(rows=>{cache.set(idKey,{time:Date.now(),rows});while(cache.size>24)cache.delete(cache.keys().next().value);return rows.slice();}).finally(()=>pending.delete(idKey));pending.set(idKey,task);return task;
 }
 return {find,invalidate(){cache.clear();},anime};
}
module.exports=Object.assign(create(),{create,match,candidates,indexed,key});
