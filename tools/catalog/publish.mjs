import fs from 'node:fs';
import path from 'node:path';
import https from 'node:https';
import crypto from 'node:crypto';
import {fileURLToPath} from 'node:url';
import {parseStringPromise} from 'xml2js';
import {movie,mergeMovie,series,tvmaze,digest,verifyEnvelope,signEnvelope} from './schema.mjs';
const root=fileURLToPath(new URL('../../',import.meta.url));process.chdir(root);
const output='catalogs/v1',now=Date.now(),bases=['https://yts.gg/api/v2/','https://movies-api.accel.li/api/v2/','https://yts.bz/api/v2/'];let preferred=bases[0];const dead=new Set();
const metrics={moviePages:0,movieFailures:0,seriesPages:0,seriesFailures:0,tvmaze:0,rss:0};
function text(url){return new Promise((resolve,reject)=>{const req=https.get(url,{headers:{'User-Agent':url.includes('yts.gg')?'okhttp/3.14.7':'StreamNest-Catalog/1','Accept':'application/json,application/rss+xml'}},res=>{if(res.statusCode!==200){res.resume();reject(Error('HTTP '+res.statusCode));return;}const chunks=[];let size=0;res.on('data',c=>{size+=c.length;if(size>16000000)req.destroy(Error('Response too large'));else chunks.push(c);});res.on('end',()=>resolve(Buffer.concat(chunks).toString('utf8')));res.on('error',reject);});const timer=setTimeout(()=>req.destroy(Error('Catalog deadline')),12000);req.on('error',reject);req.on('close',()=>clearTimeout(timer));});}
const json=async url=>JSON.parse(await text(url));
async function movies(query){let last;for(const base of [preferred,...bases.filter(b=>b!==preferred)]){if(dead.has(base))continue;try{const d=await json(base+query);if(d.status!=='ok'||!d.data||d.data.movies!=null&&!Array.isArray(d.data.movies))throw Error('Invalid movie API');preferred=base;return d.data;}catch(e){last=e;dead.add(base);}}throw last||Error('Movie endpoints unavailable');}
async function parallel(items,count,run){let next=0;await Promise.all(Array.from({length:count},async()=>{while(next<items.length){const i=next++;await run(items[i],i);}}));}
let previous={},oldManifest;try{const manifest=verifyEnvelope(fs.readFileSync(output+'/manifest.json'),fs.readFileSync('catalogs/public-key.pem'));oldManifest=manifest;for(const [kind,file]of Object.entries(manifest.files)){const bytes=fs.readFileSync(file.path);if(digest(bytes)!==file.sha256)throw Error('Previous snapshot checksum');previous[kind]=JSON.parse(bytes);}}catch(e){if(fs.existsSync(output+'/manifest.json'))throw e;}
const seed=(kind,file)=>previous[kind]||JSON.parse(fs.readFileSync(file,'utf8'));
const oldMovies=seed('movies',fs.existsSync('app/src/main/assets/catalog-movies-v1.json')?'app/src/main/assets/catalog-movies-v1.json':'app/src/main/assets/movies-hindi-v1.json'),oldShows=seed('shows',fs.existsSync('app/src/main/assets/catalog-shows-v1.json')?'app/src/main/assets/catalog-shows-v1.json':'app/src/main/assets/series-index-v6.json'),oldAnime=seed('animes',fs.existsSync('app/src/main/assets/catalog-animes-v1.json')?'app/src/main/assets/catalog-animes-v1.json':'app/src/main/assets/anime-series-v6.json');
const movieMap=new Map();for(const raw of oldMovies.movies||[])mergeMovie(movieMap,raw,raw.catalogVerifiedAt||oldMovies.saved);
console.log('Refreshing source-confirmed movie catalog');
for(const [sort,pages]of [['date_added',20],['seeds',4],['download_count',4]]){
 for(let p=1;p<=pages;p++)try{const d=await movies('list_movies.json?limit=50&quality=1080p&sort_by='+sort+'&order_by=desc&page='+p);if(!Array.isArray(d.movies)||!d.movies.length){if(p===1&&sort==='date_added')throw Error('Empty root movie catalog');break;}for(let i=0;i<d.movies.length;i++){const raw=d.movies[i];if(sort==='download_count')raw.popularityRank=(p-1)*50+i+1;mergeMovie(movieMap,raw,now);}metrics.moviePages++;}catch(e){metrics.movieFailures++;break;}
}
const regional=[];for(let p=0;p<6;p++)try{const d=await json('https://indian-regional-catalog.vercel.app/catalog/movie/hi'+(p?'/skip='+p*20:'')+'.json');for(const m of d.metas||[])if(/^tt\d+$/.test(m.imdb_id||m.id||''))regional.push(m.imdb_id||m.id);}catch(e){metrics.movieFailures++;break;}
if(!dead.has(preferred))await parallel([...new Set(regional)],4,async id=>{try{const d=await movies('list_movies.json?query_term='+id);for(const m of d.movies||[])if(m.imdb_code===id&&['hi','hin'].includes(m.language))mergeMovie(movieMap,m,now);}catch(e){metrics.movieFailures++;}});
console.log('Movie snapshot records:',movieMap.size);
const shows=new Map((oldShows.series||[]).map(series).filter(Boolean).map(m=>[m.imdb,m]));const anime=new Map((oldAnime.series||[]).map(series).filter(Boolean).map(m=>[m.imdb,m]));
console.log('Refreshing primary series catalog');
for(const sort of ['seeds','dateadded']){const seen=new Set();for(let p=1;p<=300;p++)try{const d=await json('https://api.apiabcd.com/shows?app_id=T4P_AND&os=android14&ver=3.6.10&device_type=phone&short=1&page='+p+'&sort='+sort);if(!Array.isArray(d.MovieList))throw Error('Invalid series API');let added=0;for(let i=0;i<d.MovieList.length;i++){const m=series(d.MovieList[i]);if(!m||seen.has(m.imdb))continue;seen.add(m.imdb);added++;const old=shows.get(m.imdb);if(old?.language==='hi')m.language='hi';m[sort==='seeds'?'trendRank':'latestRank']=(p-1)*75+i+1;shows.set(m.imdb,{...old,...m});}metrics.seriesPages++;if(!added)break;}catch(e){metrics.seriesFailures++;break;}}
let tvNext=0;async function maze(url){const delay=Math.max(0,tvNext-Date.now());tvNext=Math.max(Date.now(),tvNext)+550;if(delay)await new Promise(r=>setTimeout(r,delay));return json(url);}
console.log('Adding independent TVmaze metadata through exact IMDb IDs');
try{const updates=await maze('https://api.tvmaze.com/updates/shows?since=day');const ids=Object.entries(updates).sort((a,b)=>b[1]-a[1]).slice(0,80).map(x=>x[0]);await parallel(ids,2,async id=>{try{const m=tvmaze(await maze('https://api.tvmaze.com/shows/'+id));if(!m)return;const map=m.animeSeries?anime:shows;if(!map.has(m.imdb))map.set(m.imdb,m);metrics.tvmaze++;}catch(e){}});}catch(e){console.log('Independent metadata retained from previous snapshot');}
console.log('Expanding anime coverage with paged independent metadata');
let animeCursor=Number(oldAnime.discoveryPage)||0;
for(let scanned=0;scanned<24;scanned++)try{
 const rows=await maze('https://api.tvmaze.com/shows?page='+animeCursor);
 if(!Array.isArray(rows)||!rows.length){animeCursor=0;break;}
 for(const raw of rows){const m=tvmaze(raw);if(!m||!m.animeSeries)continue;const old=anime.get(m.imdb);anime.set(m.imdb,old?{...m,...old}:m);metrics.tvmaze++;}
 animeCursor++;
}catch(error){console.log('Anime discovery retained at page',animeCursor);break;}
const oldLatest=previous.latest||(fs.existsSync('app/src/main/assets/catalog-latest-v1.json')?JSON.parse(fs.readFileSync('app/src/main/assets/catalog-latest-v1.json','utf8')):{});
const latestMap=new Map((oldLatest.releases||[]).map(m=>[m.imdb,m]));
try{const rss=await text('https://nyaa.si/?page=rss&c=1_2&f=2&q=1080p');if(/<!DOCTYPE|<!ENTITY/i.test(rss))throw Error('Unsafe RSS');const doc=await parseStringPromise(rss,{explicitArray:false});const items=doc.rss?.channel?.item||[];const R=await import('../../desktop/overlay/butter-provider/streamnest-releases.js');const rows=R.default.records(Array.isArray(items)?items:[items]);for(const m of rows){latestMap.set(m.imdb,m);metrics.rss++;}}catch(e){console.log('Latest anime feed retained from prior snapshot');}
const stamp=(count,old)=>count?now:old?.saved||now;
const documents={movies:{saved:stamp(metrics.moviePages,oldMovies),movies:[...movieMap.values()]},shows:{saved:stamp(metrics.seriesPages+metrics.tvmaze,oldShows),series:[...shows.values()]},animes:{saved:stamp(metrics.tvmaze,oldAnime),discoveryPage:animeCursor,series:[...anime.values()]},latest:{saved:stamp(metrics.rss,oldLatest),releases:[...latestMap.values()].sort((a,b)=>(b.uploaded||0)-(a.uploaded||0)).slice(0,300)}};
if(!documents.movies.movies.length||!documents.shows.series.length||!documents.animes.series.length)throw Error('Refusing to publish an empty core catalog');
const keyText=process.env.CATALOG_SIGNING_KEY;if(!keyText)throw Error('CATALOG_SIGNING_KEY is required');const key=crypto.createPrivateKey(keyText);const pub=crypto.createPublicKey(key).export({type:'spki',format:'pem'});if(pub!==fs.readFileSync('catalogs/public-key.pem','utf8'))throw Error('Signing key does not match the pinned catalog key');
fs.mkdirSync(output+'/data',{recursive:true});const files={};for(const [kind,doc]of Object.entries(documents)){const bytes=Buffer.from(JSON.stringify(doc));const hash=digest(bytes),name=output+'/data/'+kind+'-'+hash.slice(0,16)+'.json';fs.writeFileSync(name,bytes);files[kind]={path:name,sha256:hash,bytes:bytes.length,count:(doc.movies||doc.series||doc.releases).length,saved:doc.saved};}
const history=[...(oldManifest?.history||[]),...(oldManifest?[{version:oldManifest.version,paths:Object.values(oldManifest.files).map(f=>f.path)}]:[])].slice(-7);
const manifest={schema:1,version:new Date(now).toISOString(),generatedAt:now,providers:{movies:bases},files,history};
fs.writeFileSync(output+'/manifest.json',signEnvelope(manifest,key));const retained=new Set([...Object.values(files).map(f=>f.path),...history.flatMap(h=>h.paths)]);for(const name of fs.readdirSync(output+'/data')){const file=output+'/data/'+name;if(/^(movies|shows|animes|latest)-[0-9a-f]{16}\.json$/.test(name)&&!retained.has(file))fs.unlinkSync(file);}
fs.writeFileSync(output+'/status.json',JSON.stringify({generatedAt:now,metrics,counts:Object.fromEntries(Object.entries(files).map(([k,v])=>[k,v.count]))},null,2)+'\n');
if(process.argv.includes('--bundle')){
 for(const [kind,doc]of Object.entries(documents))fs.writeFileSync('app/src/main/assets/catalog-'+kind+'-v1.json',JSON.stringify(doc));
 fs.copyFileSync('catalogs/public-key.pem','app/src/main/assets/catalog-public-key.pem');
 fs.copyFileSync(output+'/manifest.json','catalogs/bundled-manifest.json');
 fs.copyFileSync('catalogs/public-key.pem','desktop/overlay/butter-provider/catalog-public-key.pem');
 for(const [kind,doc]of Object.entries(documents))fs.writeFileSync('desktop/overlay/butter-provider/catalog-'+kind+'-v1.json',JSON.stringify(doc));
}
console.log(JSON.stringify({version:manifest.version,metrics,counts:Object.fromEntries(Object.entries(files).map(([k,v])=>[k,v.count]))}));
