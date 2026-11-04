import crypto from 'node:crypto';
export const camera=/(?:\bhdcam\b|\bcamrip\b|\btelesync\b|\bhdts\b|\btsrip\b|\bhdtc\b|\bhdcamrip\b)/i;
export const language=value=>({eng:'en',hin:'hi',jpn:'ja'}[value]||value);
export function movie(raw,verified=0){
 if(!/^tt\d+$/.test(raw.imdb_code||'')||!raw.title)return null;
 const lang=language(raw.language);if(!['en','hi','ja'].includes(lang))return null;
 const torrents=(raw.torrents||[]).filter(t=>['1080p','2160p'].includes(t.quality)&&['web','bluray'].includes(String(t.type).toLowerCase())&&!camera.test(t.file||t.filename||t.name||'')&&(/^[0-9a-f]{40}$/i.test(t.hash||'')||/^https:\/\//.test(t.url||''))).map(t=>{
  const out={};for(const key of ['quality','type','hash','url','seeds','peers','size_bytes','video_codec','file','filename','name'])if(t[key]!=null)out[key]=t[key];return out;
 });
 if(!torrents.length)return null;
 const out={imdb_code:raw.imdb_code,title:raw.title,language:lang,torrents,catalogVerifiedAt:verified||raw.catalogVerifiedAt||0};
 for(const key of ['year','rating','genres','runtime','date_uploaded_unix','large_cover_image','medium_cover_image','popularityRank'])if(raw[key]!=null)out[key]=raw[key];
 out.description_full=String(raw.description_full||raw.summary||'').slice(0,1200);return out;
}
export function mergeMovie(map,raw,verified){
 const value=movie(raw,verified);if(!value)return false;const old=map.get(value.imdb_code);
 if(old){const items=new Map();for(const t of [...value.torrents,...old.torrents]){const key=t.hash||t.url;if(!items.has(key+'|'+t.quality))items.set(key+'|'+t.quality,t);}value.torrents=[...items.values()];if(value.popularityRank==null&&old.popularityRank!=null)value.popularityRank=old.popularityRank;}
 map.set(value.imdb_code,value);return true;
}
export function series(raw){
 const id=raw.imdb||raw.imdb_id;if(!/^tt\d+$/.test(id||'')||!raw.title)return null;
 const out={imdb:id,title:raw.title,language:language(raw.language||'en')};
 for(const key of ['year','rating','popularity','genres','runtime','trendRank','latestRank','poster_big','regionalCatalog','animeSeries','metadataOnly','metadataSource','identitySource','verifiedEpisodes','verifiedHdEpisodes','verifiedQualities'])if(raw[key]!=null)out[key]=raw[key];
 out.description=String(raw.description||'').slice(0,1200);return out;
}
export function tvmaze(raw){
 const id=raw.externals?.imdb;if(!/^tt\d+$/.test(id||''))return null;
 const lang={English:'en',Hindi:'hi',Japanese:'ja'}[raw.language];if(!lang)return null;
 const anime=lang==='ja'&&raw.type==='Animation';if(lang==='ja'&&!anime)return null;
 return {imdb:id,title:raw.name,language:lang,year:String(raw.premiered||'').slice(0,4),rating:raw.rating?.average||0,genres:raw.genres||[],poster_big:raw.image?.original||raw.image?.medium||'',description:String(raw.summary||'').replace(/<[^>]*>/g,' ').slice(0,1200),metadataOnly:true,metadataSource:'TVmaze',trendRank:2147483647,latestRank:2147483647,...(anime?{animeSeries:true,verifiedEpisodes:0,verifiedHdEpisodes:0,verifiedQualities:[]}: {})};
}
export const digest=bytes=>crypto.createHash('sha256').update(bytes).digest('hex');
export function verifyEnvelope(bytes,key){
 const env=JSON.parse(bytes);const payload=Buffer.from(env.payload,'base64');
 if(!crypto.verify('RSA-SHA256',payload,key,Buffer.from(env.signature,'base64')))throw Error('Invalid catalog signature');
 const manifest=JSON.parse(payload);if(manifest.schema!==1)throw Error('Unsupported catalog schema');return manifest;
}
export function signEnvelope(manifest,key){const payload=Buffer.from(JSON.stringify(manifest));return JSON.stringify({payload:payload.toString('base64'),signature:crypto.sign('RSA-SHA256',payload,key).toString('base64')});}
