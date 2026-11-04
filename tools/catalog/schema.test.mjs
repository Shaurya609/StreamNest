import {test} from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import crypto from 'node:crypto';
import {movie,mergeMovie,tvmaze,verifyEnvelope,signEnvelope,digest} from './schema.mjs';
const raw={imdb_code:'tt123',title:'Hindi release',language:'hin',torrents:[{quality:'1080p',type:'web',hash:'a'.repeat(40),seeds:10}]};
test('movie gate rejects cameras, SD and metadata without real source identity',()=>{
 assert.equal(movie(raw,1).language,'hi');
 assert.equal(movie({...raw,torrents:[{...raw.torrents[0],quality:'720p'}]},1),null);
 assert.equal(movie({...raw,torrents:[{...raw.torrents[0],filename:'Release HDCAM'}]},1),null);
 assert.equal(movie({...raw,torrents:[{...raw.torrents[0],hash:'not-a-hash'}]},1),null);
});
test('partial updates retain known HD records and merge exact IMDb editions',()=>{
 const rows=new Map();mergeMovie(rows,raw,1);mergeMovie(rows,{...raw,torrents:[{quality:'2160p',type:'bluray',hash:'b'.repeat(40)}]},2);
 assert.equal(rows.size,1);assert.equal(rows.get('tt123').torrents.length,2);assert.equal(mergeMovie(rows,{...raw,torrents:[]},3),false);assert.equal(rows.size,1);
});
test('TVmaze adapter requires explicit IMDb identity and does not invent HD availability',()=>{
 assert.equal(tvmaze({name:'Unknown',language:'Hindi'}),null);
 const row=tvmaze({name:'Series',language:'Hindi',externals:{imdb:'tt123'}});assert.equal(row.language,'hi');assert.equal(row.metadataOnly,true);assert(!row.torrents);
 const anime=tvmaze({name:'Anime',type:'Animation',language:'Japanese',externals:{imdb:'tt124'}});assert.deepEqual(anime.verifiedQualities,[]);
});
test('catalog manifest signature rejects modified contents',()=>{
 const keys=crypto.generateKeyPairSync('rsa',{modulusLength:2048});const signed=signEnvelope({schema:1,version:'one'},keys.privateKey);assert.equal(verifyEnvelope(signed,keys.publicKey).version,'one');const changed=JSON.parse(signed);changed.payload=Buffer.from('{"schema":1,"version":"forged"}').toString('base64');assert.throws(()=>verifyEnvelope(JSON.stringify(changed),keys.publicKey));
});
test('bundled snapshots match the pinned public key and signed checksums',()=>{
 const manifest=verifyEnvelope(fs.readFileSync('catalogs/bundled-manifest.json'),fs.readFileSync('catalogs/public-key.pem'));
 for(const [kind,file]of Object.entries(manifest.files)){const bytes=fs.readFileSync('app/src/main/assets/catalog-'+kind+'-v1.json');assert.equal(bytes.length,file.bytes);assert.equal(digest(bytes),file.sha256);const doc=JSON.parse(bytes);assert.equal((doc.movies||doc.series||doc.releases).length,file.count);}
});
