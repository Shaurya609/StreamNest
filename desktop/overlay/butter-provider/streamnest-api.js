// StreamNest compatibility repair. Original files are backed up separately.
'use strict';
const https = require('https');
const seeds = require('./hindi-seeds.json');
const Hindi=require('./streamnest-hindi');
const Fresh=require('./streamnest-fresh');
const Browse=require('./streamnest-browse');
const Series=require('./streamnest-series');
const Episodes=require('./streamnest-episodes');
const base = 'https://api.apiabcd.com/';
const common = 'app_id=T4P_AND&os=android14&ver=3.6.10&device_type=phone';
const cache = new Map();
function get(path){return require('./streamnest-resilience').metadata(base+path,value=>value&&typeof value==='object'&&!value.error);}
function active(provider){return (provider.apiURL||[]).some(url=>url.includes('apiabcd.com'));}
function items(movie) {
 let list=[...(movie.items||[])];for(const group of Object.values(movie.items_lang||{})){if(Array.isArray(group))list=list.concat(group);}
 return list.map(item=>({url:item.torrent_magnet||item.torrent_url,magnet:item.torrent_magnet,quality:item.quality||'unknown',title:item.file||movie.title,seed:Number(item.torrent_seeds)||0,peer:Number(item.torrent_peers)||0,size:Number(item.size_bytes)||0,filesize:((Number(item.size_bytes)||0)/1073741824).toFixed(2)+' GB',provider:'Catalog',source:''})).filter(item=>item.url);
}
function qualities(movie,episode) {if(episode)return Episodes.qualities(items(movie));const result={};items(movie).filter(item=>["1080p","2160p"].includes(item.quality)&&!/(?:\bhdcam\b|\bcamrip\b|\btelesync\b|\bhdts\b|\btsrip\b)/i.test(item.title)).forEach(item=>{if(!result[item.quality]||item.seed>result[item.quality].seed)result[item.quality]=item;});return result;}
function isHindi(id){return seeds.some(s=>s.imdb===id);}
function movie(o,show,hindi) {
 const id=o.imdb||o.imdb_id, audio=o.language==='ja'?'ja':(hindi||isHindi(id))?'hi':'en',torrents=qualities(o,!!o.animeRelease),poster=o.poster_big||o.poster_med||'';
 return {animeRelease:!!o.animeRelease,type:show?'show':'movie',imdb_id:id,tvdb_id:show?id:undefined,title:o.title,title1:o.title,year:Number(o.year)>1800?o.year:'',rating:show?{percentage:(Number(o.rating)||0)*10}:Number(o.rating)||0,runtime:Number(o.runtime)||0,genre:o.genres||[],genres:o.genres||[],images:{poster:poster,fanart:poster,banner:poster},poster:poster,poster_medium:poster,image:poster,cover:poster,backdrop:poster,synopsis:o.description||'',trailer:o.trailer?'https://www.youtube.com/watch?v='+o.trailer:false,torrents:torrents,langs:{[audio]:torrents},defaultAudio:audio,contextLocale:audio,exist_translations:[audio],...(show?{num_seasons:0,seasons:0}:{}),status:'Unknown',locale:null};
}
async function detail(id,old,show) {
 if(!show){const fresh=await Fresh.find(id);if(fresh)return Object.assign(movie(fresh,false,fresh.language==='hi'),{freshMovie:true});}
 if(old&&old.regionalCatalog&&!/^tt[0-9]+$/.test(id))throw new Error('No matched playback source is available for this Hindi title yet.');
 if(!show){const result=movie(await get('movie?'+common+'&imdb='+encodeURIComponent(id)),false);if(old&&old.regionalCatalog&&Object.keys(result.torrents).length===0)throw new Error('This Hindi title is listed, but the torrent provider has no playback sources yet.');return result;}
 let response={};try{response=await get('show?'+common+'&imdb='+encodeURIComponent(id));}catch(e){}
 const episodes=await Episodes.detail(id,old,response,items);
 return Object.assign({},old||{},{imdb_id:id,tvdb_id:id,episodes,num_seasons:new Set(episodes.map(e=>e.season)).size,seasons:new Set(episodes.map(e=>e.season)).size,contextLocale:old&&old.contextLocale|| (isHindi(id)?'hi':'en'),exist_translations:[isHindi(id)?'hi':'en'],status:'Unknown',rating:old&&typeof old.rating==='object'?old.rating:{percentage:0}});
}
async function list(provider,filters,show) {
 require('./streamnest-resilience').begin();
 const f=Object.assign({},filters),hi=f.genre==='Hindi'||f.language==='hi'||(!f.language&&provider.contentLanguage==='hi'&&provider.contentLangOnly);if(f.genre==='Hindi')f.genre='All';const language=hi?'hi':f.language==='en'?'en':'all';const result=await (show?Series:Browse).list(f,language);return {results:result.rows.map(o=>Object.assign(movie(o,show,o.language==='hi'),{freshMovie:!show,regionalCatalog:!!o.regionalCatalog,selectedQuality:f.type||'All'})),hasMore:result.hasMore};
}

function filters(episodes){const names=['All','Action','Adventure','Animation','Comedy','Crime','Documentary','Drama','Fantasy','Horror','Mystery','Romance','Sci-Fi','Thriller'];const genres={},sorters={};names.forEach(k=>genres[k]=k==='Hindi'?'Hindi cinema & series':k);['trending','popularity','last added','year','title','rating'].forEach(k=>sorters[k]=k);return Promise.resolve({genres,sorters,languages:{all:'All',en:'English',hi:'Hindi'},types:episodes?{All:"Auto / all available","1080p":"1080p","2160p":"4K","720p":"720p","480p":"480p"}:{All:"1080p & 4K","1080p":"1080p","2160p":"4K"}});}
async function torrents(id,show,season,episode){
 if(!show){const fresh=await Fresh.find(id);return fresh?items(fresh):items(await get('movie?'+common+'&imdb='+encodeURIComponent(id)));}
 let result=[];try{const data=await get('show?'+common+'&imdb='+encodeURIComponent(id));for(const [s,rows]of Object.entries(data)){if(!Array.isArray(rows))continue;for(const e of rows){if(season!=null&&(Number(s)!==Number(season)||Number(e.episode)!==Number(episode)))continue;result.push(...items(e));}}}catch(e){}
 if(season!=null)try{result.push(...await Episodes.alternatives(id,Number(season),Number(episode)));}catch(e){}
 return result;
}
module.exports={active,list,detail,filters,torrents,movie,qualities,items};
