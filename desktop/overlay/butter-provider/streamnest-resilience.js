"use strict";
const fs=require('fs'),path=require('path'),crypto=require('crypto');
const net=require('./streamnest-net');
const defaults=['https://yts.gg/api/v2/','https://movies-api.accel.li/api/v2/','https://yts.bz/api/v2/'];
const mirrors=['https://raw.githubusercontent.com/Shaurya609/StreamNest/catalog-data/','https://cdn.jsdelivr.net/gh/Shaurya609/StreamNest@catalog-data/'];
const hash=value=>crypto.createHash('sha256').update(value).digest('hex');
function atomic(file,value){fs.mkdirSync(path.dirname(file),{recursive:true});const tmp=file+'.tmp';fs.writeFileSync(tmp,typeof value==='string'?value:JSON.stringify(value));fs.renameSync(tmp,file);}
function read(file){try{return JSON.parse(fs.readFileSync(file,'utf8'));}catch(e){return null;}}
function verify(bytes,key){const env=JSON.parse(bytes),payload=Buffer.from(env.payload,'base64');const verifier=crypto.createVerify('RSA-SHA256');verifier.update(payload);verifier.end();if(!verifier.verify(key,Buffer.from(env.signature,'base64')))throw Error('Catalog signature rejected');const manifest=JSON.parse(payload);if(manifest.schema!==1)throw Error('Unsupported catalog schema');return manifest;}
function check(kind,entry,bytes){if(!new RegExp('^catalogs/v1/data/'+kind+'-[0-9a-f]{16}\\.json$').test(entry.path)||bytes.length!==entry.bytes||bytes.length>16000000||hash(bytes)!==entry.sha256)throw Error('Catalog checksum rejected');const doc=JSON.parse(bytes),rows=doc[kind==='movies'?'movies':kind==='latest'?'releases':'series'];if(!Array.isArray(rows)||rows.length!==entry.count||rows.length>30000||kind!=='latest'&&!rows.length||doc.saved!==entry.saved)throw Error('Invalid catalog records');return doc;}
class Catalogs {
 constructor(options={}){
  this.root=options.root||null;this.fetch=options.fetch||net;this.clock=options.clock||Date.now;this.health=new Map();this.sessions=new Map();this.pages={};this.known=new Map();this.bases=defaults.slice();this.preferred=this.bases[0];this.saved=false;
  this.bundles=options.bundles||{};for(const kind of ['movies','shows','animes','latest'])if(!this.bundles[kind])this.bundles[kind]=read(path.join(__dirname,'catalog-'+kind+'-v1.json'));
  const doc=this.catalog('movies');for(const m of doc&&doc.movies||[])this.known.set(m.imdb_code,m);
  if(this.root){const state=read(path.join(this.root,'state.json'));if(state){this.pages=state.pages||{};if(this.bases.includes(state.preferred))this.preferred=state.preferred;}const known=read(path.join(this.root,'movies-known.json'));for(const m of known&&known.movies||[])if(!this.known.has(m.imdb_code)||Number(m.catalogVerifiedAt||0)>=Number(this.known.get(m.imdb_code).catalogVerifiedAt||0))this.known.set(m.imdb_code,m);}
 }
 catalog(kind){const disk=this.root&&read(path.join(this.root,kind+'.json')),bundle=this.bundles[kind],field=kind==='movies'?'movies':kind==='latest'?'releases':'series';return disk&&Array.isArray(disk[field])&&(kind==='latest'||disk[field].length)&&(!bundle||disk.saved>=bundle.saved)?disk:bundle;}
 candidates(hosts,preferred){const now=this.clock(),ready=hosts.filter(h=>!this.health.has(h)||this.health.get(h).retryAt<=now);if(ready.includes(preferred)){ready.splice(ready.indexOf(preferred),1);ready.unshift(preferred);}return ready;}
 failed(host,error){require('./streamnest-diagnostics').failure('catalog.failed','catalog','fetch',error);if(/HTTP\s*(400|404|410|422)\b/.test(String(error&&error.message)))return;const old=this.health.get(host)||{count:0};const count=Math.min(5,old.count+1);this.health.set(host,{count,retryAt:this.clock()+Math.min(300000,15000*Math.pow(2,count))});}
 succeeded(host){this.health.delete(host);}
 notice(saved){this.saved=this.saved||saved;if(typeof document!=='undefined'){const badge=document.getElementById('streamnest-catalog-status');if(badge){badge.textContent=this.saved?'Saved catalog':'';badge.style.display=this.saved?'inline':'none';}}}
 begin(){this.saved=false;this.notice(false);}
 persist(){if(!this.root)return;try{atomic(path.join(this.root,'movies-known.json'),{saved:this.clock(),movies:[...this.known.values()]});const feeds=Object.keys(this.pages);while(feeds.length>12)delete this.pages[feeds.shift()];atomic(path.join(this.root,'state.json'),{preferred:this.preferred,pages:this.pages});}catch(e){}}
 remember(raw){
  const row=JSON.parse(JSON.stringify(raw));if(!/^tt\d+$/.test(row.imdb_code||'')||!row.title)return;
  row.language=({eng:'en',hin:'hi',jpn:'ja',kor:'ko'}[row.language]||row.language);row.description_full=String(row.description_full||row.summary||'').slice(0,1200);row.catalogVerifiedAt=this.clock();
  this.known.set(row.imdb_code,row);
 }
 parameters(request){const url=new URL(request,'https://catalog.invalid/');if(url.pathname!=='/list_movies.json')throw Error('Unsupported saved movie request');const p={};for(const [k,v]of url.searchParams)p[k]=v;if(!p.limit)p.limit='50';if(!p.page)p.page='1';return p;}
 feed(p){const out={};for(const key of Object.keys(p).sort())if(key!=='page')out[key]=p[key];return JSON.stringify(out);}
 matching(p){const q=String(p.query_term||'').toLowerCase(),genre=String(p.genre||'').toLowerCase().replace(/science[ -]fiction/g,'sci-fi'),quality=p.quality;let rows=[...this.known.values()].filter(m=>(!q||m.imdb_code.toLowerCase()===q||m.title.toLowerCase().includes(q))&&(!genre||genre==='all'||(m.genres||[]).some(g=>g.toLowerCase().replace(/science[ -]fiction/g,'sci-fi')===genre))&&(!quality||(m.torrents||[]).some(t=>t.quality===quality)));
  const sort=p.sort_by||'date_added',seeds=m=>Math.max(0,...(m.torrents||[]).map(t=>Number(t.seeds)||0)),direction=p.order_by==='asc'?1:-1;
  rows.sort((a,b)=>{const numeric=value=>Number(value)||0;let n;
   if(sort==='title'){const aa=a.title.toLowerCase(),bb=b.title.toLowerCase();n=aa<bb?-1:aa>bb?1:0;}
   else n=sort==='year'?numeric(a.year)-numeric(b.year):sort==='rating'?numeric(a.rating)-numeric(b.rating):sort==='seeds'?seeds(a)-seeds(b):sort==='download_count'?(Number(b.popularityRank||2147483647)-Number(a.popularityRank||2147483647)||seeds(a)-seeds(b)):(numeric(a.date_uploaded_unix)-numeric(b.date_uploaded_unix));
   const tie=(sort==='title'?direction===1:direction===-1)?1:-1;return direction*n||tie*a.imdb_code.localeCompare(b.imdb_code);
  });return rows.map(m=>m.imdb_code);
 }
 savedMovies(request,token){const p=this.parameters(request),feed=this.feed(p);let ids=token&&this.sessions.get(feed+'|'+token);if(!ids){ids=[];const seen=new Set();if(token){const pages=this.pages[feed]||{};for(let n=1;pages[n];n++)for(const id of pages[n]){ids.push(id);seen.add(id);}}
  for(const id of this.matching(p))if(!seen.has(id)){seen.add(id);ids.push(id);}
  if(token){this.sessions.set(feed+'|'+token,ids);while(this.sessions.size>4)this.sessions.delete(this.sessions.keys().next().value);}}
  if(!this.known.size)return null;const limit=Math.max(1,Math.min(50,Number(p.limit)||50)),start=(Math.max(1,Number(p.page)||1)-1)*limit;
  this.notice(true);return {movie_count:ids.length,movies:ids.slice(start,start+limit).map(id=>this.known.get(id)).filter(Boolean),catalog_saved:true};
 }
 async movieData(request,options={}){
  const p=this.parameters(request),feed=this.feed(p);if(options.token&&Number(p.page)>1&&this.sessions.has(feed+'|'+options.token))return this.savedMovies(request,options.token);
  const deadline=this.clock()+14000;let error;
  for(const base of this.candidates(this.bases,this.preferred)){if(this.clock()>=deadline)break;try{
   const response=await this.fetch(base+request,deadline);const data=response.data;
   if(response.status!=='ok'||!data||data.movies!=null&&!Array.isArray(data.movies))throw Error('Invalid movie response');
   const rows=data.movies||[];if(data.movie_count>0&&!rows.length&&(Number(p.page)-1)*Number(p.limit)<data.movie_count)throw Error('Incomplete movie response');
   if(Number(p.page)===1&&!p.query_term&&!rows.length&&(((this.pages[feed]||{})[1]||[]).length||this.matching(p).length))throw Error('Empty catalog refresh rejected');
   this.preferred=base;this.succeeded(base);for(const m of rows)this.remember(m);if(!this.pages[feed])this.pages[feed]={};this.pages[feed][p.page]=rows.map(m=>m.imdb_code);this.persist();return data;
  }catch(e){error=e;this.failed(base,e);}}
  const saved=this.savedMovies(request,options.token);if(saved)return saved;throw error||Error('Movie catalog temporarily unavailable');
 }
 async searchSeries(query){const data=await this.metadata('https://api.tvmaze.com/search/shows?q='+encodeURIComponent(query),Array.isArray);const seen=new Set(),out=[];
  for(const item of data){const m=item.show,id=m&&m.externals&&m.externals.imdb;if(!/^tt\d+$/.test(id||'')||seen.has(id))continue;const language={English:'en',Hindi:'hi',Japanese:'ja',Korean:'ko'}[m.language];if(!language)continue;seen.add(id);out.push({imdb:id,title:m.name,language,year:String(m.premiered||'').slice(0,4),rating:m.rating&&m.rating.average||0,genres:m.genres||[],poster_big:m.image&&(m.image.original||m.image.medium)||'',description:String(m.summary||'').replace(/<[^>]*>/g,' ').slice(0,1200),metadataOnly:true,metadataSource:'TVmaze',trendRank:2147483647,latestRank:2147483647});}return out;
 }
 async metadata(url,validate){
  const host=new URL(url).hostname,file=this.root&&path.join(this.root,'http',hash(url)+'.json');let error;
  if(this.candidates([host],host).length)try{const value=await this.fetch(url,this.clock()+8000);if(!validate(value))throw Error('Invalid metadata response');this.succeeded(host);if(file)try{atomic(file,value);}catch(e){}return value;}catch(e){error=e;this.failed(host,e);}
  const old=file&&read(file);if(old&&validate(old)){this.notice(true);return old;}throw error||Error('Metadata temporarily unavailable');
 }
 configure(manifest){const hosts=manifest.providers&&manifest.providers.movies;if(!Array.isArray(hosts))return;const valid=hosts.filter(s=>{try{const u=new URL(s);return u.protocol==='https:'&&!u.username&&!u.password&&!u.search&&!u.hash&&!u.port&&u.pathname.endsWith('/api/v2/');}catch(e){return false;}}).slice(0,6);if(valid.length){this.bases=valid;if(!valid.includes(this.preferred))this.preferred=valid[0];}}
 async sync(){
  if(!this.root||this.syncing)return;this.syncing=true;
  try{
   const key=fs.readFileSync(path.join(__dirname,'catalog-public-key.pem'),'utf8');const acceptedFile=path.join(this.root,'accepted-manifest.json');let previous;try{previous=verify(fs.readFileSync(acceptedFile),key);this.configure(previous);}catch(e){}
   let manifest,envelope;for(const base of mirrors)try{const value=await net.text(base+'catalogs/v1/manifest.json');manifest=verify(value,key);envelope=value;break;}catch(e){}
   if(!manifest)throw Error('Catalog mirrors unavailable');if(previous&&manifest.generatedAt<previous.generatedAt)throw Error('Older catalog manifest rejected');this.configure(manifest);if(previous&&manifest.version===previous.version)return;
   const docs={};for(const kind of ['movies','shows','animes','latest']){const entry=manifest.files[kind];if(!new RegExp('^catalogs/v1/data/'+kind+'-[0-9a-f]{16}\\.json$').test(entry.path))throw Error('Unsafe snapshot path');for(const base of mirrors)try{const bytes=Buffer.from(await net.text(base+entry.path),'utf8');docs[kind]=check(kind,entry,bytes);break;}catch(e){}if(!docs[kind])throw Error('Snapshot refresh retained until every file is verified');}
   for(const kind of Object.keys(docs)){const old=this.catalog(kind);if(manifest.rollback||!old||docs[kind].saved>old.saved)atomic(path.join(this.root,kind+'.json'),docs[kind]);}
   for(const m of docs.movies.movies){const old=this.known.get(m.imdb_code);if(!old||Number(m.catalogVerifiedAt||0)>=Number(old.catalogVerifiedAt||0))this.known.set(m.imdb_code,m);}this.persist();atomic(acceptedFile,envelope);
  }catch(error){require('./streamnest-diagnostics').failure('catalog.failed','catalog','verify',error);throw error;}finally{this.syncing=false;}
 }
}
const root=typeof nw!=='undefined'?path.join(nw.App.dataPath,'streamnest-catalogs'):null;
const instance=new Catalogs({root});module.exports=instance;module.exports.Catalogs=Catalogs;module.exports.verify=verify;module.exports.check=check;
