package app.streamnest;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Parent-side snapshot only. No JNI call or native session exists in the player process. */
class RemoteTorrentEngine extends TorrentEngine {
 static final RemoteTorrentEngine[] leases=new RemoteTorrentEngine[2];
 static final class Control {static final HandlerThread thread=new HandlerThread("torrent-control");static {thread.start();}}
 final Context context;final String token=UUID.randomUUID().toString();
 final CountDownLatch died=new CountDownLatch(1),drained=new CountDownLatch(1);
 volatile long workerMemoryMb;boolean failureReported;int reportCount;long lastReportAt;final String reportAttempt=Diagnostics.attemptId;
 volatile Messenger remote;volatile int pid,slot=-1;volatile boolean bound,attached,closeStarted,stoppedConfirmed;volatile long snapshotAt;
 final Messenger receiver=new Messenger(new Handler(Control.thread.getLooper()){
  @Override public void handleMessage(Message m){
   Bundle d=m.getData();if(!token.equals(d.getString("token")))return;
   if(m.what==EngineWorkerService.HELLO){synchronized(RemoteTorrentEngine.this){if(closeStarted)return;pid=d.getInt("pid");attached=true;snapshotAt=workerPulse=System.nanoTime();startup.begin("Starting torrent worker",10000);try{Message start=Message.obtain(null,EngineWorkerService.START);start.setData(config());start.replyTo=receiver;remote.send(start);}catch(RemoteException error){closed=true;message="Torrent worker could not start";}}return;}
   if(m.what==EngineWorkerService.CLOSED){drained.countDown();return;}
   if(m.what==EngineWorkerService.REPORT){try{Diagnostics.event("torrent.stage",new org.json.JSONObject(d.getString("report")).put("attemptId",reportAttempt));}catch(Exception ignored){}return;}
   if(m.what!=EngineWorkerService.STATE||closeStarted)return;
   if(!failureReported&&d.containsKey("failureReport")){failureReported=true;try{Diagnostics.event("torrent.failed",new org.json.JSONObject(d.getString("failureReport")));}catch(Exception ignored){}}
   rangeReport=d.getString("rangeReport","{}");
   workerMemoryMb=d.getLong("workerMemoryMb");pid=d.getInt("pid");attached=true;snapshotAt=System.nanoTime();
   String stage=d.getString("stage",startup.stage);if(!startup.stage.equals(stage)||startup.active&&!d.getBoolean("active"))Diagnostics.startup(startup.stage+" finished after "+startup.elapsed()+"ms; "+stage);
   startup.stage=stage;startup.since=d.getLong("since");startup.limitMs=d.getLong("limit");startup.active=d.getBoolean("active");
   workerPulse=d.getLong("pulse");message=d.getString("message",message);verifiedComplete=d.getBoolean("complete");
   String file=d.getString("video","");if(!file.isEmpty())video=new File(file);size=d.getLong("size");receivedBytes=d.getLong("received");torrentFingerprint=d.getString("fingerprint","");cachedMovieHash=d.getString("hash","");
   diagnosticPeers=d.getInt("peers");diagnosticKbps=d.getInt("kbps");diagnosticPercent=d.getInt("percent");diagnosticError=d.getInt("error");
   storageFailure=d.getBoolean("storageFailure");
   internalFreeMb=d.getLong("internalFreeMb");cacheFreeMb=d.getLong("cacheFreeMb");
   url=d.getString("url");if(d.getBoolean("closed")){if(!closed&&!closeStarted)Diagnostics.event("torrent.failed");closed=true;url=null;}
  }
 });
 final ServiceConnection connection=new ServiceConnection(){
  @Override public void onServiceConnected(ComponentName name,IBinder binder){
   if(closeStarted||closed){unbind();return;}
   try{binder.linkToDeath(()->{confirmDeath();if(!closeStarted&&!closed){closed=true;url=null;startup.done();message="Torrent worker stopped. Try another source.";Diagnostics.event("torrent.failed");}},0);
    remote=new Messenger(binder);Bundle d=new Bundle();d.putString("token",token);Message m=Message.obtain(null,EngineWorkerService.HELLO);m.setData(d);m.replyTo=receiver;remote.send(m);
   }catch(RemoteException error){closed=true;message="Torrent worker could not start. Try another source.";died.countDown();}
  }
  @Override public void onServiceDisconnected(ComponentName name){if(!closeStarted&&!closed){closed=true;url=null;startup.done();message="Torrent worker stopped. Try another source.";Diagnostics.event("torrent.failed");}}
  @Override public void onBindingDied(ComponentName name){onServiceDisconnected(name);}
  @Override public void onNullBinding(ComponentName name){onServiceDisconnected(name);}
 };
 RemoteTorrentEngine(Context c){context=c.getApplicationContext();}
 static final class CacheWriterBusyException extends IOException{CacheWriterBusyException(){super("Previous torrent writer still owns this cache");}}
 static final class WorkerSlotsBusyException extends IOException{WorkerSlotsBusyException(){super("Waiting for the previous torrent worker to stop");}}
 synchronized int reserve()throws IOException {synchronized(leases){for(RemoteTorrentEngine e:leases)if(e!=null&&e.root!=null&&e.root.toPath().toAbsolutePath().normalize().equals(root.toPath().toAbsolutePath().normalize()))throw new CacheWriterBusyException();for(int i=0;i<leases.length;i++)if(leases[i]==null){leases[i]=this;return slot=i;}}throw new WorkerSlotsBusyException();}
 static List<File> protectedRoots(){synchronized(leases){List<File> roots=new ArrayList<>();for(RemoteTorrentEngine e:leases)if(e!=null&&e.root!=null)roots.add(e.root);return roots;}}
 static boolean cleanupPending(){synchronized(leases){for(RemoteTorrentEngine e:leases)if(e!=null&&e.closeStarted&&!e.stoppedConfirmed)return true;return false;}}
 void confirmDeath(){died.countDown();stoppedConfirmed=true;synchronized(leases){if(slot>=0&&leases[slot]==this)leases[slot]=null;}unbind();}
 Intent service(){return new Intent(context,slot==0?EngineWorkerService.class:PreloadWorkerService.class);}
 Bundle config(){Bundle d=new Bundle();d.putString("token",token);d.putString("source",sourceUrl);d.putString("root",root.getAbsolutePath());d.putString("identity",episodeIdentity);d.putString("filename",expectedFilename);d.putInt("fileIndex",fileIndex);d.putBoolean("episodeRequest",episodeRequest);d.putInt("season",requestedSeason);d.putInt("episode",requestedEpisode);d.putInt("absolute",absoluteEpisode);d.putBoolean("metadata",publicMetadataLookup);d.putBoolean("lite",lite);d.putBoolean("preload",preloadOnly);d.putDouble("resumeFraction",resumeFraction);return d;}
 @Override void start(String source,File directory)throws Exception {
  sourceUrl=source;root=directory;if(closed)return;try{Diagnostics.event("torrent.request",new org.json.JSONObject().put("mediaId",DiagnosticSource.id(WatchSync.code(context),TorrentMetadata.hash(source))));}catch(Exception ignored){}reserve();startup.begin("Connecting torrent worker",10000);long begin=System.nanoTime();
  bound=context.bindService(service(),connection,Context.BIND_AUTO_CREATE);if(!bound)throw new IOException("Could not bind torrent worker");
  while(!closed){
   long now=System.nanoTime();if(reportCount<3&&attached&&now-workerPulse>10000000000L&&now-lastReportAt>10000000000L&&Diagnostics.enabled(context)){reportCount++;lastReportAt=now;send(EngineWorkerService.REPORT);}String failure=WorkerRecovery.failure(now,begin,snapshotAt,workerPulse,attached,startup.active,startup.since,startup.limitMs);
   if(!failure.isEmpty()){message=failure+". Recovering torrent worker; try another source.";try{Diagnostics.event("torrent.stalled",new org.json.JSONObject().put("reason","timeout").put("workerAgeMs",snapshotAt==0?0:Math.max(0,(now-snapshotAt)/1000000)).put("elapsedMs",Math.max(0,(now-begin)/1000000)));}catch(Exception ignored){}closed=true;url=null;startup.done();break;}
   Thread.sleep(100);
  }
 }
 @Override void promote(){super.promote();send(EngineWorkerService.PROMOTE);}
 void send(int type){Messenger target=remote;if(target==null)return;try{Message m=Message.obtain(null,type);Bundle d=new Bundle();d.putString("token",token);m.setData(d);target.send(m);}catch(RemoteException ignored){}}
 @Override String movieHash(){return cachedMovieHash;}
 void unbind(){if(bound){bound=false;try{context.unbindService(connection);}catch(IllegalArgumentException ignored){}}}
 boolean ownProcess(){synchronized(leases){if(slot<0||leases[slot]!=this)return false;}if(pid<=0||pid==android.os.Process.myPid()||slot<0)return false;List<ActivityManager.RunningAppProcessInfo> rows=null;try{rows=((ActivityManager)context.getSystemService(Context.ACTIVITY_SERVICE)).getRunningAppProcesses();}catch(SecurityException ignored){}String name=context.getPackageName()+(slot==0?":torrent_a":":torrent_b");if(rows!=null)for(ActivityManager.RunningAppProcessInfo row:rows)if(row.pid==pid&&row.uid==android.os.Process.myUid()&&name.equals(row.processName))return true;try{String command=new String(java.nio.file.Files.readAllBytes(new File("/proc/"+pid+"/cmdline").toPath()),java.nio.charset.StandardCharsets.UTF_8);String status=new String(java.nio.file.Files.readAllBytes(new File("/proc/"+pid+"/status").toPath()),java.nio.charset.StandardCharsets.UTF_8);return WorkerIdentity.matches(command,status,name,android.os.Process.myUid());}catch(Exception ignored){return false;}}
 @Override public synchronized void close(){
  if(stoppedConfirmed){unbind();return;}
  // Retrying cleanup must remain possible after a bounded timeout. Never release
  // a writer lease merely because an OEM process-list query omits its PID.
  closeStarted=true;closed=true;url=null;send(EngineWorkerService.STOP);
  long closingAt=android.os.SystemClock.elapsedRealtime();String mode="graceful";
  boolean safe=!attached&&pid==0;
  try{
   if(attached){
    if(died.getCount()>0)drained.await(1500,TimeUnit.MILLISECONDS);
    if(died.getCount()>0){mode="self_abort";send(EngineWorkerService.ABORT);}
    if(!died.await(1500,TimeUnit.MILLISECONDS)&&ownProcess()){mode="pid_fallback";android.os.Process.killProcess(pid);}
    safe=died.await(2000,TimeUnit.MILLISECONDS);
   }else{unbind();if(slot>=0)context.stopService(service());}
  }catch(InterruptedException e){Thread.currentThread().interrupt();safe=false;}finally{
   try{Diagnostics.event("torrent.stage",new org.json.JSONObject().put("attemptId",reportAttempt).put("stage","shutdown").put("workerStopMode",safe?mode:"unconfirmed").put("elapsedMs",android.os.SystemClock.elapsedRealtime()-closingAt));}catch(Exception ignored){}
   startup.done();if(safe){stoppedConfirmed=true;unbind();synchronized(leases){if(slot>=0&&leases[slot]==this)leases[slot]=null;}}
  }
  if(!safe)throw new IllegalStateException("Torrent worker has not stopped; its cache is protected");
 }
}
