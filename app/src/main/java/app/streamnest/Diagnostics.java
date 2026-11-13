package app.streamnest;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Default-on, user-disableable bounded technical journal. No titles, media positions, paths, URLs or exception messages. */
final class Diagnostics {
  private static Context context;
  private static final ScheduledExecutorService worker=Executors.newSingleThreadScheduledExecutor();
  private static final java.util.concurrent.atomic.AtomicInteger queued=new java.util.concurrent.atomic.AtomicInteger();
  private static final ArrayList<JSONObject> events=new ArrayList<>();
  private static final String session=UUID.randomUUID().toString();
  private static final Set<String> types=new HashSet<>(Arrays.asList(("app.start app.close app.unclean_exit app.crash app.error torrent.request torrent.stage torrent.stalled torrent.failed player.open player.video_ready player.audio_only player.buffering player.resumed player.paused player.ended player.closed player.error source.changed cache.changed cache.failed catalog.failed subtitle.failed sync.failed update.failed diagnostics.enabled").split(" ")));
  private static long lastUpload;
  private static volatile String status="Sharing is off";
  static synchronized void install(Context c) {
    if(context!=null)return;context=c.getApplicationContext();
    submit(()->{prepareScope();load();if(enabled(context)){event("app.start");if(Build.VERSION.SDK_INT>=30)try{ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);java.util.List<ApplicationExitInfo> reasons=am.getHistoricalProcessExitReasons(context.getPackageName(),0,1);if(!reasons.isEmpty()){ApplicationExitInfo r=reasons.get(0);long seen=prefs(context).getLong("exitAt",0);if(r.getTimestamp()>seen){prefs(context).edit().putLong("exitAt",r.getTimestamp()).apply();if(r.getReason()!=ApplicationExitInfo.REASON_EXIT_SELF&&r.getReason()!=ApplicationExitInfo.REASON_USER_REQUESTED)event("app.unclean_exit","exitReason",r.getReason());}}}catch(Exception ignored){}upload();}});
    worker.scheduleWithFixedDelay(()->{try{upload();}catch(Exception ignored){}},60,60,TimeUnit.SECONDS);
  }
  private static android.content.SharedPreferences prefs(Context c){return c.getSharedPreferences("diagnostics",0);}
  private static String code(Context c){try{return WatchSync.code(c);}catch(Exception e){return "";}}
  private static String scope(Context c){try{String code=code(c);if(code.isEmpty())return "";byte[] b=MessageDigest.getInstance("SHA-256").digest(code.getBytes(StandardCharsets.UTF_8));StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(java.util.Locale.ROOT,"%02x",v));return s.toString();}catch(Exception e){return "";}}
  static boolean wanted(Context c){return prefs(c).getBoolean("enabled",true);}
  private static void prepareScope(){String current=scope(context);if(!current.equals(prefs(context).getString("scope",""))){events.clear();file().delete();prefs(context).edit().putString("scope",current).apply();}}
  static void connected(Context c){install(c);worker.execute(()->{prepareScope();if(enabled(c)){event("app.start");requestUpload();}});}
  static boolean enabled(Context c){return wanted(c)&&!scope(c).isEmpty()&&scope(c).equals(prefs(c).getString("scope",""));}
  static String status(){return status;}
  static void requestUpload(){submit(()->upload());}
  private static File file(){return new File(context.getFilesDir(),"diagnostics.json");}
  private static void submit(Runnable task){if(queued.incrementAndGet()>64){queued.decrementAndGet();return;}worker.execute(()->{try{task.run();}catch(Exception ignored){}finally{queued.decrementAndGet();}});}
  static void event(String type){event(type,null,0);}
  static void event(String type,String key,long number){Context c=context;if(c==null||!enabled(c)||!types.contains(type))return;final String scoped=scope(c);submit(()->{if(!enabled(c)||!scoped.equals(scope(c)))return;JSONObject data=new JSONObject();try{if(key!=null&&Arrays.asList("elapsedMs","freeRamMb","processMemoryMb","width","height","exitReason","status","attempt").contains(key)&&number>=0)data.put(key,number);JSONObject e=new JSONObject().put("id",UUID.randomUUID().toString()).put("session",session).put("at",System.currentTimeMillis()).put("type",type).put("level",type.endsWith("failed")||type.endsWith("error")||type.equals("torrent.stalled")?"error":"info").put("data",data);events.add(e);while(events.size()>250)events.remove(0);save();}catch(Exception ignored){}});}
  static void startup(String message){
    Context c=context;if(c==null||!enabled(c))return;
    boolean stalled=message.startsWith("Startup stalled");
    message=message.startsWith("Startup stalled during ")?message.substring(23):message;
    final String reportMessage=message;
    String type=stalled?"torrent.stalled":"torrent.stage";
    String completed=message.split(" finished after ",2)[0];
    String stage="unknown";
    if(completed.contains("Starting torrent engine"))stage="engine_start";
    else if(completed.contains("Creating torrent session"))stage="session_create";
    else if(completed.contains("Preparing torrent folder"))stage="folder";
    else if(completed.contains("Stopping previous"))stage="shutdown";
    else if(completed.contains("Checking cache"))stage="cache_check";
    else if(completed.contains("Preparing episode"))stage="cache_prepare";
    else if(completed.contains("Cleaning"))stage="cleanup";
    else if(completed.contains("Waiting for engine"))stage="worker";
    final String safeStage=stage,scoped=scope(c);
    submit(()->{if(!enabled(c)||!scoped.equals(scope(c)))return;try{JSONObject data=new JSONObject().put("stage",safeStage);java.util.regex.Matcher m=java.util.regex.Pattern.compile("after ([0-9]{1,12})ms").matcher(reportMessage);if(m.find())data.put("elapsedMs",Long.parseLong(m.group(1)));events.add(new JSONObject().put("id",UUID.randomUUID().toString()).put("session",session).put("at",System.currentTimeMillis()).put("type",type).put("level",type.equals("torrent.stalled")?"error":"info").put("data",data));while(events.size()>250)events.remove(0);save();}catch(Exception ignored){}});
    int next=message.indexOf("; ");if(next>=0)startup(message.substring(next+2));
  }
  static void setEnabled(Context c,boolean on){install(c);String previous=code(c),id=SyncGroup.deviceId(c),currentScope=scope(c);prefs(c).edit().putBoolean("enabled",on).putString("scope",currentScope).apply();worker.execute(()->{events.clear();file().delete();if(on&&enabled(c)){status="Sharing enabled";event("diagnostics.enabled");event("app.start");}else{status="Sharing is off. Offline server reports expire within seven days.";if(!previous.isEmpty())try{call(previous,"DELETE",new JSONObject().put("deviceId",id));}catch(Exception ignored){}}});}
  static void reset(Context c){install(c);String previous=code(c),id=SyncGroup.deviceId(c);prefs(c).edit().putString("scope","").apply();worker.execute(()->{events.clear();file().delete();if(!previous.isEmpty())try{call(previous,"DELETE",new JSONObject().put("deviceId",id));}catch(Exception ignored){}});}
  private static void load(){if(!enabled(context)){file().delete();return;}try{if(file().length()>128000){file().delete();return;}try(InputStream in=new FileInputStream(file())){JSONObject doc=new JSONObject(new String(Net.read(in,128000),StandardCharsets.UTF_8));if(!scope(context).equals(doc.optString("scope")))return;JSONArray a=doc.optJSONArray("events");if(a!=null)for(int i=Math.max(0,a.length()-250);i<a.length();i++){JSONObject e=a.getJSONObject(i);if(types.contains(e.optString("type")))events.add(e);}}}catch(Exception ignored){}}
  private static void save(){try{if(!enabled(context))return;JSONArray a=new JSONArray();for(JSONObject e:events)a.put(e);byte[] bytes=new JSONObject().put("scope",scope(context)).put("events",a).toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>128000)return;android.util.AtomicFile f=new android.util.AtomicFile(file());FileOutputStream out=f.startWrite();try{out.write(bytes);f.finishWrite(out);}catch(Exception e){f.failWrite(out);}}catch(Exception ignored){}}
  private static JSONObject config()throws Exception{ActivityManager am=(ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE);ActivityManager.MemoryInfo m=new ActivityManager.MemoryInfo();am.getMemoryInfo(m);return new JSONObject().put("platform","Android").put("os",Build.VERSION.RELEASE).put("sdk",Build.VERSION.SDK_INT).put("model",Build.MANUFACTURER+" "+Build.MODEL).put("cpu",Build.VERSION.SDK_INT>=31?Build.SOC_MODEL:Build.HARDWARE).put("arch",Build.SUPPORTED_ABIS.length>0?Build.SUPPORTED_ABIS[0]:"Unknown").put("cpuCount",Runtime.getRuntime().availableProcessors()).put("version",context.getPackageManager().getPackageInfo(context.getPackageName(),0).versionName).put("totalRamMb",m.totalMem/1048576).put("freeRamMb",m.availMem/1048576).put("processMemoryMb",Debug.getPss()/1024).put("lowMemory",DeviceProfile.lite(context)).put("gpu","Unavailable").put("graphics",am.getDeviceConfigurationInfo().getGlEsVersion());}
  private static JSONObject call(String code,String method,JSONObject body)throws Exception{HttpURLConnection c=(HttpURLConnection)new URL(WatchSync.endpoint(context)+"/v1/diagnostics").openConnection();c.setConnectTimeout(8000);c.setReadTimeout(10000);c.setInstanceFollowRedirects(false);c.setRequestMethod(method);c.setRequestProperty("Authorization","Bearer "+WatchSyncCrypto.auth(code));c.setRequestProperty("Content-Type","application/json");c.setDoOutput(true);byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);c.setFixedLengthStreamingMode(bytes.length);try{try(OutputStream out=c.getOutputStream()){out.write(bytes);}if(c.getResponseCode()!=200)throw new IOException("Diagnostics unavailable");try(InputStream in=c.getInputStream()){return new JSONObject(new String(Net.read(in,16000),StandardCharsets.UTF_8));}}finally{c.disconnect();}}
  private static void upload(){if(!enabled(context)||events.isEmpty()||System.currentTimeMillis()-lastUpload<60000)return;lastUpload=System.currentTimeMillis();String scoped=scope(context),code=code(context);try{SyncGroup.update(context,null);if(!enabled(context)||!scoped.equals(scope(context)))return;JSONArray batch=new JSONArray();for(int i=0;i<Math.min(25,events.size());i++)batch.put(events.get(i));JSONObject result=call(code,"POST",new JSONObject().put("schema",1).put("consent",true).put("deviceId",SyncGroup.deviceId(context)).put("config",config()).put("events",batch));if(!enabled(context)||!scoped.equals(scope(context)))return;JSONArray accepted=result.getJSONArray("accepted");Set<String> ids=new HashSet<>();for(int i=0;i<accepted.length();i++)ids.add(accepted.getString(i));events.removeIf(e->ids.contains(e.optString("id")));status="Reports shared successfully";save();}catch(Exception e){status="Reports saved locally; will retry automatically";}}
}
