"use strict";
const https=require('https');
function text(url,redirects=0,deadline=Date.now()+14000){return new Promise((resolve,reject)=>{
 if(Date.now()>=deadline)return reject(Error('Metadata deadline'));
 if(redirects>4)return reject(Error('Too many metadata redirects'));
 if(new URL(url).protocol!=='https:')return reject(Error('HTTPS required'));
 const req=https.get(url,{headers:{'User-Agent':['yts.gg','api.apiabcd.com'].includes(new URL(url).hostname)?'okhttp/3.14.7':'StreamNest/0.8.0','Accept':'application/json, application/rss+xml'}},res=>{
 if([301,302,303,307,308].includes(res.statusCode)&&res.headers.location){res.resume();text(new URL(res.headers.location,url).href,redirects+1,deadline).then(resolve,reject);return;}
 if(res.statusCode!==200){res.resume();reject(Object.assign(Error('Metadata HTTP '+res.statusCode),{status:res.statusCode}));return;}
 let value='';res.setEncoding('utf8');res.on('data',c=>{value+=c;if(value.length>16000000)req.destroy(Error('Response too large'));});res.on('end',()=>resolve(value));res.on('error',reject);
 });const timer=setTimeout(()=>req.destroy(Error('Metadata deadline')),Math.max(1,deadline-Date.now()));req.on('close',()=>clearTimeout(timer));req.setTimeout(Math.min(8000,Math.max(1,deadline-Date.now())),()=>req.destroy(Error('Metadata timeout')));req.on('error',reject);
});}
function provider(url){try{const h=new URL(url).hostname;return h.includes('cinemeta')?'cinemeta':h==='api.tvmaze.com'?'tvmaze':h==='api.ani.zip'?'anizip':h==='api.apiabcd.com'?'regional':'other';}catch(_){return 'other';}}
const absent=new Map(),pending=new Map();
module.exports=async (url,deadline,options={})=>{
 const optional=options.allowNotFound===true;
 if(optional&&absent.get(url)>Date.now())return null;
 // Different deadlines and optional/required policies must not inherit each other's failure.
 const key=JSON.stringify([url,deadline??null,optional]);
 let task=pending.get(key);
 if(!task){task=(async()=>{try{return await text(url,0,deadline);}catch(error){
  if(optional&&error.status===404){absent.delete(url);absent.set(url,Date.now()+300000);while(absent.size>128)absent.delete(absent.keys().next().value);return null;}
  require('./streamnest-diagnostics').failure('catalog.failed','catalog','fetch',error,{metadataProvider:provider(url)});throw error;
 }})();pending.set(key,task);}
 try{const raw=await task;if(raw===null)return null;try{return JSON.parse(raw);}catch(error){require('./streamnest-diagnostics').failure('catalog.failed','catalog','fetch',error,{metadataProvider:provider(url)});throw error;}}finally{if(pending.get(key)===task)pending.delete(key);}
};module.exports.text=(...args)=>text(...args).catch(error=>{require('./streamnest-diagnostics').failure('catalog.failed','catalog','fetch',error,{metadataProvider:provider(args[0])});throw error;});

module.exports.bytes=(url,deadline=Date.now()+8000,limit=4000000)=>new Promise((resolve,reject)=>{
 if(new URL(url).protocol!=='https:'||Date.now()>=deadline)return reject(Error('Metadata deadline'));
 const req=https.get(url,{headers:{'User-Agent':'StreamNest','Accept':'application/x-bittorrent'}},res=>{
  if(res.statusCode!==200){res.resume();return reject(Error('Torrent metadata HTTP '+res.statusCode));}
  let size=0,chunks=[];res.on('data',b=>{size+=b.length;if(size>limit)req.destroy(Error('Torrent metadata too large'));else chunks.push(b);});res.on('error',reject);res.on('end',()=>resolve(Buffer.concat(chunks)));
 });const timer=setTimeout(()=>req.destroy(Error('Metadata deadline')),Math.max(1,deadline-Date.now()));req.on('close',()=>clearTimeout(timer));req.on('error',reject);
});
