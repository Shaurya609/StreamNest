package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.*;
import org.libtorrent4j.*;import org.libtorrent4j.alerts.*;

/** No USB is impersonated: native status/control and protected-cache tests only. */
final class UsbStorageRegression {
 static Bundle run(Instrumentation test)throws Exception{
  Context c=test.getTargetContext();if(!c.getPackageName().equals("app.streamnest.homeperf"))throw new AssertionError("Disposable package required");
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();bytes.write("d4:infod6:lengthi16384e4:name16:status-check.bin12:piece lengthi16384e6:pieces20:".getBytes(java.nio.charset.StandardCharsets.US_ASCII));bytes.write(new byte[20]);bytes.write("ee".getBytes(java.nio.charset.StandardCharsets.US_ASCII));
  TorrentInfo info=new TorrentInfo(bytes.toByteArray());HomeRegression.check(info.isValid(),"Fixture metadata invalid");
  TorrentEngine e=new TorrentEngine();e.session=new SafeTorrentSession();SettingsPack settings=new SettingsPack();settings.setEnableDht(false);settings.setEnableLsd(false);
  e.session.addListener(new AlertListener(){public int[] types(){return new int[]{AlertType.STATE_UPDATE.swig()};}public void alert(Alert<?> alert){if(alert instanceof StateUpdateAlert){for(TorrentStatus s:((StateUpdateAlert)alert).status())e.acceptStatus(s,e.statusGeneration);e.statusPending=false;}}});
  File root=new File(c.getCacheDir(),"native-async-status");root.mkdirs();
  try{
   e.session.start(new SessionParams(settings));e.session.download(info,root,null,Priority.array(Priority.IGNORE,info.numFiles()),null,TorrentFlags.PAUSED);
   for(int i=0;i<100;i++){e.handle=e.session.find(info.infoHash());if(e.handle!=null&&e.handle.isValid())break;Thread.sleep(20);}
   HomeRegression.check(e.handle!=null,"Metadata-only handle missing");
   long longest=0;for(int i=0;i<20&&e.lastStatusText.isEmpty();i++){long before=SystemClock.elapsedRealtime();e.updateStatus();longest=Math.max(longest,SystemClock.elapsedRealtime()-before);Thread.sleep(100);}
   HomeRegression.check(!e.lastStatusText.isEmpty(),"Asynchronous native status never arrived");HomeRegression.check(!e.available.has(0),"Unverified piece was marked ready");HomeRegression.check(longest<1000,"Status request blocked worker");
   File cacheBase=new File(c.getFilesDir(),"cache-guard-test");File protectedRoot=new File(cacheBase,"a".repeat(64)),stoppedRoot=new File(cacheBase,"b".repeat(64));protectedRoot.mkdirs();stoppedRoot.mkdirs();new File(protectedRoot,"keep.bin").createNewFile();new File(stoppedRoot,"old.bin").createNewFile();
   PlaybackCache.record(c,cacheBase,protectedRoot);PlaybackCache.record(c,cacheBase,stoppedRoot);e.root=protectedRoot;StreamService.engine=e;PlaybackCache.purge(c);
   HomeRegression.check(new File(protectedRoot,"keep.bin").isFile(),"Active cache removed");HomeRegression.check(!stoppedRoot.exists(),"Stopped cache was not cleaned");StreamService.engine=null;
   nativeStorageUi(test);
   Bundle out=new Bundle();out.putString("result","PASS: asynchronous native status without downloaded/verified media; protected active cache; stopped-cache cleanup; storage failure prevents source cycling");out.putLong("longestStatusRequestMs",longest);return out;
  }finally{StreamService.engine=null;e.closed=true;e.session.stop();}
 }
 static void nativeStorageUi(Instrumentation test)throws Exception{
  Context c=test.getTargetContext();String source="http://127.0.0.1:9/unavailable.torrent";
  PlayerActivity p=(PlayerActivity)test.startActivitySync(new Intent(c,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK).putExtra("source",source).putExtra("subtitlesEnabled",false));
  try{
   WorkerShutdownRegression.waitFor(p::hasWindowFocus,10000,"Player control did not gain focus");test.runOnMainSync(()->c.stopService(new Intent(c,StreamService.class)));WorkerShutdownRegression.waitFor(()->!StreamService.startupBlocked(),20000,"Worker cleanup remained pending");
   test.runOnMainSync(()->{TorrentEngine failure=new TorrentEngine();failure.sourceUrl=source;failure.storageFailure=true;failure.closed=true;failure.message="Storage check";StreamService.engine=failure;p.alternatives=Arrays.asList(new Catalog.Source("one",source,0),new Catalog.Source("two",source+"?2",0));p.attempt=0;p.switchSource(true);HomeRegression.check(p.attempt==0,"Storage failure cycled to another source");});
  }finally{StreamService.engine=null;test.runOnMainSync(p::finish);c.stopService(new Intent(c,StreamService.class));}
 }
}
