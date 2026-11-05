const {test}=require('node:test'),assert=require('node:assert/strict'),crypto=require('crypto');
const P=require('../standalone/update-policy');
const keys=crypto.generateKeyPairSync('rsa',{modulusLength:2048});
function envelope(doc){const payload=Buffer.from(JSON.stringify(doc));return Buffer.from(JSON.stringify({payload:payload.toString('base64'),signature:crypto.sign('RSA-SHA256',payload,keys.privateKey).toString('base64')}));}
test('update manifest rejects altered data, wrong key and expired metadata',()=>{
 const doc={schema:1,product:'StreamNest',generatedAt:Date.now(),expiresAt:Date.now()+60000};
 assert.equal(P.verify(envelope(doc),keys.publicKey).product,'StreamNest');
 const bad=JSON.parse(envelope(doc));bad.payload=Buffer.from(JSON.stringify({...doc,product:'Other'})).toString('base64');assert.throws(()=>P.verify(Buffer.from(JSON.stringify(bad)),keys.publicKey),/signature/);
 assert.throws(()=>P.verify(envelope({...doc,expiresAt:1}),keys.publicKey),/expired/);
 assert.throws(()=>P.verify(envelope(doc),crypto.generateKeyPairSync('rsa',{modulusLength:2048}).publicKey),/signature/);
});
test('platform version comparison does not confuse desktop patch and Android versions',()=>{
 assert(P.newer('0.12.0','0.11.1'));assert(!P.newer('0.11.1','0.12.0'));assert(!P.newer('0.12.0','0.12.0'));assert(P.newer('0.12.10','0.12.9'));assert.throws(()=>P.newer('next','0.12.0'));
});
test('installer metadata rejects foreign repositories, invalid checksums and unreasonable sizes',()=>{
 const a={url:'https://github.com/Shaurya609/StreamNest/releases/download/desktop-v0.12.0/StreamNest.exe',sha256:'a'.repeat(64),bytes:100};assert.equal(P.artifact(a),a);
 for(const bad of [{...a,url:a.url.replace('Shaurya609','other')},{...a,url:'http://example.com/file.exe'},{...a,sha256:'bad'},{...a,bytes:900000000}])assert.throws(()=>P.artifact(bad));
});
