"use strict";
const https=require('https');
function text(url,redirects=0){return new Promise((resolve,reject)=>{
 if(redirects>4)return reject(Error('Too many metadata redirects'));
 if(new URL(url).protocol!=='https:')return reject(Error('HTTPS required'));
 const req=https.get(url,{headers:{'User-Agent':new URL(url).hostname==='yts.gg'?'okhttp/3.14.7':'StreamNest/0.8.0','Accept':'application/json, application/rss+xml'}},res=>{
 if([301,302,303,307,308].includes(res.statusCode)&&res.headers.location){res.resume();text(new URL(res.headers.location,url).href,redirects+1).then(resolve,reject);return;}
 if(res.statusCode!==200){res.resume();reject(Error('Metadata HTTP '+res.statusCode));return;}
 let value='';res.setEncoding('utf8');res.on('data',c=>{value+=c;if(value.length>16000000)req.destroy(Error('Response too large'));});res.on('end',()=>resolve(value));res.on('error',reject);
 });req.setTimeout(10000,()=>req.destroy(Error('Metadata timeout')));req.on('error',reject);
});}
module.exports=async url=>JSON.parse(await text(url));module.exports.text=text;
