"use strict";
const https=require('https');
function text(url,redirects=0,deadline=Date.now()+14000){return new Promise((resolve,reject)=>{
 if(Date.now()>=deadline)return reject(Error('Metadata deadline'));
 if(redirects>4)return reject(Error('Too many metadata redirects'));
 if(new URL(url).protocol!=='https:')return reject(Error('HTTPS required'));
 const req=https.get(url,{headers:{'User-Agent':['yts.gg','api.apiabcd.com'].includes(new URL(url).hostname)?'okhttp/3.14.7':'StreamNest/0.8.0','Accept':'application/json, application/rss+xml'}},res=>{
 if([301,302,303,307,308].includes(res.statusCode)&&res.headers.location){res.resume();text(new URL(res.headers.location,url).href,redirects+1,deadline).then(resolve,reject);return;}
 if(res.statusCode!==200){res.resume();reject(Error('Metadata HTTP '+res.statusCode));return;}
 let value='';res.setEncoding('utf8');res.on('data',c=>{value+=c;if(value.length>16000000)req.destroy(Error('Response too large'));});res.on('end',()=>resolve(value));res.on('error',reject);
 });const timer=setTimeout(()=>req.destroy(Error('Metadata deadline')),Math.max(1,deadline-Date.now()));req.on('close',()=>clearTimeout(timer));req.setTimeout(Math.min(8000,Math.max(1,deadline-Date.now())),()=>req.destroy(Error('Metadata timeout')));req.on('error',reject);
});}
module.exports=async (url,deadline)=>{try{return JSON.parse(await text(url,0,deadline));}catch(error){require('./streamnest-diagnostics').failure('catalog.failed','catalog','fetch',error);throw error;}};module.exports.text=(...args)=>text(...args).catch(error=>{require('./streamnest-diagnostics').failure('catalog.failed','catalog','fetch',error);throw error;});

module.exports.bytes=(url,deadline=Date.now()+8000,limit=4000000)=>new Promise((resolve,reject)=>{
 if(new URL(url).protocol!=='https:'||Date.now()>=deadline)return reject(Error('Metadata deadline'));
 const req=https.get(url,{headers:{'User-Agent':'StreamNest','Accept':'application/x-bittorrent'}},res=>{
  if(res.statusCode!==200){res.resume();return reject(Error('Torrent metadata HTTP '+res.statusCode));}
  let size=0,chunks=[];res.on('data',b=>{size+=b.length;if(size>limit)req.destroy(Error('Torrent metadata too large'));else chunks.push(b);});res.on('error',reject);res.on('end',()=>resolve(Buffer.concat(chunks)));
 });const timer=setTimeout(()=>req.destroy(Error('Metadata deadline')),Math.max(1,deadline-Date.now()));req.on('close',()=>clearTimeout(timer));req.on('error',reject);
});
