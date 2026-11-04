const {test}=require('node:test'),assert=require('node:assert/strict'),vm=require('vm'),fs=require('fs'),path=require('path');
test('anime scroll pins unique pages across background refresh and refreshes on a new visit',async()=>{
 let rows=Array.from({length:80},(_,i)=>({imdb:'tt'+String(i).padStart(6,'0'),title:'Anime '+String(i).padStart(3,'0'),language:'ja',animeSeries:true})),saved=Date.now();
 const module={exports:{}};
 vm.runInNewContext(fs.readFileSync(path.resolve('desktop/overlay/butter-provider/streamnest-anime.js'),'utf8'),{module,__dirname:path.resolve('desktop/overlay/butter-provider'),Date,Map,Set,require:name=>name==='./streamnest-resilience'?{catalog:()=>({series:rows,saved}),notice:()=>{}}:name==='./streamnest-api'?{movie:o=>o}:name==='./streamnest-series'?require('../overlay/butter-provider/streamnest-series'):require(name)});
 const api=module.exports,ids=new Set();
 for(let page=1;page<=4;page++){
  const result=await api.list({page,sorter:'title'});
  for(const row of result.results){assert(!ids.has(row.imdb));ids.add(row.imdb);}
  assert.equal(result.hasMore,page<4);
  if(page===1){rows=[{imdb:'tt999999',title:'AAA new',animeSeries:true,language:'ja'},...rows];saved++;}
 }
 assert.equal(ids.size,80);assert(!ids.has('tt999999'));
 assert.equal((await api.list({page:1,sorter:'title'})).results[0].imdb,'tt999999');
});
