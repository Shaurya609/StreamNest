const MAX=180000;
const json=(data,status=200)=>Response.json(data,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
async function room(request){
 const header=request.headers.get('Authorization')||'';
 if(!/^Bearer [A-Za-z0-9_-]{43}$/.test(header))return null;
 const bytes=new TextEncoder().encode(header.slice(7));
 return [...new Uint8Array(await crypto.subtle.digest('SHA-256',bytes))].map(b=>b.toString(16).padStart(2,'0')).join('');
}
export default {
 async fetch(request,env){
  try{
   const url=new URL(request.url);
   if(url.pathname==='/pair'&&request.method==='GET')return new Response(`<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Pair StreamNest</title><style>body{background:#161920;color:#fff;font:18px system-ui;max-width:480px;margin:12vh auto;padding:24px}a{display:inline-block;background:#247ac5;color:white;padding:14px;border-radius:8px}code{font-size:24px}</style><h1>Pair StreamNest</h1><p id="message">Checking pairing link…</p><p><code id="code"></code></p><a id="open" hidden>Open StreamNest</a><p>If StreamNest does not open, enter this code in Resume &amp; sync devices.</p><script>const value=decodeURIComponent(location.hash.slice(1));if(/^[A-HJ-NP-Z2-9]{8}$/.test(value.replace(/[ -]/g,''))){document.getElementById('code').textContent=value;const a=document.getElementById('open');a.href='streamnest-watch://pair/'+encodeURIComponent(value);a.hidden=false;document.getElementById('message').textContent='Open StreamNest and confirm the connection.';}else document.getElementById('message').textContent='Invalid or incomplete pairing link.';</script></html>`,{headers:{'Content-Type':'text/html; charset=utf-8','Cache-Control':'no-store','Referrer-Policy':'no-referrer','X-Content-Type-Options':'nosniff','Content-Security-Policy':"default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'"}});
   if(url.pathname==='/health'&&request.method==='GET')return json({service:'StreamNest private watch sync',schema:1});
   if(url.pathname.startsWith('/v1/pair/')){
    if(!env.RATE)return json({error:'Pairing unavailable'},503);
    const rate=await env.RATE.limit({key:'pair:'+request.headers.get('CF-Connecting-IP')});
    if(!rate.success)return json({error:'Try again shortly'},429);
    const id=url.pathname.slice('/v1/pair/'.length);
    if(!/^[a-f0-9]{64}$/.test(id))return json({error:'Invalid code'},400);
    const now=Date.now();
    if(request.method==='PUT'){
     if(!await room(request))return json({error:'Pairing required'},401);
     const payload=await request.text();
     if(payload.length>1000||!/^[A-Za-z0-9+/=]+$/.test(payload))return json({error:'Invalid ticket'},400);
     await env.DB.prepare('DELETE FROM pairing_tickets WHERE expires_at<?').bind(now).run();
     const count=await env.DB.prepare('SELECT COUNT(*) AS total FROM pairing_tickets').first();
     if(count.total>=2000)return json({error:'Pairing capacity reached'},503);
     const result=await env.DB.prepare('INSERT OR IGNORE INTO pairing_tickets(id,payload,expires_at) VALUES (?,?,?)').bind(id,payload,now+300000).run();
     return result.meta.changes===1?json({expiresAt:now+300000}):json({error:'Generate another code'},409);
    }
    if(request.method==='POST'){
     const ticket=await env.DB.prepare('DELETE FROM pairing_tickets WHERE id=? AND expires_at>? RETURNING payload').bind(id,now).first();
     return ticket?json({payload:ticket.payload}):json({error:'Code expired or already used'},404);
    }
    return json({error:'Method not allowed'},405);
   }
   if(url.pathname!=='/v1/progress')return json({error:'Not found'},404);
   if(!['GET','PUT','DELETE'].includes(request.method))return json({error:'Method not allowed'},405);
   const id=await room(request);if(!id)return json({error:'Pairing required'},401);
   if(env.RATE){const rate=await env.RATE.limit({key:request.headers.get('CF-Connecting-IP')||id});if(!rate.success)return json({error:'Try again shortly'},429);}
   if(request.method==='GET'){
    const row=await env.DB.prepare('SELECT revision,payload FROM rooms WHERE id=?').bind(id).first();
    return json(row?{revision:row.revision,payload:JSON.parse(row.payload)}:{revision:0,payload:null});
   }
   if(request.method==='DELETE'){await env.DB.prepare('DELETE FROM rooms WHERE id=?').bind(id).run();return json({deleted:true});}
   if(Number(request.headers.get('Content-Length'))>MAX)return json({error:'Progress too large'},413);
   const reader=request.body?.getReader();if(!reader)return json({error:'Missing payload'},400);
   let size=0;const chunks=[];for(;;){const r=await reader.read();if(r.done)break;size+=r.value.length;if(size>MAX){await reader.cancel();return json({error:'Progress too large'},413);}chunks.push(r.value);}
   const bytes=new Uint8Array(size);let offset=0;for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
   let body;try{body=JSON.parse(new TextDecoder().decode(bytes));}catch(e){return json({error:'Invalid payload'},400);}
   if(body.v!==1||!/^[A-Za-z0-9+/]{16}$/.test(body.iv||'')||typeof body.body!=='string'||body.body.length>170000||!/^[A-Za-z0-9+/]+={0,2}$/.test(body.body))return json({error:'Invalid encrypted payload'},400);
   const supplied=request.headers.get('If-Match');const revision=Number(supplied);if(!supplied||!/^\d+$/.test(supplied)||!Number.isSafeInteger(revision)||revision<0)return json({error:'Revision required'},400);
   const payload=JSON.stringify(body),now=Date.now();
   if(revision===0){const count=await env.DB.prepare('SELECT COUNT(*) AS total FROM rooms').first();if(count.total>=2000)return json({error:'Sync capacity reached'},503);}
   const result=revision===0
    ?await env.DB.prepare('INSERT OR IGNORE INTO rooms(id,revision,payload,updated_at) VALUES (?,1,?,?)').bind(id,payload,now).run()
    :await env.DB.prepare('UPDATE rooms SET revision=revision+1,payload=?,updated_at=? WHERE id=? AND revision=?').bind(payload,now,id,revision).run();
   if(result.meta.changes!==1)return json({error:'Progress changed; merge and retry'},409);
   return json({revision:revision+1});
  }catch(error){return json({error:'Sync temporarily unavailable'},503);}
 },
 async scheduled(event,env){await env.DB.prepare('DELETE FROM pairing_tickets WHERE expires_at<?').bind(Date.now()).run();await env.DB.prepare('DELETE FROM rooms WHERE updated_at<?').bind(Date.now()-180*86400000).run();}
};
