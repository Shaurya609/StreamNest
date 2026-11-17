const json=(value,status=200)=>Response.json(value,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});
export const authorized=(request,env)=>!!env.ADMIN_TOKEN&&env.ADMIN_TOKEN.length>=32&&request.headers.get('Authorization')==='Bearer '+env.ADMIN_TOKEN;
export const revoked=(env,id)=>env.DB.prepare('SELECT id FROM revoked_groups WHERE id=?').bind(id).first();
export const gone=()=>json({error:'This sync group was deleted by the owner. Create or join a new group.',code:'GROUP_DELETED'},410);
async function body(request,max){if(Number(request.headers.get('Content-Length'))>max)throw Error('size');const reader=request.body?.getReader();if(!reader)throw Error('body');let bytes=0,text='',decoder=new TextDecoder();for(;;){const r=await reader.read();if(r.done)break;bytes+=r.value.length;if(bytes>max){await reader.cancel();throw Error('size');}text+=decoder.decode(r.value,{stream:true});}return JSON.parse(text+decoder.decode());}
export async function deleteGroup(request,env){
 if(!authorized(request,env))return json({error:'Owner authorization required'},401);
 if(request.method!=='DELETE')return json({error:'Method not allowed'},405);
 const id=new URL(request.url).searchParams.get('group');if(!/^[a-f0-9]{64}$/.test(id||''))return json({error:'Invalid group'},400);
 const group=await env.DB.prepare('SELECT name FROM sync_groups WHERE id=?').bind(id).first();
 if(!group)return await revoked(env,id)?json({deleted:true}):json({error:'Group not found'},404);
 let input;try{input=await body(request,512);}catch(e){return json({error:'Type the group name to confirm deletion'},400);}
 if(input?.confirmName!==group.name)return json({error:'Group name confirmation does not match'},400);
 // D1 batch is a transaction: revocation and erasure must succeed together.
 await env.DB.batch([
  env.DB.prepare('INSERT OR IGNORE INTO revoked_groups(id,deleted_at) VALUES (?,?)').bind(id,Date.now()),
  ...['watch_activity','diagnostic_events','diagnostic_devices','sync_devices'].map(table=>env.DB.prepare('DELETE FROM '+table+' WHERE room_id=?').bind(id)),
  env.DB.prepare('DELETE FROM sync_groups WHERE id=?').bind(id),env.DB.prepare('DELETE FROM rooms WHERE id=?').bind(id)
 ]);
 return json({deleted:true,codesRevoked:true});
}
export function cleanActivity(raw){
 if(!raw||!['movies','shows','animes'].includes(raw.kind)||!['starting','buffering','playing','paused','stopped'].includes(raw.state))return null;
 if(typeof raw.title!=='string'||!raw.title.trim()||raw.title.length>180||/[\x00-\x1f\x7f]/.test(raw.title)||/(?:https?:\/\/|magnet:\?|file:\/\/|[A-Za-z]:\\)/i.test(raw.title))return null;
 const out={title:raw.title.trim(),kind:raw.kind,state:raw.state};
 if(raw.kind!=='movies'){for(const key of ['season','episode']){if(raw[key]!==undefined&&(!Number.isInteger(raw[key])||raw[key]<0||raw[key]>100000))return null;if(raw[key]!==undefined)out[key]=raw[key];}}
 return out;
}
export async function activity(request,env,id){
 if(!['PUT','DELETE'].includes(request.method))return json({error:'Method not allowed'},405);
 let input;try{input=await body(request,2048);}catch(e){return json({error:'Invalid activity report'},400);}
 if(!/^[a-f0-9-]{32,36}$/.test(input?.deviceId||''))return json({error:'Invalid device'},400);
 if(await revoked(env,id))return gone();
 if(!await env.DB.prepare('SELECT id FROM sync_devices WHERE room_id=? AND id=?').bind(id,input.deviceId).first())return json({error:'Register this device first'},403);
 if(request.method==='DELETE'){await env.DB.prepare('DELETE FROM watch_activity WHERE room_id=? AND device_id=?').bind(id,input.deviceId).run();return json({deleted:true});}
 const report=cleanActivity(input.activity);if(input.sharing!==true||!report)return json({error:'Activity sharing and a valid title/state are required'},400);
 if(input.ageMs!==undefined&&(!Number.isSafeInteger(input.ageMs)||input.ageMs<0||input.ageMs>=86400000))return json({error:'Invalid activity age'},400);
 const now=Date.now()-Number(input.ageMs||0);
 await env.DB.prepare('INSERT INTO watch_activity(room_id,device_id,payload,updated_at) SELECT ?,?,?,? WHERE NOT EXISTS (SELECT 1 FROM revoked_groups WHERE id=?) AND EXISTS (SELECT 1 FROM sync_devices WHERE room_id=? AND id=?) ON CONFLICT(room_id,device_id) DO UPDATE SET payload=excluded.payload,updated_at=excluded.updated_at WHERE excluded.updated_at>=watch_activity.updated_at').bind(id,input.deviceId,JSON.stringify(report),now,id,id,input.deviceId).run();
 if(await revoked(env,id))return gone();
 return json({accepted:true,updatedAt:now});
}
export async function pruneActivity(env){await env.DB.prepare('DELETE FROM watch_activity WHERE updated_at<? OR NOT EXISTS (SELECT 1 FROM sync_devices d WHERE d.room_id=watch_activity.room_id AND d.id=watch_activity.device_id)').bind(Date.now()-86400000).run();}
