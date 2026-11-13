'use strict';
const https=require('https'),zlib=require('zlib');
let token='',expires=0,loginTask=null;
const downloads=new Map();
const array=value=>Array.isArray(value)?value:value?[value]:[];
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&apos;'}[c]));
function encode(value) {
 if(Array.isArray(value))return '<value><array><data>'+value.map(encode).join('')+'</data></array></value>';
 if(typeof value==='object')return '<value><struct>'+Object.entries(value).map(([key,item])=>'<member><name>'+escape(key)+'</name>'+encode(item)+'</member>').join('')+'</struct></value>';
 if(typeof value==='number')return '<value><int>'+value+'</int></value>';
 return '<value><string>'+escape(value)+'</string></value>';
}
function decode(value) {
 if(value==null)return '';
 if(typeof value!=='object')return value;
 if(value.struct){const out={};for(const member of array(value.struct.member))out[member.name]=decode(member.value);return out;}
 if(value.array)return array(value.array.data&&value.array.data.value).map(decode);
 if(value.boolean!==undefined)return Number(value.boolean)!==0;
 if(value.int!==undefined||value.i4!==undefined)return Number(value.int===undefined?value.i4:value.int);
 return value.string===undefined?'':value.string;
}
function rpc(method,params) {
 const body='<?xml version="1.0"?><methodCall><methodName>'+method+'</methodName><params>'+params.map(v=>'<param>'+encode(v)+'</param>').join('')+'</params></methodCall>';
 return new Promise((resolve,reject)=>{
  const req=https.request('https://api.opensubtitles.org/xml-rpc',{method:'POST',headers:{'Content-Type':'text/xml','User-Agent':'Popcorn Time NodeJS','Accept-Encoding':'identity','Content-Length':Buffer.byteLength(body)}},res=>{
   if(res.statusCode!==200){res.resume();return reject(Error('OpenSubtitles.org HTTP '+res.statusCode));}
   const chunks=[];let size=0;res.on('data',chunk=>{size+=chunk.length;if(size>8000000)req.destroy(Error('Subtitle response too large'));else chunks.push(chunk);});res.on('error',reject);
   res.on('end',()=>{try{
    let bytes=Buffer.concat(chunks);if(bytes[0]===31&&bytes[1]===139)bytes=zlib.gunzipSync(bytes);
    const text=bytes.toString('utf8');if(/<!DOCTYPE|<!ENTITY/i.test(text))throw Error('Unsafe XML response');
    require('xml2js').parseString(text,{explicitArray:false,trim:true},(error,doc)=>{
     if(error)return reject(error);
     try {if(doc.methodResponse.fault)throw Error('Subtitle API fault');const result=decode(doc.methodResponse.params.param.value);if(!/^200/.test(result.status||''))throw Error(result.status||'Subtitle API error');resolve(result);}catch(error){reject(error);}
    });
   }catch(error){reject(error);}});
  });req.setTimeout(12000,()=>req.destroy(Error('OpenSubtitles.org timed out')));req.on('error',reject);req.end(body);
 });
}
async function login(force=false) {
 if(!force&&token&&Date.now()<expires)return true;
 if(loginTask)return loginTask;
 loginTask=(async()=>{const result=await rpc('LogIn',['','','en','Popcorn Time NodeJS']);
 if(!result.token)throw Error('No anonymous subtitle session');token=result.token;expires=Date.now()+600000;return true;})();
 try{return await loginTask;}finally{loginTask=null;}
}
async function sessionRpc(method,params) {
 await login();
 try{return await rpc(method,[token,...params]);}
 catch(error){if(!/\b(?:401|406)\b/.test(error.message))throw error;
 token='';expires=0;await login();return rpc(method,[token,...params]);}
}
async function search(query) {
 await login();const criteria={sublanguageid:'all'};
 if(query.imdbid&&/^tt\d+$/.test(query.imdbid))criteria.imdbid=query.imdbid.slice(2);
 if(query.season!=null&&query.episode!=null){criteria.season=String(query.season);criteria.episode=String(query.episode);}
 if(!criteria.imdbid&&query.filename)criteria.query=query.filename;
 if(!criteria.imdbid&&!criteria.query)return {};
 const requested=[Object.assign({},criteria,{sublanguageid:'eng'}),Object.assign({},criteria,{sublanguageid:'hin'}),criteria];
 const response=await sessionRpc('SearchSubtitles',[requested,{limit:500}]);const out={};
 for(const item of array(response.data)) {
  if(item.SubFormat!=='srt'||!/^https?:\/\//.test(item.SubDownloadLink||''))continue;
  const language=item.ISO639||item.SubLanguageID;if(!language)continue;
  if(item.IDSubtitleFile){downloads.set(item.SubDownloadLink,String(item.IDSubtitleFile));while(downloads.size>1000)downloads.delete(downloads.keys().next().value);}
  (out[language]||(out[language]=[])).push({url:item.SubDownloadLink,langcode:language});
 }
 return out;
}
async function download(url) {
 const id=downloads.get(url);if(!id)return null;
 const result=await sessionRpc('DownloadSubtitles',[[id]]);
 const row=array(result.data)[0];if(!row||!row.data)throw Error('Subtitle download was empty');
 return zlib.gunzipSync(Buffer.from(row.data,'base64'),{maxOutputLength:8000000});
}
module.exports={login,search,download,encode,decode,sessionRpc};

for(const name of ["login","search","download"]){const original=module.exports[name];module.exports[name]=async(...args)=>{try{return await original(...args);}catch(error){require("./streamnest-diagnostics").failure("subtitle.failed","subtitles",name==="download"?"download":"fetch",error);throw error;}};}
