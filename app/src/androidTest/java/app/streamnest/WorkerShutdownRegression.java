package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.concurrent.atomic.*;
final class WorkerShutdownRegression {
 static void waitFor(java.util.function.BooleanSupplier check,long ms,String msg)throws Exception{long end=SystemClock.elapsedRealtime()+ms;while(!check.getAsBoolean()&&SystemClock.elapsedRealtime()<end)Thread.sleep(50);if(!check.getAsBoolean())throw new AssertionError(msg);}
 static RemoteTorrentEngine start(Context c,File root)throws Exception{RemoteTorrentEngine e=new RemoteTorrentEngine(c){@Override boolean ownProcess(){return false;}};Thread run=new Thread(()->{try{e.start("magnet:?xt=urn:btih:0000000000000000000000000000000000000000",root);}catch(Exception ignored){e.closed=true;}},"isolated-worker-test");run.start();waitFor(()->e.attached,12000,"Worker handshake missing");return e;}
 static Bundle run(Instrumentation test)throws Exception{Context c=test.getTargetContext();RemoteTorrentEngine e=null;int frozen=0;try{
  File root=new File(CacheLocation.current(c),"ab".repeat(32));root.mkdirs();e=start(c,root);RemoteTorrentEngine first=e;frozen=e.pid;android.os.Process.sendSignal(frozen,19);boolean refused=false;try{e.close();}catch(IllegalStateException protectedWriter){refused=true;}HomeRegression.check(refused&&!e.stoppedConfirmed&&RemoteTorrentEngine.protectedRoots().contains(root),"Unconfirmed death released a cache lease");
  android.os.Process.sendSignal(frozen,18);frozen=0;waitFor(()->first.died.getCount()==0,8000,"Token-authorized self abort did not stop worker without process list");e.close();HomeRegression.check(!RemoteTorrentEngine.protectedRoots().contains(root),"Dead worker lease retained");
  for(int n=0;n<3;n++){e=start(c,root);e.close();HomeRegression.check(e.stoppedConfirmed&&!RemoteTorrentEngine.protectedRoots().contains(root),"Repeated retry leaked reservation");}
  Bundle out=new Bundle();out.putString("result","PASS: fully frozen worker keeps cache protected; queued self-abort confirms death without process-list lookup; cleanup retry and three same-cache worker restarts succeed");return out;
 }finally{if(frozen>0)android.os.Process.sendSignal(frozen,18);if(e!=null)try{e.close();}catch(Exception ignored){}}}
}
