package app.streamnest;
import android.app.*;import android.os.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
final class PerformanceRegression {
 static final class Handle extends org.libtorrent4j.swig.torrent_handle {
  int deadlines,have,valid;boolean available=true;
  Handle(){super(0,false);}
  @Override public boolean is_valid(){valid++;return true;}
  @Override public boolean have_piece(int piece){have++;return available;}
  @Override public void set_piece_deadline(int piece,int deadline){deadlines++;}
 }
 static int homeCache(android.content.Context c)throws Exception{
  HomeLibrary.cached=HomeLibrary.read(c.getAssets().open("catalog-movies-v1.json"),"movies",true);
  return HomeLibrary.cached.size();
 }
 static long heap(){Runtime r=Runtime.getRuntime();return (r.totalMemory()-r.freeMemory())/1048576;}
 static Bundle run(Instrumentation test)throws Exception{
  Bundle result=new Bundle();android.content.Context c=test.getTargetContext();File file=new File(c.getCacheDir(),"performance-pieces.bin");byte[] block=new byte[16384];Arrays.fill(block,(byte)90);
  try(FileOutputStream out=new FileOutputStream(file)){for(int n=0;n<256;n++)out.write(block);}
  TorrentEngine engine=new TorrentEngine();Handle handle=new Handle();engine.handle=new org.libtorrent4j.TorrentHandle(handle);engine.video=file;engine.size=file.length();engine.pieceLength=1048576;engine.lite=true;
  try(InputStream in=engine.new PieceStream(0,file.length()-1)){long total=0;int n;while((n=in.read(block))>=0){for(int at=0;at<n;at++)if(block[at]!=90)throw new AssertionError("Corrupt piece stream");total+=n;}if(total!=file.length())throw new AssertionError("Truncated piece stream");}
  if(handle.deadlines!=10)throw new AssertionError("Piece deadlines repeated: "+handle.deadlines);result.putInt("pieceDeadlines",handle.deadlines);result.putInt("previousDeadlinesForSameReads",640);
  int valid=handle.valid,have=handle.have;test.runOnMainSync(()->{for(int n=0;n<1000;n++)if(engine.complete())throw new AssertionError("Premature completion");});if(valid!=handle.valid||have!=handle.have)throw new AssertionError("UI queried native torrent status");
  handle.available=false;engine.verifyCompletion(1);if(engine.complete())throw new AssertionError("Unverified pieces completed");handle.available=true;engine.verifyCompletion(1);if(!engine.complete())throw new AssertionError("Complete pieces not published");result.putBoolean("completionVerifiedOffUi",true);
  SeriesIndex.sessions.size();MovieIndex.prepare(c.getAssets(),c.getFilesDir());
  CountDownLatch held=new CountDownLatch(2),release=new CountDownLatch(1);
  Thread series=new Thread(()->{synchronized(SeriesIndex.class){held.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}});
  Thread movies=new Thread(()->{synchronized(MovieIndex.class){held.countDown();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException ignored){}}});series.start();movies.start();if(!held.await(5,TimeUnit.SECONDS))throw new AssertionError("Catalog locks not acquired");
  long start=SystemClock.elapsedRealtime();try{test.runOnMainSync(()->{SeriesIndex.trimForPlayback();MovieIndex.prepare(c.getAssets(),c.getFilesDir());});long elapsed=SystemClock.elapsedRealtime()-start;if(elapsed>1000)throw new AssertionError("UI waited for catalog lock: "+elapsed);result.putLong("uiCatalogSetupMs",elapsed);}finally{release.countDown();series.join(6000);movies.join(6000);}
  long profile=SystemClock.elapsedRealtime();test.runOnMainSync(()->{for(int n=0;n<2000;n++)DeviceProfile.lite(c);});result.putLong("profileChecks2000Ms",SystemClock.elapsedRealtime()-profile);
  int titles=homeCache(c);System.gc();Thread.sleep(200);result.putInt("cachedTitles",titles);result.putLong("homeHeapBeforeClearMb",heap());HomeLibrary.clear();System.gc();Thread.sleep(200);result.putLong("homeHeapAfterClearMb",heap());if(HomeLibrary.cached!=null)throw new AssertionError("Home metadata retained");
  result.putString("result","PASS: identical 4MB streaming bytes with fewer piece deadlines; native-free UI completion; nonblocking catalog setup under held locks");return result;
 }
}
