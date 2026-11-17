package app.streamnest;
import android.app.*;import android.os.*;import android.widget.*;import android.graphics.*;import java.io.*;import java.util.*;import java.util.concurrent.*;import java.util.concurrent.atomic.*;
final class PosterRegression {
 static void drain(PosterLoader loader)throws Exception {
  int threads=loader.worker.getCorePoolSize();CountDownLatch ready=new CountDownLatch(threads),release=new CountDownLatch(1);
  try{for(int n=0;n<threads;n++)loader.worker.execute(()->{ready.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});if(!ready.await(10,TimeUnit.SECONDS))throw new AssertionError("Poster queue blocked");}finally{release.countDown();}
 }
 static Bundle run(Instrumentation i)throws Exception {
  android.content.Context c=i.getTargetContext();AtomicInteger fetched=new AtomicInteger();
  Bitmap bitmap=Bitmap.createBitmap(32,48,Bitmap.Config.RGB_565);ByteArrayOutputStream out=new ByteArrayOutputStream();bitmap.compress(Bitmap.CompressFormat.PNG,100,out);bitmap.recycle();byte[] bytes=out.toByteArray();
  PosterLoader loader=new PosterLoader(c,url->{fetched.incrementAndGet();return bytes;});CountDownLatch gate=new CountDownLatch(1);
  try {
   // Occupy every worker so every queued request can be invalidated before it starts.
   for(int n=0;n<loader.worker.getCorePoolSize();n++)loader.worker.execute(()->{try{gate.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}});
   i.runOnMainSync(()->{for(int n=0;n<30;n++){ImageView v=new ImageView(c);loader.load("https://poster.invalid/"+UUID.randomUUID(),v);v.setTag("replaced");}});
   long start=System.nanoTime();gate.countDown();drain(loader);
   if(fetched.get()!=0)throw new AssertionError("Stale posters fetched: "+fetched.get());
   String url="https://poster.invalid/"+UUID.randomUUID();
   i.runOnMainSync(()->loader.load(url,new ImageView(c)));drain(loader);
   i.waitForIdleSync();if(loader.cache.get(url)==null)throw new AssertionError("Poster decode failed");
   Bundle result=new Bundle();result.putString("result","PASS: staleRequests=30 fetches=0; real poster decode succeeded; queueDrainMs="+((System.nanoTime()-start)/1000000));return result;
  } finally{gate.countDown();loader.close();}
 }
}
