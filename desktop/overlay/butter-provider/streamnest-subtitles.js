'use strict';
const fs=require('fs'),zlib=require('zlib');
const LIMIT=8000000;
function unpack(bytes) {
 if(bytes.length>LIMIT)throw Error('Subtitle is too large');
 if(bytes[0]===31&&bytes[1]===139)bytes=zlib.gunzipSync(bytes,{maxOutputLength:LIMIT});
 else if(bytes[0]===80&&bytes[1]===75){
  const entry=new (require('adm-zip'))(bytes).getEntries().find(e=>!e.isDirectory&&/\.srt$/i.test(e.entryName));
  if(!entry||entry.header.size>LIMIT)throw Error('No usable SRT in subtitle archive');
  bytes=entry.getData();
 }
 if(bytes.length>LIMIT)throw Error('Subtitle is too large');
 const encoding=require('jschardet').detect(bytes).encoding||'utf8';
 const text=require('iconv-lite').decode(bytes,encoding).replace(/^\uFEFF/,'');
 if(text.length<2048&&/Become OpenSubtitles\.org VIP Member/i.test(text))throw Error('OpenSubtitles.org returned a VIP notice instead of subtitles. Try OpenSubtitles.com or another subtitle.');
 if(!/\d{1,2}:\d{2}:\d{2}[,.]\d{3}\s*-->/.test(text))throw Error('Subtitle has no valid timings');
 return Buffer.from(text,'utf8');
}
async function read(source) {
 if(fs.existsSync(source))return unpack(await fs.promises.readFile(source));
 if(!/^https?:\/\//i.test(source))throw Error('Invalid subtitle URL');
 let bytes;try{bytes=await require('./streamnest-org').download(source);if(bytes)return unpack(bytes);}catch(error){/* Direct URL remains a fallback for rejected RPC downloads. */}
 return new Promise((resolve,reject)=>{
  let size=0,chunks=[];
  const req=require('request').get({url:source,encoding:null,timeout:15000,followAllRedirects:false});
  const deadline=setTimeout(()=>{req.abort();reject(Error('Subtitle download timed out'));},20000);
  const fail=error=>{clearTimeout(deadline);req.abort();reject(error);};
  req.on('response',res=>{if(res.statusCode!==200)fail(Error('Subtitle download HTTP '+res.statusCode));});
  req.on('data',chunk=>{size+=chunk.length;if(size>LIMIT)fail(Error('Subtitle is too large'));else chunks.push(chunk);});
  req.on('error',fail);
  req.on('end',()=>{clearTimeout(deadline);try{resolve(unpack(Buffer.concat(chunks)));}catch(error){reject(error);}});
 });
}
module.exports={read:async(...args)=>{try{return await read(...args);}catch(error){require("./streamnest-diagnostics").failure("subtitle.failed","subtitles","download",error);throw error;}},unpack};
