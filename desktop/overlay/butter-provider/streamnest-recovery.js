'use strict';
// A timeout bounds the UI wait, never proves that an old writer stopped.
function create(defaultMs=6000){
 const pending=new Set();
 const bounded=(promise,ms)=>new Promise((resolve,reject)=>{const timer=setTimeout(()=>reject(Error('Previous stream is still stopping. Its cache is protected; wait and try again.')),ms);promise.then(v=>{clearTimeout(timer);resolve(v);},e=>{clearTimeout(timer);reject(e);});});
 function track(operation,ms=defaultMs){let done;const item={};item.promise=new Promise((resolve,reject)=>{done=error=>{if(item.finished)return;item.finished=true;if(error){item.error=error;reject(error);}else{pending.delete(item);resolve();}};});pending.add(item);item.promise.catch(()=>{});try{operation(done);}catch(e){done(e);}const result=bounded(item.promise,ms);result.completion=item.promise;result.catch(()=>{});return result;}
 function wait(ms=defaultMs){return bounded(Promise.all(Array.from(pending,item=>item.promise)),ms);}
 return {track,wait,get pending(){return pending.size;}};
}
function listen(factory,port,current=()=>true,ms=4000){return new Promise((resolve,reject)=>{
 let server,finished=false,retried=false;const close=s=>{try{s?.destroy?s.destroy(()=>{}):s?.close(()=>{});}catch(e){}};
 const finish=(error,value)=>{if(finished)return;finished=true;clearTimeout(timer);clearInterval(check);if(error){close(server);reject(error);}else resolve(value);};
 const timer=setTimeout(()=>finish(Error('Local video server startup timed out')),ms),check=setInterval(()=>{if(!current())finish(Error('Cancelled'));},100);
 const open=value=>{if(!current())return finish(Error('Cancelled'));try{server=factory();const owned=server;server.on('error',error=>{if(finished||owned!==server)return;if(error.code==='EADDRINUSE'&&value>0&&!retried){retried=true;close(owned);open(0);}else finish(error);});server.once('listening',()=>{if(owned!==server)return;if(!current())return finish(Error('Cancelled'));finish(null,{server:owned,port:owned.address().port});});server.listen(value);}catch(e){finish(e);}};open(port);
 });}
module.exports=Object.assign(create(),{create,listen});
