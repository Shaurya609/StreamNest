 'use strict';
const known={34572:{slug:'black-clover',title:'Black Clover',max:170},21:{slug:'one-piece',title:'One Piece',max:5000}},cache=new Map(),pending=new Map();
function parse(html,mal){const show=known[mal];if(!show||typeof html!=='string'||html.length>2000000||/<!ENTITY/i.test(html))throw Error('Unsupported filler document');
 const canonical='https://www.animefillerlist.com/shows/'+show.slug;
 if(!new RegExp('<link\\b[^>]*rel=["\']canonical["\'][^>]*href=["\']'+canonical+'["\']','i').test(html))throw Error('Filler identity mismatch');
 const heading=(html.match(/<h1\b[^>]*>([\s\S]*?)<\/h1>/i)||[])[1]||'';if(!heading.replace(/<[^>]*>/g,'').includes(show.title))throw Error('Filler title mismatch');
 const rows=[];for(const match of html.matchAll(/<tr\b[^>]*>([\s\S]*?)<\/tr>/gi)){
  const cells=match[1],number=(cells.match(/<td\b[^>]*class=["']Number["'][^>]*>\s*(\d+)\s*<\/td>/i)||[])[1],type=(cells.match(/<td\b[^>]*class=["']Type["'][^>]*>([\s\S]*?)<\/td>/i)||[])[1];
  if(!number||!type)continue;const label=type.replace(/<[^>]*>/g,'').trim(),episode=Number(number);if(episode<1||episode>show.max||!['Filler','Manga Canon','Mixed Canon/Filler','Anime Canon'].includes(label))continue;
  rows.push({mal_id:episode,filler:label==='Filler'});if(rows.length>5000)throw Error('Too many filler episodes');
 }
 if(!rows.length||new Set(rows.map(r=>r.mal_id)).size!==rows.length)throw Error('Invalid filler episode table');return rows;
}
async function get(mal,page,text=(u,d)=>require('./streamnest-net').text(u,0,d)){
 const show=known[mal];if(!show)throw Error('No independently mapped filler provider');
 let saved=cache.get(mal);if(!saved||Date.now()-saved.at>86400000){let task=pending.get(mal);if(!task){task=(async()=>{const rows=parse(await text('https://www.animefillerlist.com/shows/'+show.slug,Date.now()+6000),mal),value={at:Date.now(),rows};cache.set(mal,value);return value;})();pending.set(mal,task);}try{saved=await task;}finally{pending.delete(mal);}}
 return saved.rows.filter(r=>r.mal_id>(page-1)*100&&r.mal_id<=page*100);
}
module.exports={parse,get};
