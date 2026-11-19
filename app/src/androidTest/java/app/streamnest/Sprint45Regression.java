package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import org.json.*;import java.util.*;import java.io.*;
final class Sprint45Regression {
 static Bundle run(Instrumentation test)throws Exception {
  android.content.Context c=test.getTargetContext();WatchProgress.preferenceName="home-regression-history";c.getSharedPreferences(WatchProgress.preferenceName,0).edit().clear().commit();
  WatchProgress.merge(c,new JSONObject().put("episode:tt11280740:1:1",new JSONObject().put("positionMs",125000).put("durationMs",3435000).put("updatedAt",System.currentTimeMillis())));
  Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);MainActivity a=(MainActivity)test.startActivitySync(intent);
  View[] resume=new View[1];
  for(int n=0;n<120&&resume[0]==null;n++){Thread.sleep(250);test.runOnMainSync(()->{for(View v:HomeRegression.collect(a.content))if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Severance")&&v.getContentDescription().toString().contains("Resume at 2:05"))resume[0]=v;});}
  HomeRegression.check(resume[0]!=null,"Continue Watching card missing");test.runOnMainSync(()->{HomeRegression.check(a.homeTab,"Startup did not select Home");HomeRegression.check(!resume[0].isFocusableInTouchMode(),"Home card consumes initial touch for focus");});
  java.util.concurrent.atomic.AtomicInteger clicks=new java.util.concurrent.atomic.AtomicInteger();int[] point=new int[2];
  test.runOnMainSync(()->{resume[0].setOnClickListener(v->clicks.incrementAndGet());resume[0].getLocationOnScreen(point);point[0]+=resume[0].getWidth()/2;point[1]+=resume[0].getHeight()/2;});
  long down=SystemClock.uptimeMillis();test.sendPointerSync(MotionEvent.obtain(down,down,MotionEvent.ACTION_DOWN,point[0],point[1],0));test.sendPointerSync(MotionEvent.obtain(down,down+50,MotionEvent.ACTION_UP,point[0],point[1],0));test.waitForIdleSync();HomeRegression.check(clicks.get()==1,"First touch did not activate card");
  test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);test.runOnMainSync(()->HomeRegression.check(resume[0].requestFocus(),"Remote focus unavailable"));test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);test.waitForIdleSync();HomeRegression.check(clicks.get()==2,"Remote center did not activate card");
  Thread.sleep(2500);android.graphics.Bitmap screen=test.getUiAutomation().takeScreenshot();if(screen!=null)try(FileOutputStream file=new FileOutputStream(new File(c.getCacheDir(),"home-regression.png"))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);screen.recycle();}
  WatchProgress.merge(c,new JSONObject().put("episode:tt11280740:1:1",new JSONObject().put("positionMs",150000).put("durationMs",3435000).put("updatedAt",System.currentTimeMillis()+1)));
  boolean[] updated={false};for(int n=0;n<60&&!updated[0];n++){Thread.sleep(250);test.runOnMainSync(()->{for(View v:HomeRegression.collect(a.content))if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Resume at 2:30"))updated[0]=true;});}HomeRegression.check(updated[0],"Synced timestamp did not refresh Home");
  Catalog.Title knownTitle=HomeLibrary.load(c).stream().filter(t->t.id.equals("tt11280740")).findFirst().get();HomeLibrary.rememberEpisodes(c,knownTitle,Arrays.asList(new JSONObject().put("season",1).put("episode",1),new JSONObject().put("season",1).put("episode",2)));
  WatchProgress.merge(c,new JSONObject().put("episode:tt11280740:1:1",new JSONObject().put("positionMs",0).put("durationMs",3435000).put("completed",true).put("updatedAt",System.currentTimeMillis()+2)));
  boolean[] upNext={false};for(int n=0;n<60&&!upNext[0];n++){Thread.sleep(250);test.runOnMainSync(()->{for(View v:HomeRegression.collect(a.content))if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Severance")&&v.getContentDescription().toString().contains("Up next · S1 · E2"))upNext[0]=true;});}HomeRegression.check(upNext[0],"Completed episode did not offer known next episode");
  test.runOnMainSync(a::finish);Bundle result=new Bundle();result.putString("result","PASS: Home first-touch activation, remote focus/center, synced resume refresh and known next episode in isolated app");return result;
 }
}
