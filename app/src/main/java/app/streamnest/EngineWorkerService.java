package app.streamnest;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Native sessions live here; the independent control looper stays responsive during JNI waits. */
public class EngineWorkerService extends Service {
 static final int START=1, STOP=2, PROMOTE=3, STATE=4, CLOSED=5, HELLO=6, ABORT=7, REPORT=8;
 final HandlerThread controlThread=new HandlerThread("torrent-worker-control");
 {controlThread.start();}
 final Handler control=new Handler(controlThread.getLooper());
 private final Object lifecycleLock = new Object();
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 long memoryAt;volatile long memoryMb;final java.util.concurrent.atomic.AtomicBoolean memoryBusy=new java.util.concurrent.atomic.AtomicBoolean();
 volatile Thread engineThread;int stackReports;final java.util.concurrent.atomic.AtomicBoolean stackBusy=new java.util.concurrent.atomic.AtomicBoolean();
 volatile String failureReport="";
 volatile TorrentEngine engine;Messenger owner;String token="";volatile boolean stopping;
 final Messenger endpoint=new Messenger(new Handler(controlThread.getLooper()){
  @Override public void handleMessage(Message m){
   Bundle d=m.getData();
   if(m.what==HELLO&&engine==null&&!stopping){owner=m.replyTo;token=d.getString("token","");Bundle hello=new Bundle();hello.putString("token",token);hello.putInt("pid",android.os.Process.myPid());send(HELLO,hello);return;}
   if(m.what==START&&engine==null&&!stopping&&token.equals(d.getString("token"))){
    synchronized(lifecycleLock){
     if(stopping)return;
     owner=m.replyTo;token=d.getString("token","");engine=new TorrentEngine();
    }
    engine.sourceUrl=d.getString("source","");engine.episodeIdentity=d.getString("identity");engine.expectedFilename=d.getString("filename","");engine.fileIndex=d.getInt("fileIndex",-1);
    engine.episodeRequest=d.getBoolean("episodeRequest");engine.requestedSeason=d.getInt("season");engine.requestedEpisode=d.getInt("episode");engine.absoluteEpisode=d.getInt("absolute");engine.publicMetadataLookup=d.getBoolean("metadata");engine.lite=d.getBoolean("lite");engine.preloadOnly=d.getBoolean("preload");engine.resumeFraction=d.getDouble("resumeFraction");
    engine.startup.begin("Creating torrent worker",10000);snapshot();
    worker.execute(()->{engineThread=Thread.currentThread();try{
     engine.startup.begin("Checking cache folder",20000);
     long probeAt=android.os.SystemClock.elapsedRealtime();
     File requested=new File(d.getString("root",""));File root=requested.getCanonicalFile(),base=CacheLocation.writable(requested.getParentFile());
     StorageGuard.check(EngineWorkerService.this,base,true);
     engine.storageCheck=()->StorageGuard.check(EngineWorkerService.this,base,false);
     Bundle storage=new Bundle();storage.putString("token",token);String kind=storageKind(base);
     storage.putString("report",new org.json.JSONObject().put("stage","cache_check").put("storage",kind).put("cacheFreeMb",base.getUsableSpace()/1048576).put("elapsedMs",android.os.SystemClock.elapsedRealtime()-probeAt).toString());send(REPORT,storage);
     if(!requested.getName().matches("[a-f0-9]{64}")||!base.equals(root.getParentFile()))throw new IOException("Invalid torrent worker folder");
     if("external".equals(kind)&&!engine.preloadOnly&&d.getBoolean("diagnostics")){
      engine.startup.enter("Checking USB random access",15000);
      try{UsbCacheProbe.run(base,EngineWorkerService.this::reportUsbProbe);}catch(IOException | SecurityException error){
       // Keep testing actual playback after a probe I/O error.
      }
      if(engine.closed)return;
     }
     if(engine.preloadOnly&&base.getUsableSpace()<512L*1024*1024)throw new IOException("Not enough free storage to preload");
     if(!engine.closed)engine.start(engine.sourceUrl,root);
    }catch(Throwable error){engine.storageFailure|=StorageGuard.isStorageFailure(error);failureReport=DiagnosticData.failure(engine.storageFailure?"storage":"torrent","startup",error).toString();engine.message="Unable to stream: "+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());engine.closed=true;engine.startup.done();}
    finally{snapshot();if(stopping||engine.closed)finishEngine();}});
   }else if(token.equals(d.getString("token"))){
    if(m.what==ABORT){android.os.Process.killProcess(android.os.Process.myPid());return;}
    if(m.what==REPORT&&stackReports<3&&engineThread!=null&&stackBusy.compareAndSet(false,true)){stackReports++;new Thread(()->{try{org.json.JSONArray frames=new org.json.JSONArray();for(StackTraceElement f:engineThread.getStackTrace())if(frames.length()<12&&f.getLineNumber()>0)frames.put(f.getClassName()+"."+f.getMethodName()+":"+f.getLineNumber());Bundle report=new Bundle();report.putString("token",token);report.putString("report",DiagnosticData.clean(new org.json.JSONObject().put("stage","worker").put("frames",frames).put("workerAgeMs",Math.max(0,(System.nanoTime()-engine.workerPulse)/1000000)).put("peers",engine.diagnosticPeers).put("downloadKbps",engine.diagnosticKbps).put("percent",engine.diagnosticPercent)).toString());send(REPORT,report);}catch(Exception ignored){}finally{stackBusy.set(false);}},"worker-stack").start();return;}

    if(m.what==STOP){stopping=true;if(engine!=null)engine.closed=true;else {Bundle done=new Bundle();done.putString("token",token);send(CLOSED,done);terminate();}}
    else if(m.what==PROMOTE&&engine!=null)engine.promote();
   }
  }
 });
 void reportUsbProbe(String phase,String state,long elapsedMs){
  try{
   Bundle report=new Bundle();report.putString("token",token);
   report.putString("report",new org.json.JSONObject().put("stage","cache_check").put("storage","external")
       .put("ioPhase",phase).put("ioState",state).put("elapsedMs",elapsedMs).toString());
   send(REPORT,report);
  }catch(org.json.JSONException ignored){}
 }
 String storageKind(File base){return CacheLocation.kind(this,base);}
 @Override public IBinder onBind(Intent i){return endpoint.getBinder();}
 void snapshot(){
  TorrentEngine e=engine;if(e==null)return;
  Bundle d=new Bundle();d.putString("token",token);d.putInt("pid",android.os.Process.myPid());
  d.putLong("workerMemoryMb",memoryMb);if(!failureReport.isEmpty())d.putString("failureReport",failureReport);
  d.putString("rangeReport",e.rangeSnapshot().toString());
  d.putString("message",e.storageFailure&&!e.storageMessage.isEmpty()?e.storageMessage:e.message);d.putString("url",e.url);d.putBoolean("closed",e.closed);d.putBoolean("complete",e.verifiedComplete);
  d.putBoolean("storageFailure",e.storageFailure);
  if(e.storageFailure&&failureReport.isEmpty())try{failureReport=new org.json.JSONObject().put("service","storage").put("operation","cache").put("reason","storage").put("nativeCode",Math.abs((long)e.diagnosticError)).put("cacheFreeMb",StorageGuard.cacheFreeMb).put("internalFreeMb",StorageGuard.internalFreeMb).toString();d.putString("failureReport",failureReport);}catch(Exception ignored){}
  d.putLong("internalFreeMb",StorageGuard.internalFreeMb);d.putLong("cacheFreeMb",StorageGuard.cacheFreeMb);
  d.putString("stage",e.startup.stage);d.putLong("since",e.startup.since);d.putLong("limit",e.startup.limitMs);d.putBoolean("active",e.startup.active);d.putLong("pulse",e.workerPulse);
  d.putString("video",e.video==null?"":e.video.getAbsolutePath());d.putLong("size",e.size);d.putLong("received",e.receivedBytes);d.putString("fingerprint",e.fingerprint());d.putString("hash",e.cachedMovieHash);
  d.putInt("peers",e.diagnosticPeers);d.putInt("kbps",e.diagnosticKbps);d.putInt("percent",e.diagnosticPercent);d.putInt("error",e.diagnosticError);
  send(STATE,d);
 }
 void send(int type,Bundle data){try{Message m=Message.obtain(null,type);m.setData(data);if(owner!=null)owner.send(m);}catch(RemoteException e){stopping=true;if(engine!=null)engine.closed=true;}}
 final Runnable ticker=new Runnable(){public void run(){if(android.os.SystemClock.elapsedRealtime()-memoryAt>30000&&memoryBusy.compareAndSet(false,true)){memoryAt=android.os.SystemClock.elapsedRealtime();new Thread(()->{try{memoryMb=android.os.Debug.getPss()/1024;}finally{memoryBusy.set(false);}},"worker-memory").start();}snapshot();control.postDelayed(this,engine==null?500:WorkerCadence.delay(engine.startup.active,engine.preloadOnly));}};
 @Override public void onCreate(){super.onCreate();control.post(ticker);}
 void finishEngine(){try{engine.close();}finally{Bundle d=new Bundle();d.putString("token",token);send(CLOSED,d);control.post(this::terminate);}}
 void terminate(){stopSelf();new Thread(()->{android.os.SystemClock.sleep(100);android.os.Process.killProcess(android.os.Process.myPid());},"worker-exit").start();}
 @Override public boolean onUnbind(Intent i){
  TorrentEngine active;
  synchronized(lifecycleLock){stopping=true;active=engine;}
  if(active==null){stopSelf();return false;}
  active.closed=true;
  // The Android lifecycle can still stop this process if its control looper stalls.
  new Handler(getMainLooper()).postDelayed(this::terminate,1500);
  return false;
 }
 @Override public void onDestroy(){control.removeCallbacks(ticker);if(engine!=null)engine.closed=true;worker.shutdown();controlThread.quitSafely();super.onDestroy();}
}
