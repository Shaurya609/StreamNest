"use strict";
const E=require('./streamnest-episodes');
function ordered(rows,season){const seen=new Set();return (rows||[]).filter(e=>Number(e.episode)>0&&(Number(season)===0?Number(e.season)===0:Number(e.season)>0)).slice().sort((a,b)=>E.compareId(a.season+':'+a.episode,b.season+':'+b.episode)).filter(e=>{const k=e.season+':'+e.episode;if(seen.has(k))return false;seen.add(k);return true;});}
function neighbour(attrs,direction){if(![1,-1].includes(direction)||!(Number(attrs.episode)>0))return null;const rows=ordered(attrs.auto_play_data,attrs.season),index=rows.findIndex(e=>Number(e.season)===Number(attrs.season)&&Number(e.episode)===Number(attrs.episode));return index<0?null:rows[index+direction]||null;}
function future(row){let date=String(row.airdate||row.released||'').slice(0,10);if(!date&&Number(row.first_aired)>0)date=new Date(Number(row.first_aired)*1000).toISOString().slice(0,10);return /^\d{4}-\d{2}-\d{2}$/.test(date)&&date>new Date().toISOString().slice(0,10);}
async function resolve(attrs,direction,lookup=E.alternatives){
 const row=neighbour(attrs,direction);if(!row)throw Error('No adjacent episode');if(future(row))throw Error('This episode has not aired yet');
 let lookupFailure;
 const sources=(row.streamnestSources||Object.values(row.torrents||{})).slice();try{sources.push(...await lookup(attrs.imdb_id,Number(row.season),Number(row.episode),Object.assign({},row,{streamnestSeriesTitle:attrs.streamnestSeriesTitle})));}catch(e){lookupFailure=e;}
 const quality=attrs.streamnestQuality||'Auto',ranked=E.rank(sources,quality),seen=new Set(),candidates=E.attempts(ranked,quality);
 if(!candidates.length)throw lookupFailure||Error('No matching source found. Try another quality or retry source lookup.');const source=candidates[0];
 return Object.assign({},row,{type:'episode',torrent:source.url,file_name:source.file||'',fileIndex:source.fileIndex,streamnestSources:candidates,streamnestCandidates:candidates,streamnestAttempt:0,streamnestRetry:false,streamnestQuality:quality,quality:source.quality,imdb_id:attrs.imdb_id,tvdb_id:attrs.tvdb_id,auto_id:row.season+':'+row.episode,auto_play_data:attrs.auto_play_data,episodes:attrs.episodes,auto_play:attrs.auto_play,streamnestFromBeginning:true,streamnestSeriesTitle:attrs.streamnestSeriesTitle,malId:row.malId||0});
}
function completed(position,length,played,paused){return played&&!paused&&Number.isFinite(length)&&length>10&&position>=length-5;}
function range(type,start,end,length){const max=type==='intro'?300:900;if(!Number.isFinite(start)||!Number.isFinite(end)||!Number.isFinite(length)||length<=0||start<0||end-start<5||end-start>max||end>length+1||(type==='intro'?start>=length/2:start<=length/2))return null;return {type,start,end:Math.min(end,length)};}
function introdb(doc,attrs,length){if(doc.imdb_id!==attrs.imdb_id||Number(doc.season)!==Number(attrs.season)||Number(doc.episode)!==Number(attrs.episode)||doc.is_movie)return [];return ['intro','outro'].map(type=>{const row=doc[type];return !doc['no_'+type]&&row&&row.confidence>=0.75&&row.submission_count>=1?range(type,row.start_ms/1000,row.end_ms/1000,length):null;}).filter(Boolean);}
function aniskip(doc,length){if(!doc.found||!Array.isArray(doc.results))return [];return doc.results.map(row=>{if(!['op','ed'].includes(row.skipType)||Math.abs(row.episodeLength-length)>5||!row.interval)return null;return range(row.skipType==='op'?'intro':'outro',row.interval.startTime,row.interval.endTime,length);}).filter(Boolean);}
function skipdb(doc,attrs,length){
 if(doc.imdb_id!==attrs.imdb_id||Number(doc.season)!==Number(attrs.season)||Number(doc.episode)!==Number(attrs.episode)||!doc.segments)return [];
 return ['intro','outro'].map(type=>{
  const row=doc.segments[type];
  return row&&row.match==='exact'&&row.confidence>=0.75?range(type,row.start_ms/1000,row.end_ms/1000,length):null;
 }).filter(Boolean);
}
async function segments(attrs,length,fetch){const get=fetch||(url=>require('./streamnest-resilience').metadata(url,v=>v&&typeof v==='object'&&!v.error));let rows=[],absent=new Set();if(!/^tt\d+$/.test(attrs.imdb_id||'')||!(Number(attrs.episode)>0)||!(length>0))return rows;
 try{const doc=await get('https://api.introdb.app/segments?imdb_id='+attrs.imdb_id+'&season='+Number(attrs.season)+'&episode='+Number(attrs.episode));rows=introdb(doc,attrs,length);if(doc.imdb_id===attrs.imdb_id&&Number(doc.season)===Number(attrs.season)&&Number(doc.episode)===Number(attrs.episode)&&!doc.is_movie)for(const type of ['intro','outro'])if(doc['no_'+type])absent.add(type);}catch(e){}
 if(rows.length+absent.size<2)try{
  const doc=await get('https://api.skipdb.tv/api/segments?imdb_id='+attrs.imdb_id+'&season='+Number(attrs.season)+'&episode='+Number(attrs.episode)+'&duration='+length.toFixed(3)+'&adjust=none');
  for(const row of skipdb(doc,attrs,length))if(!absent.has(row.type)&&!rows.some(s=>s.type===row.type))rows.push(row);
 }catch(error){}
 if(attrs.malId>0&&Number(attrs.season)===1&&rows.length+absent.size<2)try{for(const row of aniskip(await get('https://api.aniskip.com/v2/skip-times/'+attrs.malId+'/'+Number(attrs.episode)+'?types[]=op&types[]=ed&episodeLength='+length.toFixed(3)),length))if(!absent.has(row.type)&&!rows.some(s=>s.type===row.type))rows.push(row);}catch(e){}
 return rows;
}
function active(rows,position){return rows.find(s=>position>=s.start&&position<s.end-1)||null;}
module.exports={ordered,neighbour,resolve,future,completed,range,introdb,aniskip,skipdb,segments,active};
