const assert=require('node:assert/strict');
const root=process.argv[2]||'C:/Users/shaur/AppData/Local/Popcorn-Time/src/app/butter-provider';
const P=require(root+'/streamnest-paged');
const m=(i,lang='en')=>({imdb_code:'tt'+i,title:'T'+i,language:lang,genres:['Animation'],torrents:[{quality:'1080p',type:'web',hash:'abc',seeds:i}]});
(async()=>{
 P.clear();const fetch=async(o,p)=>({movie_count:1000,movies:Array.from({length:50},(_,i)=>m((p-1)*50+i))});
 const ids=new Set();for(let p=1;p<=12;p++){const r=await P.list({page:p,sorter:'rating'},'all',false,fetch);assert.equal(r.rows.length,24);for(const x of r.rows){assert(!ids.has(x.imdb));ids.add(x.imdb);}}
 assert.equal(ids.size,288);assert.equal((await P.list({page:1,sorter:'rating'},'all',false,fetch)).rows[0].imdb,'tt0');
 P.clear();const anime=await P.list({page:1,sorter:'trending'},'all',true,async()=>({movies:[m(1),m(2,'jpn')]}));assert.equal(anime.rows.length,1);assert.equal(anime.rows[0].imdb,'tt2');
 P.clear();const trend=await P.list({page:1,sorter:'trending'},'all',false,async(o)=>{assert.equal(o.sort,'date_added');return {movies:[m(1),m(9)]};});assert.equal(trend.rows[0].imdb,'tt9');
 console.log('Pagination: 12 pages / 288 unique titles, revisits, anime filter, language aliases and recent-window seed ranking passed');
})().catch(e=>{console.error(e);process.exitCode=1;});
