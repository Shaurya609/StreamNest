package app.streamnest;
import android.app.*;import android.os.*;import java.io.*;import java.util.*;import org.libtorrent4j.*;
final class NativeTrackerRegression {
 static Bundle run(Instrumentation test)throws Exception{
  File fixture=new File(test.getTargetContext().getCacheDir(),"tracker-metadata.torrent");TorrentInfo info=new TorrentInfo(java.nio.file.Files.readAllBytes(fixture.toPath()));SessionManager session=new SafeTorrentSession();
  try{session.start();File root=new File(test.getTargetContext().getCacheDir(),"tracker-paused");root.mkdirs();session.download(info,root,null,Priority.array(Priority.IGNORE,info.numFiles()),null,TorrentFlags.PAUSED);
   TorrentHandle handle=null;long end=SystemClock.elapsedRealtime()+5000;while(SystemClock.elapsedRealtime()<end){handle=session.find(info.infoHash());if(handle!=null&&handle.isValid())break;Thread.sleep(20);}HomeRegression.check(handle!=null&&handle.isValid(),"Paused metadata handle missing");
   Set<String> before=new HashSet<>();for(AnnounceEntry tracker:handle.trackers())before.add(tracker.url());String tracker="https://source-only.invalid/announce?key=a+b",source="magnet:?xt=urn:btih:"+info.infoHash().toHex()+"&tr="+java.net.URLEncoder.encode(tracker,"UTF-8");
   HomeRegression.check(MagnetTrackers.apply(handle,source,false)==1,"Original magnet tracker was not restored");Set<String> after=new HashSet<>();for(AnnounceEntry row:handle.trackers())after.add(row.url());HomeRegression.check(after.containsAll(before)&&after.contains(tracker),"Existing or encoded source tracker was lost");HomeRegression.check(MagnetTrackers.apply(handle,source,false)==0,"Duplicate tracker added");HomeRegression.check(MagnetTrackers.apply(handle,source+"&tr=https://private-only.invalid/announce",true)==0,"Private tracker policy bypassed");
   Bundle result=new Bundle();result.putString("result","PASS: paused native metadata retains existing trackers, restores original magnet hint once, preserves encoded query and skips private tracker supplementation; no video downloaded");return result;
  }finally{session.stop();}
 }
}
