'use strict';
const fs=require('fs'),path=require('path');
function identity(a){const id=a.imdb_id||a.imdb||'';return /^tt\d+$/.test(id)?Number(a.episode)>0?'episode:'+id+':'+Number(a.season||0)+':'+Number(a.episode):'movie:'+id:'source:'+String(a.torrent||a.src||a.title||'');}
function resumable(position,duration){return Number.isFinite(position)&&position>=10&&(!(duration>0)||(position<duration-10&&position<duration*0.98))?position:0;}
function file(){return path.join(typeof nw!=='undefined'?nw.App.dataPath:require('os').tmpdir(),'streamnest-watch-progress-v1.json');}
let cached,lastUpdatedAt=0;function load(){if(!cached){try{cached=JSON.parse(fs.readFileSync(file(),'utf8'));if(!cached||typeof cached!=='object'||Array.isArray(cached))cached={};}catch(e){cached={};}for(const row of Object.values(cached)){if(row&&Number.isSafeInteger(row.updatedAt)&&row.updatedAt<=Date.now()+86400000)lastUpdatedAt=Math.max(lastUpdatedAt,row.updatedAt);}}return cached;}
function read(a,legacy=0){const key=identity(a),rows=load();const row=rows[key];return row&&typeof row==='object'?resumable(Number(row.position),Number(row.duration)):Math.max(0,Number(legacy)||0);}
function save(a,position,duration){if(!Number.isFinite(position)||!(duration>0))return;const rows=load();rows[identity(a)]={position:resumable(position,duration),duration,completed:position>=duration-10||position>=duration*0.98,updatedAt:(lastUpdatedAt=Math.max(Date.now(),lastUpdatedAt+1))};const keys=Object.keys(rows).sort((a,b)=>rows[b].updatedAt-rows[a].updatedAt);for(const key of keys.slice(2000))delete rows[key];const target=file();try{fs.writeFileSync(target+'.tmp',JSON.stringify(rows));fs.renameSync(target+'.tmp',target);}catch(e){}try{require('./streamnest-sync').request();}catch(e){}}
function records(){const out={};for(const [key,row] of Object.entries(load()))out[key]={positionMs:Math.round(row.position*1000),durationMs:Math.round(row.duration*1000),updatedAt:row.updatedAt,...(row.completed?{completed:true}:{})};return require('./streamnest-sync-crypto').clean(out);}
function merge(incoming){const merged=require('./streamnest-sync-crypto').merge(records(),incoming),rows=load();for(const [key,row]of Object.entries(merged))rows[key]={position:row.positionMs/1000,duration:row.durationMs/1000,updatedAt:row.updatedAt,completed:row.completed===true};for(const row of Object.values(rows))if(row&&Number.isSafeInteger(row.updatedAt))lastUpdatedAt=Math.max(lastUpdatedAt,row.updatedAt);const target=file();try{fs.writeFileSync(target+'.tmp',JSON.stringify(rows));fs.renameSync(target+'.tmp',target);}catch(e){}}
function chooseEpisode(imdb,episodes,history=records(),now=Date.now()){
 if(!/^tt\d+$/.test(imdb))return null;
 const seen=new Set(),ordered=(episodes||[]).filter(e=>Number.isInteger(e.season)&&e.season>=0&&Number.isInteger(e.episode)&&e.episode>0&&!seen.has(e.season+':'+e.episode)&&seen.add(e.season+':'+e.episode)).slice().sort((a,b)=>a.season-b.season||a.episode-b.episode);
 const aired=e=>{const date=String(e.airdate||e.released||'').slice(0,10);return !(Number(e.first_aired)>now/1000)&&(!/^\d{4}-\d{2}-\d{2}$/.test(date)||date<=new Date(now).toISOString().slice(0,10));};
 let latest=null,record=null;
 for(const e of ordered){const row=history['episode:'+imdb+':'+e.season+':'+e.episode];if(row&&aired(e)&&row.updatedAt>0&&(!record||row.updatedAt>record.updatedAt)){latest=e;record=row;}}
 if(!latest||!record.completed)return latest;
 const next=ordered.slice(ordered.indexOf(latest)+1).find(e=>(e.season===0)===(latest.season===0));return next&&aired(next)?next:latest;
}
module.exports={identity,resumable,read,save,records,merge,chooseEpisode};
