'use strict';
const https=require('https'),fs=require('fs'),crypto=require('crypto'),path=require('path');
const policy=require('./update-policy');
const FEED='https://raw.githubusercontent.com/Shaurya609/StreamNest/main/updates/stable.json';
function response(url,redirects=0){return new Promise((resolve,reject)=>{
 const req=https.get(url,{headers:{'User-Agent':'StreamNest-Updater','Accept-Encoding':'identity'}},res=>{
 if([301,302,303,307,308].includes(res.statusCode)){
  res.resume();const next=new URL(res.headers.location,url);if(next.protocol!=='https:'||redirects>=5)return reject(Error('Unsafe update redirect'));
  return response(next.href,redirects+1).then(resolve,reject);
 }
 if(res.statusCode!==200){res.resume();return reject(Error('Update server HTTP '+res.statusCode));}resolve(res);
 });req.setTimeout(20000,()=>req.destroy(Error('Update download timed out')));req.on('error',reject);
});}
async function manifest(key){const res=await response(FEED),chunks=[];let count=0;for await(const b of res){count+=b.length;if(count>100000){res.destroy();throw Error('Manifest too large');}chunks.push(b);}return policy.verify(Buffer.concat(chunks),key);}
async function download(item,folder,progress){
 policy.artifact(item);fs.mkdirSync(folder,{recursive:true});const target=path.join(folder,item.sha256+'.exe');
 if(fs.existsSync(target)){const bytes=fs.readFileSync(target);if(bytes.length===item.bytes&&crypto.createHash('sha256').update(bytes).digest('hex')===item.sha256)return target;fs.unlinkSync(target);}
 const temp=target+'.part';const hash=crypto.createHash('sha256');let count=0;const res=await response(item.url);const out=await fs.promises.open(temp,'w');const deadline=Date.now()+900000;
 try{for await(const b of res){if(Date.now()>deadline)throw Error('Update download timed out');count+=b.length;if(count>item.bytes)throw Error('Installer size mismatch');hash.update(b);let written=0;while(written<b.length){const result=await out.write(b,written,b.length-written);if(!result.bytesWritten)throw Error('Cannot write update');written+=result.bytesWritten;}if(progress)progress(count,item.bytes);}
 await out.close();
 if(count!==item.bytes||hash.digest('hex')!==item.sha256)throw Error('Installer checksum rejected');fs.renameSync(temp,target);return target;
 }catch(error){res.destroy();try{await out.close();}catch(ignore){}try{fs.unlinkSync(temp);}catch(ignore){}throw error;}
}
module.exports={manifest,download};
