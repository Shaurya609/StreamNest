package app.streamnest;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Native sessions live here; the independent control looper stays responsive during JNI waits. */
public class EngineWorkerService extends Service {
 static final int START=1, STOP=2, PROMOTE=3, STATE=4, CLOSED=5, HELLO=6;
 final Handler control=new Handler(Looper.getMainLooper());
 final ExecutorService worker=Executors.newSingleThreadExecutor();
 long memoryAt;volatile long memoryMb;final java.util.concurrent.atomic.AtomicBoolean memoryBusy=new java.util.concurrent.atomic.AtomicBoolean();
 volatile String failureReport="";
 TorrentEngine engine;Messenger owner;String token="";boolean stopping;
 final Messenger endpoint=new Messenger(new Handler(Looper.getMainLooper()){
  @Override public void handleMessage(Message m){
   Bundle d=m.getData();
   if(m.what==HELLO&&engine==null&&!stopping){owner=m.replyTo;token=d.getString("token","");Bundle hello=new Bundle();hello.putString("token",token);hello.putInt("pid",android.os.Process.myPid());send(HELLO,hello);return;}
   if(m.what==START&&engine==null&&!stopping&&token.equals(d.getString("token"))){
    owner=m.replyTo;token=d.getString("token","");engine=new TorrentEngine();
    engine.sourceUrl=d.getString("source","");engine.episodeIdentity=d.getString("identity");engine.expectedFilename=d.getString("filename","");engine.fileIndex=d.getInt("fileIndex",-1);
    engine.episodeRequest=d.getBoolean("episodeRequest");engine.requestedSeason=d.getInt("season");engine.requestedEpisode=d.getInt("episode");engine.absoluteEpisode=d.getInt("absolute");engine.publicMetadataLookup=d.getBoolean("metadata");engine.lite=d.getBoolean("lite");engine.preloadOnly=d.getBoolean("preload");engine.resumeFraction=d.getDouble("resumeFraction");
    engine.startup.begin("Creating torrent worker",10000);snapshot();
    worker.execute(()->{try{
     engine.startup.begin("Checking cache folder",20000);
     File requested=new File(d.getString("root",""));File root=requested.getCanonicalFile(),base=CacheLocation.writable(requested.getParentFile());
     if(!requested.getName().matches("[a-f0-9]{64}")||!base.equals(root.getParentFile()))throw new IOException("Invalid torrent worker folder");
     if(engine.preloadOnly&&base.getUsableSpace()<512L*1024*1024)throw new IOException("Not enough free storage to preload");
     if(!engine.closed)engine.start(engine.sourceUrl,root);
    }catch(Throwable error){failureReport=DiagnosticData.failure("torrent","startup",error).toString();engine.message="Unable to stream: "+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage());engine.closed=true;engine.startup.done();}
    finally{snapshot();if(stopping||engine.closed)finishEngine();}});
   }else if(token.equals(d.getString("token"))){
    if(m.what==STOP){stopping=true;if(engine!=null)engine.closed=true;else {Bundle done=new Bundle();done.putString("token",token);send(CLOSED,done);terminate();}}
    else if(m.what==PROMOTE&&engine!=null)engine.promote();
   }
  }
 });
 @Override public IBinder onBind(Intent i){return endpoint.getBinder();}
 void snapshot(){
  TorrentEngine e=engine;if(e==null)return;
  Bundle d=new Bundle();d.putString("token",token);d.putInt("pid",android.os.Process.myPid());
  d.putLong("workerMemoryMb",memoryMb);if(!failureReport.isEmpty())d.putString("failureReport",failureReport);
  d.putString("message",e.message);d.putString("url",e.url);d.putBoolean("closed",e.closed);d.putBoolean("complete",e.verifiedComplete);
  d.putString("stage",e.startup.stage);d.putLong("since",e.startup.since);d.putLong("limit",e.startup.limitMs);d.putBoolean("active",e.startup.active);d.putLong("pulse",e.workerPulse);
  d.putString("video",e.video==null?"":e.video.getAbsolutePath());d.putLong("size",e.size);d.putLong("received",e.receivedBytes);d.putString("fingerprint",e.fingerprint());d.putString("hash",e.cachedMovieHash);
  d.putInt("peers",e.diagnosticPeers);d.putInt("kbps",e.diagnosticKbps);d.putInt("percent",e.diagnosticPercent);d.putInt("error",e.diagnosticError);
  send(STATE,d);
 }
 void send(int type,Bundle data){try{Message m=Message.obtain(null,type);m.setData(data);if(owner!=null)owner.send(m);}catch(RemoteException e){stopping=true;if(engine!=null)engine.closed=true;}}
 final Runnable ticker=new Runnable(){public void run(){if(android.os.SystemClock.elapsedRealtime()-memoryAt>30000&&memoryBusy.compareAndSet(false,true)){memoryAt=android.os.SystemClock.elapsedRealtime();new Thread(()->{try{memoryMb=android.os.Debug.getPss()/1024;}finally{memoryBusy.set(false);}},"worker-memory").start();}snapshot();control.postDelayed(this,engine==null?500:WorkerCadence.delay(engine.startup.active,engine.preloadOnly));}};
 @Override public void onCreate(){super.onCreate();control.post(ticker);}
 void finishEngine(){try{engine.close();}finally{Bundle d=new Bundle();d.putString("token",token);send(CLOSED,d);control.post(this::terminate);}}
 void terminate(){stopSelf();control.postDelayed(()->android.os.Process.killProcess(android.os.Process.myPid()),100);}
 @Override public boolean onUnbind(Intent i){stopping=true;if(engine!=null){engine.closed=true;control.postDelayed(this::terminate,4500);}else stopSelf();return false;}
 @Override public void onDestroy(){control.removeCallbacks(ticker);if(engine!=null)engine.closed=true;worker.shutdown();super.onDestroy();}
}
