'use strict';
const {test}=require('node:test'),assert=require('node:assert/strict'),fs=require('fs'),os=require('os'),path=require('path');
const H=require('../overlay/butter-provider/streamnest-home');
test('saved Home uses current progress and removals without reading full catalogs',async()=>{
 const root=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-home-startup-'));const old=global.nw;global.nw={App:{dataPath:root}};
 try{
 const title={imdb:'tt11280740',title:'Severance',kind:'shows',homeEpisodeOrder:['1:1','1:2']};
 await H.savePreview([{name:'Continue Watching',cards:[{title,historyKey:'episode:tt11280740:1:1',progress:{positionMs:90000,durationMs:100000}}]}]);
 assert.ok(!fs.readFileSync(path.join(root,'streamnest-home-preview-v1.json'),'utf8').includes('positionMs'));
 const quick=H.quick();assert.equal(quick.length,1);
 const current={'episode:tt11280740:1:1':{positionMs:44000,durationMs:100000,updatedAt:2}};
 assert.equal(H.build(quick,current)[0].cards[0].progress.positionMs,44000);
 assert.equal(H.build(quick,{})[0].cards.length,0);
 fs.writeFileSync(path.join(root,'streamnest-home-library-v1.json'),JSON.stringify({tt11280740:{...title,title:'Updated title'}}));
 assert.equal(H.build(H.quick(),current)[0].cards[0].title.title,'Updated title');
 fs.writeFileSync(path.join(root,'streamnest-home-preview-v1.json'),'broken');assert.equal(H.quick().length,1);
 fs.writeFileSync(path.join(root,'streamnest-home-preview-v1.json'),' '.repeat(1000001));assert.equal(H.quick().length,1);
 }finally{global.nw=old;fs.rmSync(root,{recursive:true,force:true});}
});

test('series/anime bundles stay unloaded until a category needs them',()=>{
 const {Catalogs}=require('../overlay/butter-provider/streamnest-resilience');
 const r=new Catalogs({bundles:{movies:{saved:1,movies:[]}}});
 for(const kind of ['shows','animes','latest'])assert.equal(typeof Object.getOwnPropertyDescriptor(r.bundles,kind).get,'function');
 const doc=r.catalog('animes');assert.ok(Array.isArray(doc.series));
 assert.equal(Object.getOwnPropertyDescriptor(r.bundles,'animes').get,undefined);
 assert.equal(r.catalog('animes'),doc);
 assert.equal(typeof Object.getOwnPropertyDescriptor(r.bundles,'shows').get,'function');
});
