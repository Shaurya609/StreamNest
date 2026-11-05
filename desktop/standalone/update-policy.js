'use strict';
const crypto=require('crypto');
function version(v){if(!/^\d+\.\d+\.\d+$/.test(v))throw Error('Invalid version');return v.split('.').map(Number);}
function newer(a,b){a=version(a);b=version(b);for(let i=0;i<3;i++){if(a[i]!==b[i])return a[i]>b[i];}return false;}
function verify(bytes,key){
 if(bytes.length>100000)throw Error('Update manifest too large');
 const envelope=JSON.parse(bytes),payload=Buffer.from(envelope.payload,'base64');
 if(!crypto.verify('RSA-SHA256',payload,key,Buffer.from(envelope.signature,'base64')))throw Error('Update signature rejected');
 const doc=JSON.parse(payload);if(doc.schema!==1||doc.product!=='StreamNest'||!Number.isSafeInteger(doc.generatedAt)||!Number.isSafeInteger(doc.expiresAt)||doc.generatedAt<=0||doc.expiresAt<=Date.now()||doc.generatedAt>Date.now()+300000)throw Error('Invalid or expired update manifest');return doc;
}
function artifact(a){
 if(!a||!/^https:\/\/github\.com\/Shaurya609\/StreamNest\/releases\/download\/[A-Za-z0-9._-]+\/[A-Za-z0-9._-]+$/.test(a.url)||!/^([a-f0-9]{64})$/.test(a.sha256)||!Number.isSafeInteger(a.bytes)||a.bytes<1||a.bytes>700000000)throw Error('Invalid update artifact');return a;
}
module.exports={newer,verify,artifact};
