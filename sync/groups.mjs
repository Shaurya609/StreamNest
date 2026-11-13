import {eraseDevice} from './diagnostics.mjs';
// Group/device names are administrative metadata; watch history remains encrypted.
const json=(body,status=200)=>Response.json(body,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
export async function groups(request,env,id) {
 const now=Date.now();
 if(request.method==='GET') {
  const group=await env.DB.prepare('SELECT name,created_at FROM sync_groups WHERE id=?').bind(id).first();
  const rows=await env.DB.prepare('SELECT id,name,seen_at FROM sync_devices WHERE room_id=? AND seen_at>? ORDER BY seen_at DESC').bind(id,now-90*86400000).all();
  return json({name:group?.name||'Sync group',createdAt:group?.created_at||null,devices:rows.results});
 }
 if(!['PUT','DELETE'].includes(request.method))return json({error:'Method not allowed'},405);
 if(Number(request.headers.get('Content-Length'))>4096)return json({error:'Metadata too large'},413);
 const reader=request.body?.getReader();if(!reader)return json({error:'Missing metadata'},400);
 let text='',size=0;const decoder=new TextDecoder();
 for(;;){const part=await reader.read();if(part.done)break;size+=part.value.length;if(size>4096){await reader.cancel();return json({error:'Metadata too large'},413);}text+=decoder.decode(part.value,{stream:true});}
 let body;try{body=JSON.parse(text+decoder.decode());}catch(e){return json({error:'Invalid metadata'},400);}
 if(!/^[a-f0-9-]{32,36}$/.test(body.deviceId||''))return json({error:'Invalid device'},400);
 if(request.method==='DELETE') {await eraseDevice(env,id,body.deviceId);await env.DB.prepare('DELETE FROM sync_devices WHERE room_id=? AND id=?').bind(id,body.deviceId).run();return json({disconnected:true});}
 const valid=s=>typeof s==='string'&&s.trim().length>0&&s.trim().length<=64&&!/[\x00-\x1f\x7f]/.test(s);
 if(!valid(body.deviceName)||(body.groupName!==undefined&&!valid(body.groupName)))return json({error:'Use names of 1–64 characters'},400);
 const existing=await env.DB.prepare('SELECT name FROM sync_groups WHERE id=?').bind(id).first();
 if(!existing){const count=await env.DB.prepare('SELECT COUNT(*) AS total FROM sync_groups').first();if(count.total>=2000)return json({error:'Group capacity reached'},503);}
 const count=await env.DB.prepare('SELECT COUNT(*) AS total FROM sync_devices WHERE room_id=?').bind(id).first();
 const device=await env.DB.prepare('SELECT id FROM sync_devices WHERE room_id=? AND id=?').bind(id,body.deviceId).first();
 if(!device&&count.total>=32)return json({error:'Group device limit reached'},409);
 await env.DB.prepare('INSERT INTO sync_groups(id,name,created_at,updated_at) VALUES (?,?,?,?) ON CONFLICT(id) DO UPDATE SET updated_at=excluded.updated_at').bind(id,body.groupName?.trim()||'Sync group',now,now).run();
 if(body.groupName!==undefined)await env.DB.prepare('UPDATE sync_groups SET name=?,updated_at=? WHERE id=?').bind(body.groupName.trim(),now,id).run();
 await env.DB.prepare('INSERT INTO sync_devices(room_id,id,name,seen_at) VALUES (?,?,?,?) ON CONFLICT(room_id,id) DO UPDATE SET name=excluded.name,seen_at=excluded.seen_at').bind(id,body.deviceId,body.deviceName.trim(),now).run();
 return groups(new Request(request.url,{method:'GET'}),env,id);
}
export async function admin(request,env) {
 if(!env.ADMIN_TOKEN||env.ADMIN_TOKEN.length<32||request.headers.get('Authorization')!=='Bearer '+env.ADMIN_TOKEN)return json({error:'Owner authorization required'},401);
 const result=await env.DB.prepare('SELECT g.id AS group_id,g.name,g.created_at,g.updated_at,d.id AS device_id,d.name AS device_name,d.seen_at FROM sync_groups g LEFT JOIN sync_devices d ON d.room_id=g.id AND d.seen_at>? ORDER BY g.id,d.seen_at DESC').bind(Date.now()-90*86400000).all();
 // No recovery keys, authentication hashes or playback records are returned.
 return json({groups:result.results});
}
