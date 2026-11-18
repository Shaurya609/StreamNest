package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import java.util.*;import java.util.concurrent.atomic.*;import org.json.*;
/** Separate verification install, real catalog source, deliberately stopped child process. */
final class EngineRecoveryRegression {
 static void waitFor(java.util.function.BooleanSupplier check,long ms,String message)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!check.getAsBoolean()&&SystemClock.elapsedRealtime()<end)Thread.sleep(100);if(!check.getAsBoolean())throw new AssertionError(message);}
 static Bundle run(Instrumentation test)throws Exception{
  android.content.Context c=test.getTargetContext();PlayerActivity a=null;Bundle report=new Bundle();int mainPid=android.os.Process.myPid();
  Handler ui=new Handler(Looper.getMainLooper());AtomicBoolean running=new AtomicBoolean(true);AtomicLong maxGap=new AtomicLong(),previous=new AtomicLong(SystemClock.elapsedRealtime());AtomicInteger beats=new AtomicInteger();Runnable beat=new Runnable(){public void run(){long now=SystemClock.elapsedRealtime();maxGap.accumulateAndGet(now-previous.getAndSet(now),Math::max);beats.incrementAndGet();if(running.get())ui.postDelayed(this,100);}};ui.post(beat);
  try{
   JSONObject row=new JSONObject().put("season",1).put("episode",1);List<Catalog.Source> found=EpisodeDiscovery.find("tt3581920",row,"The Last of Us");Catalog.Source source=PlaybackPlan.episodeBest(found,"Auto",DeviceProfile.lite(c));if(source==null)throw new AssertionError("No real Last of Us source");
   Intent intent=new Intent(c,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("source",source.url).putExtra("title","The Last of Us S1E1").putExtra("imdb","tt3581920").putExtra("season",1).putExtra("episode",1).putExtra("episodeIdentity","tt3581920:1:1").putExtra("fileIndex",source.fileIndex).putExtra("filename",source.filename).putExtra("alternatives",AlternateEpisodes.encode(Arrays.asList(source,source))).putExtra("subtitlesEnabled",false);
   a=(PlayerActivity)test.startActivitySync(intent);PlayerActivity player=a;
   waitFor(()->StreamService.engine instanceof RemoteTorrentEngine&&((RemoteTorrentEngine)StreamService.engine).attached,15000,"Worker handshake failed");RemoteTorrentEngine old=(RemoteTorrentEngine)StreamService.engine;int oldPid=old.pid;if(oldPid==mainPid||oldPid<=0)throw new AssertionError("Native worker was not isolated");
   // SIGSTOP freezes JNI and the control looper; SIGKILL must still recover that app-owned child.
   android.os.Process.sendSignal(oldPid,19);long started=SystemClock.elapsedRealtime();
   waitFor(()->StreamService.engine instanceof RemoteTorrentEngine&&StreamService.engine!=old&&((RemoteTorrentEngine)StreamService.engine).attached,22000,"Automatic recovery queued behind stopped worker");
   RemoteTorrentEngine next=(RemoteTorrentEngine)StreamService.engine;report.putLong("recoveryMs",SystemClock.elapsedRealtime()-started);report.putBoolean("oldWorkerGone",old.died.getCount()==0);report.putBoolean("newWorkerPid",next.pid!=oldPid&&next.pid!=mainPid);report.putBoolean("playerProcessPreserved",android.os.Process.myPid()==mainPid);
   if(old.died.getCount()!=0)throw new AssertionError("New writer started before old child died");
   waitFor(()->player.playedVideo&&player.player.getTime()>10000,150000,"Recovered real source did not render video");
   report.putLong("videoPositionMs",player.player.getTime());report.putInt("audioTrack",player.player.getAudioTrack());report.putLong("maxUiGapMs",maxGap.get());report.putInt("uiHeartbeats",beats.get());if(maxGap.get()>2000)throw new AssertionError("UI blocked during recovery: "+maxGap.get());
   // Exercise warm adoption with a real next episode without waiting for a whole public torrent.
   JSONObject second=new JSONObject().put("season",1).put("episode",2);
   List<Catalog.Source> secondSources=EpisodeDiscovery.find("tt3581920",second,"The Last of Us");Catalog.Source best=PlaybackPlan.episodeBest(secondSources,"Auto",DeviceProfile.lite(c));if(best==null)throw new AssertionError("No real next episode source");
   String identity="tt3581920:1:2";EpisodePreload.Slot warm=new EpisodePreload.Slot(c,identity);warm.choices=Arrays.asList(best);
   Intent request=new Intent().putExtra("source",best.url).putExtra("filename",best.filename).putExtra("fileIndex",best.fileIndex).putExtra("episodeIdentity",identity).putExtra("season",1).putExtra("episode",2).putExtra("catalogSource",true);
   StreamService.configure(warm.engine,request);warm.engine.lite=DeviceProfile.lite(c);warm.engine.preloadOnly=true;warm.engine.root=StreamService.directory(c,request);PlaybackCache.prepareEpisode(c,identity,warm.engine.root,false);PlaybackCache.record(c,CacheLocation.current(c),warm.engine.root);
   synchronized(EpisodePreload.class){EpisodePreload.slot=warm;EpisodePreload.inFlight.add(warm.engine);EpisodePreload.attempted=next.sourceUrl+"|"+identity;warm.readyToClaim=true;}
   c.getSharedPreferences("playback",0).edit().putBoolean("preloadNext",true).commit();
   Thread preload=new Thread(()->{try{warm.engine.start(best.url,warm.engine.root);}catch(Exception error){warm.engine.closed=true;}finally{if(!warm.claimed||warm.engine.closed)warm.engine.close();if(!warm.claimed)EpisodePreload.released(warm.engine);}},"real-preload-check");preload.start();
   waitFor(()->warm.engine.url!=null,150000,"Next episode warm worker did not buffer");RemoteTorrentEngine prefetched=(RemoteTorrentEngine)warm.engine;int warmPid=prefetched.pid;java.io.File previousRoot=next.root;
   test.runOnMainSync(()->{player.episodeQueue=new JSONObject();try{player.episodeQueue.put("imdb","tt3581920").put("title","The Last of Us").put("quality","Auto");}catch(Exception e){throw new RuntimeException(e);}player.episodeRows=Arrays.asList(row,second);player.player.detachViews();player.viewsAttached=false;player.changeEpisode(1,false);});
   waitFor(()->StreamService.engine==warm.engine&&!player.changingEpisode,15000,"Warm worker was not adopted");
   if(!previousRoot.exists())throw new AssertionError("Previous cache removed before next video");if(((RemoteTorrentEngine)StreamService.engine).pid!=warmPid)throw new AssertionError("Warm worker was restarted");
   test.runOnMainSync(()->{player.player.attachViews(player.video,null,true,true);player.viewsAttached=true;});
   waitFor(()->player.playedVideo&&player.player.getTime()>1000&&!previousRoot.exists(),90000,"Next video / previous cache cleanup did not finish");
   report.putBoolean("warmWorkerReused",true);report.putBoolean("previousCachePurgedAfterVideo",true);report.putString("result","PASS: frozen native worker recovered automatically; old writer death precedes replacement; real Last of Us video; second isolated worker adopted for episode 2 without restart; previous cache purged after new video");return report;
  }finally{running.set(false);ui.removeCallbacks(beat);if(a!=null){PlayerActivity player=a;test.runOnMainSync(player::finish);}c.stopService(new Intent(c,StreamService.class));}
 }
}
