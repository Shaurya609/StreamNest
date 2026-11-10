'use strict';
const crypto=require('crypto'),https=require('https');
const alphabet='ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
function input(value){return String(value).normalize('NFKC').replace(/[\s\u200B-\u200D\uFEFF\u2010-\u2015\u2212-]/g,'');}
function normalize(value){const text=input(value).toUpperCase();if(!/^[A-HJ-NP-Z2-9]{8}$/.test(text))throw Error('Enter the eight-character code');return text;}
function create(){return [...crypto.randomBytes(8)].map(b=>alphabet[b&31]).join('');}
function digest(value){return crypto.createHash('sha256').update('StreamNest pairing v1:'+normalize(value)).digest();}
function lookup(value){return crypto.createHash('sha256').update('StreamNest pairing lookup v1:'+normalize(value)).digest('hex');}
function seal(short,recovery){const iv=crypto.randomBytes(12),c=crypto.createCipheriv('aes-256-gcm',digest(short),iv);return Buffer.concat([iv,c.update(recovery,'utf8'),c.final(),c.getAuthTag()]).toString('base64');}
function open(short,payload){const b=Buffer.from(payload,'base64');if(b.length<29||b.length>1000)throw Error('Invalid pairing ticket');const c=crypto.createDecipheriv('aes-256-gcm',digest(short),b.slice(0,12));c.setAuthTag(b.slice(-16));return Buffer.concat([c.update(b.slice(12,-16)),c.final()]).toString('utf8');}
function call(endpoint,short,method,payload,auth){return new Promise((resolve,reject)=>{
 const headers={'Content-Type':'text/plain'};if(auth)headers.Authorization='Bearer '+auth;
 const req=https.request(endpoint+'/v1/pair/'+lookup(short),{method,headers},res=>{
 let body='';res.on('data',b=>{body+=b;if(body.length>2000)req.destroy(Error('Invalid pairing response'));});res.on('end',()=>{try{const doc=JSON.parse(body);if(res.statusCode!==200)throw Error(doc.error||'Pairing unavailable');resolve(doc);}catch(e){reject(e);}});
 });req.setTimeout(10000,()=>req.destroy(Error('Pairing timed out')));req.on('error',reject);req.end(payload);
});}
module.exports={input,normalize,create,digest,lookup,seal,open,call};
