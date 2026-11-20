package app.streamnest;
import android.app.*;import android.os.*;import android.content.*;import java.io.*;import java.util.*;import org.libtorrent4j.*;
final class NativeTrackerRegression {
 static Bundle run(Instrumentation test)throws Exception{
  File fixture=new File(test.getTargetContext().getCacheDir(),"tracker-metadata.torrent");TorrentInfo info=new TorrentInfo(java.nio.file.Files.readAllBytes(fixture.toPath()));SessionManager session=new SafeTorrentSession();
  try{session.start();File root=new File(test.getTargetContext().getCacheDir(),"tracker-paused");root.mkdirs();session.download(info,root,null,Priority.array(Priority.IGNORE,info.numFiles()),null,TorrentFlags.PAUSED);
   TorrentHandle handle=null;long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){handle=session.find(info.infoHash());if(handle!=null&&handle.isValid())break;Thread.sleep(20);}HomeRegression.check(handle!=null&&handle.isValid(),"Paused metadata handle missing");
   Set<String> before=new HashSet<>();for(AnnounceEntry tracker:handle.trackers())before.add(tracker.url());String tracker="https://source-only.invalid/announce?key=a+b",source="magnet:?xt=urn:btih:"+info.infoHash().toHex()+"&tr="+java.net.URLEncoder.encode(tracker,"UTF-8");
   HomeRegression.check(MagnetTrackers.apply(handle,source,false)==1,"Original magnet tracker was not restored");Set<String> after=new HashSet<>();for(AnnounceEntry row:handle.trackers())after.add(row.url());HomeRegression.check(after.containsAll(before)&&after.contains(tracker),"Existing or encoded source tracker was lost");HomeRegression.check(MagnetTrackers.apply(handle,source,false)==0,"Duplicate tracker added");HomeRegression.check(MagnetTrackers.apply(handle,source+"&tr=https://private-only.invalid/announce",true)==0,"Private tracker policy bypassed");
   exhaustedUi(test);
   Bundle result=new Bundle();result.putString("result","PASS: paused native metadata retains existing trackers, restores original magnet hint once, preserves encoded query and skips private tracker supplementation; final-source UI respects pause and ends a peerless wait; no video downloaded");return result;
  }finally{session.stop();}
 }
 static void exhaustedUi(Instrumentation test)throws Exception{
  android.content.Context c=test.getTargetContext();String source="http://127.0.0.1:9/unavailable.torrent";
  PlayerActivity player=(PlayerActivity)test.startActivitySync(new Intent(c,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("source",source).putExtra("title","Isolated startup control check").putExtra("subtitlesEnabled",false));
  try{
   WorkerShutdownRegression.waitFor(player::hasWindowFocus,10000,"Player did not gain focus");
   test.runOnMainSync(()->c.stopService(new Intent(c,StreamService.class)));
   WorkerShutdownRegression.waitFor(()->!StreamService.startupBlocked(),20000,"Worker cleanup remained pending");
   test.runOnMainSync(()->{TorrentEngine snapshot=new TorrentEngine();snapshot.sourceUrl=source;snapshot.startup.done();StreamService.engine=snapshot;long now=SystemClock.elapsedRealtime();player.attemptStarted=now-60000;player.lastProgressAt=now-60000;player.alternatives.clear();player.attempt=0;player.mediaSet=false;player.playedVideo=false;player.sourceFailed=false;player.startupExhausted=false;player.userPaused=true;player.tick.run();HomeRegression.check(!snapshot.closed&&!player.startupExhausted,"Pause incorrectly exhausted startup");player.userPaused=false;player.tick.run();HomeRegression.check(snapshot.closed&&player.startupExhausted,"Final source was left waiting");HomeRegression.check(player.episodeMessage!=null&&player.episodeMessage.startsWith("No peers"),"Missing actionable peerless status");});
  }finally{StreamService.engine=null;test.runOnMainSync(player::finish);c.stopService(new Intent(c,StreamService.class));}
 }
}
