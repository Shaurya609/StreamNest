'use strict';
const get = url=>require('./streamnest-resilience').metadata(url,value=>{
  if(!value||typeof value!=='object'||value.error)return false;
  if(url.includes('/meta/series/')){const id=url.split('/').pop().replace('.json','');return value.meta&&(value.meta.imdb_id||value.meta.id)===id&&Array.isArray(value.meta.videos);}
  if(url.includes('/lookup/shows?imdb='))return value.id>0&&value.externals&&value.externals.imdb===new URL(url).searchParams.get('imdb');
  if(url.includes('api.ani.zip'))return !!value.mappings&&!!value.episodes;
  if(url.includes('/stream/series/'))return Array.isArray(value.streams);
  return Array.isArray(value);
});
const path = require('path');
const cache = new Map();
const camera = name => /\b(cam|hdcam|camrip|telesync|hdts|tsrip)\b/i.test(name || '');
function quality(value) {
  value = String(value || '').replace(/[\r\n]/g, ' ');
  if (camera(value)) return '';
  const match = value.match(/\b(2160p|1080p|720p|576p|480p|360p)\b/i);
  return match ? match[1].toLowerCase() : /\b(sd|dvd|dvdrip|sdtv)\b/i.test(value) ? 'SD' : '';
}
function rank(rows, selected = 'Auto') {
  let remaining = rows.filter(s => s && s.url && !camera(s.title) && quality(s.quality || s.title));
  if (selected !== 'Auto' && selected !== 'All') remaining = remaining.filter(s => quality(s.quality || s.title) === selected);
  const order = ['1080p','720p','576p','480p','360p','SD','2160p'];
  return remaining.slice().sort((a,b) => (Number(b.seed > 0) - Number(a.seed > 0)) ||
    order.indexOf(quality(a.quality || a.title)) - order.indexOf(quality(b.quality || b.title)) ||
    Number(/265|hevc|av1/i.test(a.title || '')) - Number(/265|hevc|av1/i.test(b.title || '')) ||
    (Number(b.seed) || 0) - (Number(a.seed) || 0));
}
function qualities(rows) {
  const result = {};
  for (const source of rank(rows)) { const q = quality(source.quality || source.title); if (!result[q]) result[q] = source; }
  const best = rank(rows)[0];
  return best ? Object.assign({Auto: best}, result) : result;
}
function parseStreams(doc) {
  const out = [], seen = new Set();
  for (const row of doc.streams || []) {
    if (!/^[a-f0-9]{40}$/i.test(row.infoHash || '') || !Number.isInteger(row.fileIdx) || row.fileIdx < 0) continue;
    const label = (row.name || '') + ' ' + (row.title || ''), q = quality(label);
    if (!q || seen.has(row.infoHash.toLowerCase()+':'+row.fileIdx) || camera(label)) continue;
    const key = row.infoHash.toLowerCase()+':'+row.fileIdx;
    seen.add(key);
    const match = (row.title || '').match(/👤\s*(\d+)/);
    let magnet = 'magnet:?xt=urn:btih:' + row.infoHash;
    for (const tracker of (row.sources || []).slice(0,12))
      if (/^tracker:(?:https?|udp):\/\/\S+$/.test(tracker)) magnet += '&tr=' + encodeURIComponent(tracker.slice(8));
    out.push({key, url:magnet, magnet, quality:q, title:label.replace(/[\r\n]/g,' '),
      seed:match ? Number(match[1]) : 0, peer:0, size:0, provider:'Alternative', source:'',
      fileIndex:row.fileIdx, file:row.behaviorHints && row.behaviorHints.filename || ''});
    if (out.length >= 80) break;
  }
  return out;
}
async function alternatives(id, season, episode) {
  if (!/^tt\d+$/.test(id) || Number(season) < 0 || Number(episode) <= 0) return [];
  const key = `${id}:${Number(season)}:${Number(episode)}`, old = cache.get(key);
  if (old && Date.now()-old.time < 600000) return old.rows;
  const rows = parseStreams(await get('https://torrentio.strem.fun/stream/series/'+key+'.json'));
  cache.set(key,{time:Date.now(),rows});
  while(cache.size>12) cache.delete(cache.keys().next().value);
  return rows;
}
function canonical(meta) {
  return (meta.videos || []).filter(e => Number.isInteger(e.season) && Number.isInteger(e.episode)).map(e=>({
    season:e.season,episode:e.episode,title:e.name || e.title || 'Episode '+e.episode,
    overview:e.overview || '',first_aired:Date.parse(e.released || '')/1000 || 0,tvdb_id:e.id || meta.id+':'+e.season+':'+e.episode
  }));
}
function mapped(doc,id) {
  if (!doc.mappings || doc.mappings.imdb_id !== id) return [];
  return Object.entries(doc.episodes || {}).filter(([key,e])=>Number.isInteger(e.seasonNumber)&&Number.isInteger(e.episodeNumber)).map(([key,e])=>({
    skipMalId:doc.mappings.mal_id>0&&e.seasonNumber===1&&key===String(e.episodeNumber)?Number(doc.mappings.mal_id):0,
    season:e.seasonNumber,episode:e.episodeNumber,absolute:Number(e.absoluteEpisodeNumber)||0,
    title: typeof e.title==='object' ? e.title.en || e.title['x-jat'] || 'Episode '+e.episodeNumber : e.title || 'Episode '+e.episodeNumber,
    overview:e.overview || '',first_aired:Date.parse(e.airDate || '')/1000 || 0,tvdb_id:e.tvdbId || id+':'+e.seasonNumber+':'+e.episodeNumber
  }));
}
function merge(metadata, source, collect) {
  const byKey=new Map();
  for (const [season,rows] of Object.entries(source || {})) if (Array.isArray(rows)) for (const e of rows)
    byKey.set(Number(e.season || season)+':'+Number(e.episode), Object.assign({},e,{season:Number(e.season || season),episode:Number(e.episode)}));
  const out=[];
  for (const e of metadata) {
    const key=e.season+':'+e.episode, row=byKey.get(key) || {};
    byKey.delete(key); const sources=collect(row);
    out.push(Object.assign({},e,{streamnestSources:sources,torrents:qualities(sources)}));
  }
  for (const e of byKey.values()) {
    const sources=collect(e);
    out.push(Object.assign({},e,{overview:e.synopsis||e.overview||'',first_aired:Number(e.air_time)||0,
      tvdb_id:e.id || 'episode:'+e.season+':'+e.episode,streamnestSources:sources,torrents:qualities(sources)}));
  }
  out.sort((a,b)=>(a.season===0?9999:a.season)-(b.season===0?9999:b.season)||a.episode-b.episode);
  return out;
}
async function detail(id,old,source,collect) {
  let metadata=[];
  const mal = old && old.malId || (id==='tt0388629' ? 21 : 0);
  if (mal) try { metadata=mapped(await get('https://api.ani.zip/mappings?mal_id='+mal),id); } catch(e) {}
  if (!metadata.length) try {
    const meta=(await get('https://v3-cinemeta.strem.io/meta/series/'+id+'.json')).meta;
    if (meta && (meta.id===id || meta.imdb_id===id)) metadata=canonical(meta);
  } catch(e) {}
  if (!metadata.length) try {
    const show=await get('https://api.tvmaze.com/lookup/shows?imdb='+id);
    metadata=(await get('https://api.tvmaze.com/shows/'+show.id+'/episodes?specials=1')).map(e=>({
      season:e.season,episode:e.number,title:e.name,overview:(e.summary||'').replace(/<[^>]+>/g,' '),first_aired:Date.parse(e.airdate)/1000||0,tvdb_id:e.id
    }));
  } catch(e) {}
  const rows=merge(metadata,source,collect);
  if(old && (old.animeSeries || mal))try{await require('./streamnest-releases').attach(rows,old.title);}catch(e){}
  return rows;
}
function video(name) { return /\.(mp4|mkv|avi|webm|mov|m4v|ts)$/i.test(name) && !/(?:^|[\/\\._ -])sample(?:[\/\\._ -]|$)/i.test(name); }
function selectFile(files, model) {
  const index=model.fileIndex;
  if (Number.isInteger(index) && index>=0) {
    const file=files[index]; if (!file || !video(file.name)) throw Error('Invalid episode file index');
    if (model.file_name && path.basename(file.path) !== model.file_name) throw Error('Episode filename mismatch');
    return index;
  }
  const videos=files.map((file,index)=>({file,index})).filter(x=>video(x.file.name));
  const matched=videos.filter(({file})=>{
    let m=file.path.match(/(?:^|[^a-z0-9])s(\d{1,2})e(\d{1,4})(?!\d)/i) || file.path.match(/(?:^|[^a-z0-9])(\d{1,2})x(\d{1,4})(?!\d)/i);
    if (m) return Number(m[1])===Number(model.season) && Number(m[2])===Number(model.episode);
    m=file.path.match(/\s-\s(\d{1,4})(?:v\d+)?(?=\s|\[|\(|\.)/);
    return m && model.absolute>0 && Number(m[1])===Number(model.absolute);
  });
  if (matched.length===1) return matched[0].index;
  if (!matched.length && videos.length===1) return videos[0].index;
  throw Error('Cannot uniquely identify the requested episode in this pack');
}
function nextAttempt(attrs) {
  const next=(attrs.streamnestAttempt||0)+1, candidates=attrs.streamnestCandidates||[];
  if(next>=candidates.length)return null;
  const source=candidates[next];
  return Object.assign({},attrs,{torrent:source.url,file_name:source.file||'',fileIndex:source.fileIndex,
      streamnestAttempt:next,streamnestRetry:true});
}
function compareId(a,b){const x=String(a).split(':').map(Number),y=String(b).split(':').map(Number);return (x[0]===0?9999:x[0])-(y[0]===0?9999:y[0])||x[1]-y[1];}
function nextUnwatched(episodes,watched,mode){
  let rows=episodes.filter(e=>e.season>0&&e.episode>0);if(!rows.length)rows=episodes.filter(e=>e.episode>0);
  rows=rows.slice().sort((a,b)=>compareId(a.season+':'+a.episode,b.season+':'+b.episode));
  const seen=new Set(watched.map(e=>e.season+':'+e.episode));
  if(mode!=='firstUnwatched'){let last=-1;rows.forEach((e,n)=>{if(seen.has(e.season+':'+e.episode))last=n;});if(last>=0&&last+1<rows.length)return rows[last+1];}
  return rows.find(e=>!seen.has(e.season+':'+e.episode))||rows[0];
}
module.exports={quality,rank,qualities,parseStreams,alternatives,canonical,mapped,merge,detail,selectFile,nextAttempt,compareId,nextUnwatched};
