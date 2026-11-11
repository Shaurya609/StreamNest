 'use strict';
const fs=require('fs'),path=require('path');let revision=0;
function id(o){return o.imdb||o.imdb_id||o.imdb_code||o.id||'';}
function genres(t){return [...new Set((Array.isArray(t.genres)?t.genres:Array.isArray(t.genre)?t.genre:[]).map(g=>String(g).toLowerCase()))];}
function build(pool,history={},limit=12){
 const titles=new Map();for(const title of pool)if(/^tt\d+$/.test(id(title))&&title.title){const old=titles.get(id(title));if(!old||old.kind!=='animes'||title.kind==='animes')titles.set(id(title),title);}
 const latest=new Map();for(const [key,row]of Object.entries(history)){const parts=key.split(':');if(!/^(movie:tt\d+|episode:tt\d+:\d+:\d+)$/.test(key)||!row)continue;const old=latest.get(parts[1]);if(!old||row.updatedAt>old.progress.updatedAt)latest.set(parts[1],{historyKey:key,progress:row});}
 const recent=[...latest.keys()].sort((a,b)=>latest.get(b).progress.updatedAt-latest.get(a).progress.updatedAt),affinity=new Map(),resume=[];
 for(const [n,key]of recent.entries()){const title=titles.get(key);if(!title)continue;const item=latest.get(key),p=item.progress;
  if(!p.completed&&p.positionMs>=10000&&(!(p.durationMs>0)||(p.positionMs<p.durationMs-10000&&p.positionMs<p.durationMs*.98))&&resume.length<limit)resume.push(Object.assign({title},item));
  if(p.completed&&title.kind!=='movies'&&resume.length<limit&&Array.isArray(title.homeEpisodeOrder)){const parts=item.historyKey.split(':'),at=title.homeEpisodeOrder.indexOf(parts[2]+':'+parts[3]),next=title.homeEpisodeOrder[at+1];if(at>=0&&next&&next.startsWith('0:')===(parts[2]==='0'))resume.push({title,progress:{nextEpisode:true},historyKey:'episode:'+key+':'+next});}
  if(n<50)for(const genre of genres(title))affinity.set(genre,(affinity.get(genre)||0)+1);
 }
 const scores=new Map([...titles.values()].map(t=>[id(t),genres(t).reduce((s,g)=>s+(affinity.get(g)||0),0)])),score=t=>scores.get(id(t)),candidates=[...titles.values()].filter(t=>!latest.has(id(t))&&!(Number(t.year)>new Date().getFullYear())).sort((a,b)=>score(b)-score(a)||(a.trendRank||100000)-(b.trendRank||100000)||(Number(b.rating)||0)-(Number(a.rating)||0)||id(a).localeCompare(id(b)));
 const kinds={movies:[],shows:[],animes:[]};for(const title of candidates)if(kinds[title.kind]&&kinds[title.kind].length<limit)kinds[title.kind].push({title});
 const mixed=[];for(let n=0;n<limit&&mixed.length<limit;n++)for(const cards of Object.values(kinds))if(cards[n]&&mixed.length<limit)mixed.push(cards[n]);
 const rows=[{name:'Continue Watching',cards:resume},{name:affinity.size?'Picked for you':'Discover something new',cards:mixed},...Object.entries(kinds).map(([kind,cards])=>({name:{movies:'Movies',shows:'Series',animes:'Anime'}[kind],cards}))];
 const recentKinds=Object.keys(kinds).map(kind=>candidates.filter(t=>t.kind===kind&&(t.latestRank>0&&t.latestRank<2147483647||t.date_uploaded_unix>0)).sort((a,b)=>(a.date_uploaded_unix?-a.date_uploaded_unix:a.latestRank)-(b.date_uploaded_unix?-b.date_uploaded_unix:b.latestRank))),added=[];for(let n=0;n<limit&&added.length<limit;n++)for(const list of recentKinds)if(list[n]&&added.length<limit)added.push({title:list[n]});if(added.length)rows.splice(2,0,{name:'Recently added',cards:added});
 for(const language of ['hi','ko']){const cards=candidates.filter(t=>t.language===language).slice(0,limit).map(title=>({title}));if(cards.length)rows.push({name:language==='hi'?'Hindi picks':'Korean picks',cards});}return rows;
}
function libraryFile(){return path.join(nw.App.dataPath,'streamnest-home-library-v1.json');}
function remembered(){try{return JSON.parse(fs.readFileSync(libraryFile(),'utf8'));}catch(e){return {};}}
function remember(raw,kind){try{const rows=remembered();const old=rows[id(raw)];rows[id(raw)]={...(old&&old.homeEpisodeOrder?{homeEpisodeOrder:old.homeEpisodeOrder,homeEpisodeOrderAt:old.homeEpisodeOrderAt}:{}),...raw,kind,seen:Date.now()};revision++;const kept=Object.values(rows).sort((a,b)=>b.seen-a.seen).slice(0,200);fs.writeFileSync(libraryFile()+'.tmp',JSON.stringify(Object.fromEntries(kept.map(t=>[id(t),t]))));fs.renameSync(libraryFile()+'.tmp',libraryFile());}catch(e){}}
function load(){const R=require('./streamnest-resilience'),pool=[];for(const kind of ['movies','shows','animes']){const doc=R.catalog(kind);for(const t of doc&&(doc.movies||doc.series)||[])pool.push({...t,kind});}for(const t of R.known.values())pool.push({...t,kind:'movies'});for(const t of Object.values(remembered()))pool.push(t);return pool;}
async function enrich(pool,history){
 const known=new Map(),seen=new Set();for(const t of pool){const old=known.get(id(t));if(!old||old.kind!=='animes'||t.kind==='animes')known.set(id(t),t);}let attempts=0;
 for(const [key,progress] of Object.entries(history).sort((a,b)=>b[1].updatedAt-a[1].updatedAt)){const parts=key.split(':');if(!/^tt\d+$/.test(parts[1]||'')||seen.has(parts[1]))continue;seen.add(parts[1]);const existing=known.get(parts[1]),next=parts[0]==='episode'&&progress.completed;if(existing&&(!next||Date.now()-existing.homeEpisodeOrderAt<7200000))continue;if(attempts++>=4)break;
  try{const type=parts[0]==='movie'?'movie':'series',meta=(await require('./streamnest-net')('https://v3-cinemeta.strem.io/meta/'+type+'/'+parts[1]+'.json',Date.now()+4000)).meta;if(!meta||(meta.imdb_id||meta.id)!==parts[1])continue;
   const raw=existing?{...existing}:{imdb:parts[1],title:meta.name,poster_big:meta.poster,description:String(meta.description||'').slice(0,1200),genres:meta.genres||[],year:String(meta.releaseInfo||'').slice(0,4),kind:type==='movie'?'movies':'shows'};
   if(type==='series'){const rows=await require('./streamnest-episodes').detail(parts[1],{...raw,animeSeries:raw.kind==='animes',homeMetadataOnly:true},{},()=>[]);raw.homeEpisodeOrder=episodeOrder(rows);raw.homeEpisodeOrderAt=Date.now();}
   if(existing)pool.splice(pool.indexOf(existing),1);pool.push(raw);known.set(parts[1],raw);remember(raw,raw.kind);
  }catch(e){}
 }return pool;
}
function episodeOrder(rows){return rows.filter(r=>!require('./streamnest-playback').future(r)).slice().sort((a,b)=>a.season-b.season||a.episode-b.episode).map(r=>r.season+':'+r.episode);}
module.exports={get revision(){return revision+require('./streamnest-home-sync').revision;},episodeOrder,id,genres,build,load,remember,enrich};
