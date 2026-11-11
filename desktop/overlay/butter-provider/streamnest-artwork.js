'use strict';
const preferred=new Map(),alternate=new Map(),pending=new Map();
function id(t){return t.imdb||t.imdb_id||t.imdb_code||t.id||'';}
function url(value){try{const u=new URL(value);if(u.protocol==='http:'&&/^(images\.metahub\.space|image\.tmdb\.org|static\.tvmaze\.com)$/.test(u.hostname))u.protocol='https:';return u.protocol==='https:'&&!u.username&&!u.password&&u.href.length<=1000?u.href:'';}catch(e){return '';}}
function candidates(t,usePreferred=true){const fallback=/^tt\d+$/.test(id(t))?'https://images.metahub.space/poster/medium/'+id(t)+'/img':'',urls=[...new Set([usePreferred?preferred.get(id(t)):null,t.poster_big,t.large_cover_image,t.poster,t.images&&t.images.poster,t.images&&t.images.medium].map(url).filter(Boolean))].slice(0,2);if(fallback&&!urls.includes(fallback))urls.push(fallback);return urls;}

function bounded(map,key,value){map.delete(key);map.set(key,value);while(map.size>256)map.delete(map.keys().next().value);}
function tvmaze(doc,imdb){return doc&&doc.externals&&doc.externals.imdb===imdb&&doc.image?url(doc.image.medium||doc.image.original):'';}
async function lookup(t,get=require('./streamnest-net')){
 const imdb=id(t);if(!/^tt\d+$/.test(imdb)||t.kind==='movies')return '';const old=alternate.get(imdb);if(old&&old.until>Date.now())return old.url;if(pending.has(imdb))return pending.get(imdb);
 const task=(async()=>{let image='';try{image=tvmaze(await get('https://api.tvmaze.com/lookup/shows?imdb='+imdb,Date.now()+4000),imdb);}catch(e){}bounded(alternate,imdb,{url:image,until:Date.now()+(image?86400000:60000)});return image;})();pending.set(imdb,task);try{return await task;}finally{pending.delete(imdb);}
}
function fraction(p){return p&&Number.isFinite(p.positionMs)&&Number.isFinite(p.durationMs)&&p.durationMs>0?Math.max(0,Math.min(1,p.positionMs/p.durationMs)):0;}
function attach(t,image,placeholder){
 const queue=candidates(t),tried=new Set();let live=true,looking=false,timer,observer,visible=false;const arm=()=>{clearTimeout(timer);timer=setTimeout(()=>next(),8000);if(timer.unref)timer.unref();};
 const next=async()=>{if(!live)return;clearTimeout(timer);const candidate=queue.find(u=>!tried.has(u));if(candidate){tried.add(candidate);image.hidden=false;placeholder.hidden=true;image.src=candidate;if(visible)arm();return;}if(!looking){looking=true;const extra=await lookup(t);if(!live)return;if(extra&&!tried.has(extra)){queue.push(extra);return next();}}image.hidden=true;image.removeAttribute('src');placeholder.hidden=false;};
 image.onload=()=>{if(!live)return;clearTimeout(timer);bounded(preferred,id(t),image.src);image.hidden=false;placeholder.hidden=true;};image.onerror=()=>{next();};const Observer=image.ownerDocument&&image.ownerDocument.defaultView&&image.ownerDocument.defaultView.IntersectionObserver;if(Observer&&image.parentElement){observer=new Observer(entries=>{if(entries.some(e=>e.isIntersecting)){visible=true;observer.disconnect();if(live&&!image.hidden&&!(image.complete&&image.naturalWidth>0))arm();}},{rootMargin:'200px'});observer.observe(image.parentElement);}else visible=true;next();
 return ()=>{live=false;clearTimeout(timer);if(observer)observer.disconnect();image.onload=null;image.onerror=null;};
}
module.exports={id,url,candidates,tvmaze,lookup,fraction,attach};
