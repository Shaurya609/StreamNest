'use strict';
const net=require('./streamnest-net');
const core=()=>require('./streamnest-episodes');
const cache=new Map();
const array=x=>Array.isArray(x)?x:x?[x]:[];
function records(items) {
  const out=[];
  for(const item of items) {
    if(String(item['nyaa:trusted']).toLowerCase()!=='yes')continue;
    const title=String(item.title||''),url=String(item.link||''),quality=core().quality(title),seeds=Number(item['nyaa:seeders'])||0;
    if(!quality||seeds<=0||!/^https:\/\/nyaa\.si\/download\/\d+\.torrent$/.test(url))continue;
    if(!/\s-\s\d{1,4}(?:v\d+)?(?=\s|\[|\(|$)/.test(title)||/\b(batch|complete)\b|\d{1,4}[-~]\d{1,4}/i.test(title))continue;
    out.push({imdb:'nyaa:'+url.replace(/\D/g,''),title,year:new Date(item.pubDate).getUTCFullYear()||'',
      animeRelease:true,language:'ja',genres:['Animation'],description:'Individual trusted release. Audio and embedded subtitles depend on the release.',
      uploaded:Date.parse(item.pubDate)||0,releaseSeeds:seeds,
      items:[{quality,torrent_url:url,torrent_seeds:seeds,file:title}]});
  }
  return out;
}
async function releases(query) {
  const old=cache.get(query);if(old&&Date.now()-old.time<300000)return old.rows;
  const text=await net.text('https://nyaa.si/?page=rss&c=1_2&f=2&q='+encodeURIComponent(query||''));
  if(/<!DOCTYPE|<!ENTITY/i.test(text)||text.includes('\0'))throw Error('Unsafe XML declaration');
  const doc=await new Promise((resolve,reject)=>require('xml2js').parseString(text,{explicitArray:false,trim:true},(e,value)=>e?reject(e):resolve(value)));
  const rows=records(array(doc.rss&&doc.rss.channel&&doc.rss.channel.item));
  cache.set(query,{time:Date.now(),rows});while(cache.size>4)cache.delete(cache.keys().next().value);return rows;
}
async function list(filters) {
  const rows=(await releases(filters.keywords||'')).filter(r=>!filters.type||filters.type==='All'||r.items[0].quality===filters.type);
  const key=filters.sorter||'last added',normal=key==='title'?1:-1,direction=Number(filters.order||normal)===normal?1:-1;
  rows.sort((a,b)=>direction*(key==='title'?a.title.localeCompare(b.title):key==='trending'||key==='popularity'?b.releaseSeeds-a.releaseSeeds:key==='year'?(Number(b.year)||0)-(Number(a.year)||0):b.uploaded-a.uploaded)||a.imdb.localeCompare(b.imdb));
  const start=Math.max(0,(Number(filters.page)||1)-1)*24;
  return {rows:rows.slice(start,start+24),hasMore:start+24<rows.length};
}
function attachRows(episodes,rows,title) {
  const clean=s=>s.toLowerCase().replace(/[^\p{L}\p{N}]/gu,''),byAbsolute=new Map(episodes.filter(e=>e.absolute>0).map(e=>[e.absolute,e]));
  for(const row of rows) {
    const label=row.title.replace(/^(?:\[[^\]]+\]\s*)+/,''),m=label.match(/^(.+?)\s-\s(\d{1,4})(?:v\d+)?(?=\s|\[|\(|$)/);
    if(!m||clean(m[1])!==clean(title))continue;
    const e=byAbsolute.get(Number(m[2]));if(!e)continue;
    for(const item of row.items)e.streamnestSources.push({url:item.torrent_url,quality:item.quality,title:row.title,seed:item.torrent_seeds,peer:0,size:0,file:'',provider:'Nyaa',source:''});
    e.torrents=core().qualities(e.streamnestSources);
  }
  return episodes;
}
async function attach(episodes,title) { return attachRows(episodes,await releases(title),title); }
module.exports={records,list,attach,attachRows};
