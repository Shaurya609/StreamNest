 'use strict';
const https=require('https');
function call(endpoint,auth,method,data){return new Promise((resolve,reject)=>{
 const bytes=data?Buffer.from(JSON.stringify(data)):null,headers={Authorization:'Bearer '+auth,Accept:'application/json'};
 if(bytes){headers['Content-Type']='application/json';headers['Content-Length']=bytes.length;}
 const req=https.request(endpoint+'/v1/group',{method,headers},res=>{let size=0,chunks=[];res.on('data',b=>{size+=b.length;if(size>16000)req.destroy(Error('Group response too large'));else chunks.push(b);});res.on('error',reject);res.on('end',()=>{try{const doc=JSON.parse(Buffer.concat(chunks));if(res.statusCode!==200)throw Error(doc.error||'Group service unavailable');resolve(doc);}catch(e){reject(e);}});});
 req.on('error',reject);req.setTimeout(10000,()=>req.destroy(Error('Group service timed out')));req.end(bytes);
});}
module.exports={call};
