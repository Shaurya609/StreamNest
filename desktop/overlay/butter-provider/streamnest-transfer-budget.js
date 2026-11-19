'use strict';
const hashState=require('./streamnest-piece-hash').state;
// Backpressure at the verified-write boundary. Already-requested blocks and hash
// verification finish normally; no partial pieces, cache files or peers are discarded.
function install(torrent,preload=false){
 const piece=Array.isArray(torrent.pieces)&&torrent.pieces.find(p=>p&&p.constructor?.name==='Piece'&&typeof p.flush==='function');if(piece)require('./streamnest-piece-memory').install(piece.constructor);
 if(torrent.streamnestTransferBudget){const state=torrent.streamnestTransferBudget;if(state.preload&&!preload){state.preload=false;state.cacheBytes=32*1024*1024;state.writeBytes=64*1024*1024;}return state;}
 if(!torrent.store?.put||typeof torrent._request!=='function')return null;
 const cacheBytes=(preload?8:32)*1024*1024,writeBytes=(preload?32:64)*1024*1024;
 const state={preload,pendingBytes:0,peakPendingBytes:0,blockedRequests:0,cacheBytes,writeBytes};
 torrent.streamnestTransferBudget=state;
 const cache=torrent.store.store?.cache;
 if(cache&&typeof cache.set==='function'&&typeof cache.peek==='function'&&typeof cache.remove==='function'){
  const set=cache.set.bind(cache);
  cache.set=(key,value)=>value?.length>state.cacheBytes?value:set(key,value);
  cache.max=Math.max(1,Math.min(cache.max,Math.floor(cacheBytes/torrent.pieceLength)||1));
  for(const key of cache.keys)if(cache.peek(key)?.length>cacheBytes)cache.remove(key);
  while(cache.length>cache.max)cache.evict();
 }
 const put=torrent.store.put.bind(torrent.store),request=torrent._request;
 torrent.store.put=function(index,bytes,callback=()=>{}){
  state.pendingBytes+=bytes.length;state.peakPendingBytes=Math.max(state.peakPendingBytes,state.pendingBytes);
  let settled=false;
  const done=error=>{if(settled)return;settled=true;state.pendingBytes-=bytes.length;callback(error);};
  try{return put(index,bytes,done);}catch(error){if(settled)throw error;done(error);}
 };
 torrent._request=function(...args){if(state.pendingBytes+hashState.pendingBytes>=state.writeBytes){state.blockedRequests++;return false;}return request.apply(this,args);};
 return state;
}
module.exports={install};
