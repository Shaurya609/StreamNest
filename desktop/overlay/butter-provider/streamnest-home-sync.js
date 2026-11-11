'use strict';
// A separate, domain-derived encrypted room keeps older clients from erasing Home.
const fs=require('fs'),path=require('path'),crypto=require('crypto'),C=require('./streamnest-sync-crypto');
const NAMES=['Picked for you','Discover something new','Recently added','Movies','Series','Anime','Hindi picks','Korean picks'];
let revision=0;
function createGate(){let lastScope='',lastSignature='',lastAt=0;return(scope,signature,now)=>{if(scope===lastScope&&signature===lastSignature&&now-lastAt<60000)return false;lastScope=scope;lastSignature=signature;lastAt=now;return true;};}
const shouldPoll=createGate();
function watched(history,previous={}){const result={...previous};for(const [key,p]of Object.entries(C.clean(history))){const id=key.split(':')[1];result[id]=Math.max(result[id]||0,p.updatedAt);}return Object.fromEntries(Object.entries(result).sort((a,b)=>b[1]-a[1]||(a[0]<b[0]?-1:1)).slice(0,500));}
function asHistory(watched){return Object.fromEntries(Object.entries(watched).map(([id,time])=>['movie:'+id,{positionMs:10000,durationMs:120000,updatedAt:time}]));}
function signature(history){
 const latest=new Map();for(const [key,p] of Object.entries(C.clean(history))){const id=key.split(':')[1];latest.set(id,Math.max(latest.get(id)||0,p.updatedAt));}
 return crypto.createHash('sha256').update(JSON.stringify([...latest.keys()].sort((a,b)=>latest.get(b)-latest.get(a)||(a<b?-1:a>b?1:0)))).digest('hex');
}
function metadata(t){
 const H=require('./streamnest-home'),id=H.id(t);if(!/^tt\d+$/.test(id)||!['movies','shows','animes'].includes(t.kind)||typeof t.title!=='string'||!t.title.trim())return null;
 const out={imdb:id,kind:t.kind,title:t.title.slice(0,150),genres:H.genres(t).filter(g=>g.length<=40).sort().slice(0,8)};
 for(const key of ['year','language'])if(t[key]!=null)out[key]=String(t[key]).slice(0,12);
 const poster=require('./streamnest-artwork').candidates(t,false)[0];if(poster&&poster.length<=600)out.poster_big=poster;
 if(Number.isSafeInteger(t.malId)&&t.malId>0)out.malId=t.malId;
 return out;
}
function clean(value){
 if(!value||value.schema!==1||!/^[a-f0-9]{64}$/.test(value.history||'')||!Number.isSafeInteger(value.createdAt)||value.createdAt<1||value.createdAt>Date.now()+86400000||!Array.isArray(value.rows)||!value.titles||Buffer.byteLength(JSON.stringify(value))>90000)return null;
 const titles={};for(const [id,t] of Object.entries(value.titles).slice(0,150)){const m=metadata(t||{});if(m&&m.imdb===id)titles[id]=m;}
 const times={};for(const [id,time]of Object.entries(value.watched||{}).slice(0,500))if(/^tt\d+$/.test(id)&&Number.isSafeInteger(time)&&time>0&&time<=Date.now()+86400000)times[id]=time;
 const rows=[],seen=new Set();for(const row of value.rows.slice(0,8)){if(!row||!NAMES.includes(row.name)||seen.has(row.name)||!Array.isArray(row.ids))continue;seen.add(row.name);rows.push({name:row.name,ids:[...new Set(row.ids.filter(id=>typeof id==='string'&&titles[id]))].slice(0,12)});}
 return rows.length?{schema:1,history:value.history,createdAt:value.createdAt,rows,titles,watched:times}:null;
}
function create(pool,history,previous,now=Date.now()){
 history=asHistory(watched(history,previous&&previous.watched));
 const H=require('./streamnest-home'),merged=new Map();for(const t of pool){const old=merged.get(H.id(t));if(!old||old.kind!=='animes'||t.kind==='animes')merged.set(H.id(t),t);}for(const t of Object.values(previous&&previous.titles||{}))merged.set(t.imdb,{...merged.get(t.imdb),...t});
 const titles={},rows=H.build([...merged.values()],history,12).slice(1).map(row=>({name:row.name,ids:row.cards.map(c=>{const m=metadata(c.title);if(m)titles[m.imdb]=m;return m&&m.imdb;}).filter(Boolean)}));
 const latest=new Map();for(const [key,p]of Object.entries(C.clean(history))){const id=key.split(':')[1];latest.set(id,Math.max(latest.get(id)||0,p.updatedAt));}
 for(const id of [...latest.keys()].sort((a,b)=>latest.get(b)-latest.get(a)||(a<b?-1:1)).slice(0,50)){const m=metadata(merged.get(id)||{});if(m)titles[id]=m;}
 return clean({schema:1,history:signature(history),createdAt:now,rows,titles,watched:watched(history)});
}
function file(){return path.join(nw.App.dataPath,'streamnest-group-home-v1.json');}
function cached(code){try{const d=JSON.parse(fs.readFileSync(file(),'utf8'));return d.scope===C.auth(C.homeCode(code))?clean(d.home):null;}catch(e){return null;}}
function save(code,home){const data=JSON.stringify({scope:C.auth(C.homeCode(code)),home});try{if(fs.readFileSync(file(),'utf8')===data)return;}catch(e){}fs.writeFileSync(file()+'.tmp',data,{mode:0o600});fs.renameSync(file()+'.tmp',file());revision++;}
function apply(local,pool,history,home,limit=12){
 home=clean(home);if(!home)return local;const H=require('./streamnest-home'),known=new Map(pool.map(t=>[H.id(t),t])),watched=new Set(Object.keys(history).map(k=>k.split(':')[1]));
 return [local[0],...home.rows.map(row=>({name:row.name,cards:row.ids.filter(id=>!watched.has(id)).slice(0,limit).map(id=>({title:{...known.get(id),...home.titles[id]}}))}))];
}
async function sync(code,call,current,history,pool){
 const derived=C.homeCode(code),existing=cached(code),wanted=signature(asHistory(watched(history,existing&&existing.watched)));if(!shouldPoll(C.auth(derived),wanted,Date.now()))return existing;for(let n=0;n<3;n++){
  if(current()!==code)return;const remote=await call(derived),home=remote.payload?clean(C.openDocument(derived,remote.payload)):null;if(current()!==code)return;
  if(home&&home.history===signature(asHistory(watched(history,home.watched)))&&Date.now()-home.createdAt<86400000){save(code,home);return home;}
  const next=create(await pool(),history,home);if(!next)return;const response=await call(derived,C.seal(derived,next),remote.revision);if(current()!==code)return;if(!response.conflict){save(code,next);return next;}
 }throw Error('Home changed; retry shortly');
}
module.exports={createGate,watched,asHistory,signature,metadata,clean,create,apply,cached,sync,changed(){revision++;},get revision(){return revision;}};
