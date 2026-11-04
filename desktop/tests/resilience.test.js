const {test}=require('node:test'),assert=require('node:assert/strict');
const fs=require('fs'),os=require('os'),path=require('path'),crypto=require('crypto');
const {Catalogs,verify,check}=require('../overlay/butter-provider/streamnest-resilience');
const m=i=>({imdb_code:'tt'+i,title:'Title '+i,language:i%3?'en':'hi',year:2000+i%20,rating:i%10,date_uploaded_unix:10000-i,genres:['Drama'],torrents:[{quality:i%2?'1080p':'2160p',type:'web',hash:String(i).padStart(40,'0'),seeds:i}]});
function fixture(fetch,rows=Array.from({length:180},(_,i)=>m(i+1))){const dir=fs.mkdtempSync(path.join(os.tmpdir(),'streamnest-catalog-test-'));return {r:new Catalogs({root:dir,fetch,bundles:{movies:{saved:1,movies:rows}}}),dir};}
function cleanup(dir){assert(path.resolve(dir).startsWith(path.resolve(os.tmpdir())+path.sep+'streamnest-catalog-test-'));fs.rmSync(dir,{recursive:true,force:true});}
test('all endpoint outages preserve searchable data and skip cooling hosts',async()=>{
 let calls=0;const {r,dir}=fixture(async()=>{calls++;throw Error('HTTP 521');});try{
 const one=await r.movieData('list_movies.json?limit=50&page=1&sort_by=year&order_by=desc',{token:'one'});assert.equal(one.movies.length,50);assert.equal(one.catalog_saved,true);assert.equal(calls,3);
 await r.movieData('list_movies.json?query_term=Title%2012');assert.equal(calls,3);assert(r.saved);assert((await r.movieData('list_movies.json?query_term=Missing')).movies.length===0);
 }finally{cleanup(dir);}
});
test('saved fallback paging is pinned when new records arrive',async()=>{
 let online=true;const {r,dir}=fixture(async()=>{if(!online)throw Error('offline');return {status:'ok',data:{movie_count:180,movies:Array.from({length:50},(_,i)=>m(i+1))}};});try{
 const prefix='list_movies.json?limit=50&sort_by=date_added&order_by=desc&page=';const a=await r.movieData(prefix+1,{token:'feed'});online=false;const b=await r.movieData(prefix+2,{token:'feed'});r.remember({...m(999),date_uploaded_unix:999999});const c=await r.movieData(prefix+3,{token:'feed'});
 const ids=[...a.movies,...b.movies,...c.movies].map(x=>x.imdb_code);assert.equal(ids.length,150);assert.equal(new Set(ids).size,150);assert(!ids.includes('tt999'));
 }finally{cleanup(dir);}
});
test('invalid 200 response cannot replace a known catalog and empty searches remain legitimate',async()=>{
 let invalid=false;const {r,dir}=fixture(async()=>invalid?{error:'maintenance'}:{status:'ok',data:{movie_count:0,movies:[]}});try{
 assert.equal((await r.movieData('list_movies.json?query_term=NoMatch')).movies.length,0);invalid=true;const saved=await r.movieData('list_movies.json?limit=24');assert(saved.movies.length>0);assert.equal(saved.catalog_saved,true);
 }finally{cleanup(dir);}
});
test('independent series lookup requires exact IMDb identity and keeps Hindi language',async()=>{
 const {r,dir}=fixture(async()=>[{show:{name:'Hindi title',language:'Hindi',externals:{imdb:'tt999'}}},{show:{name:'No identity'}}]);try{const rows=await r.searchSeries('Hindi');assert.equal(rows.length,1);assert.equal(rows[0].imdb,'tt999');assert.equal(rows[0].language,'hi');assert.equal(rows[0].metadataOnly,true);}finally{cleanup(dir);}
});
test('metadata cache survives outages without accepting wrong schemas',async()=>{
 let down=false;const {r,dir}=fixture(async()=>down?{error:'maintenance'}:{MovieList:[{imdb:'tt1'}]});try{const url='https://fixture.example/shows',valid=x=>x&&Array.isArray(x.MovieList);await r.metadata(url,valid);down=true;assert.equal((await r.metadata(url,valid)).MovieList.length,1);assert(r.saved);}finally{cleanup(dir);}
});
test('published bundles verify on the desktop reader and altered files are rejected',()=>{
 const root=path.resolve(__dirname,'../..'),manifest=verify(fs.readFileSync(path.join(root,'catalogs/bundled-manifest.json')),fs.readFileSync(path.join(root,'catalogs/public-key.pem')));
 for(const [kind,entry]of Object.entries(manifest.files)){const bytes=fs.readFileSync(path.join(__dirname,'../overlay/butter-provider/catalog-'+kind+'-v1.json'));assert(check(kind,entry,bytes));assert.throws(()=>check(kind,entry,Buffer.from('tampered')));}
});

test('empty cold-start refresh retains packaged titles and saved rows survive reopening',async()=>{
 const fetch=async()=>({status:'ok',data:{movie_count:0,movies:[]}});const {r,dir}=fixture(fetch);try{
  const result=await r.movieData('list_movies.json?limit=24');assert.equal(result.catalog_saved,true);assert.equal(result.movies.length,24);
  r.remember(m(987));r.persist();const reopened=new Catalogs({root:dir,fetch:async()=>{throw Error('offline');},bundles:{movies:{saved:1,movies:[]}}});
  assert.equal((await reopened.movieData('list_movies.json?query_term=tt987')).movies[0].imdb_code,'tt987');
 }finally{cleanup(dir);}
});
