'use strict';
const crypto=require('crypto'),path=require('path');
const state={pendingBytes:0,peakPendingBytes:0};
function create(original,{budget=state,schedule=setImmediate,chunkBytes=256*1024}={}){
 function sha1(bytes,callback){
  if(!Buffer.isBuffer(bytes)||bytes.length<1024*1024)return original(bytes,callback);
  budget.pendingBytes+=bytes.length;budget.peakPendingBytes=Math.max(budget.peakPendingBytes,budget.pendingBytes);
  const hash=crypto.createHash('sha1');let offset=0,settled=false;
  function done(digest){if(settled)return;settled=true;budget.pendingBytes-=bytes.length;callback(digest);}
  function step(){let digest;try{const end=Math.min(bytes.length,offset+chunkBytes);hash.update(bytes.subarray(offset,end));offset=end;if(offset===bytes.length)digest=hash.digest('hex');}catch(error){done('');return;}
   if(digest!==undefined)done(digest);else schedule(step);
  }
  schedule(step);
 }
 sha1.sync=original.sync;sha1.streamnestCooperative=true;return sha1;
}
function install(load){try{
 const parent=path.dirname(load.resolve('webtorrent'));
 const id=load.resolve('simple-sha1',{paths:[parent]});
 const metadata=load(load.resolve('simple-sha1/package.json',{paths:[parent]}));
 if(metadata.version!=='3.1.0')return false;
 const original=load(id);if(original.streamnestCooperative)return true;
 if(typeof original!=='function'||typeof original.sync!=='function')return false;
 load.cache[id].exports=create(original);return true;
 }catch(error){return false;}}
module.exports={create,install,state};
