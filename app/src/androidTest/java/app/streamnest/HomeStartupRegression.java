package app.streamnest;
import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import java.util.*;
import org.json.*;

/** Runs only in the disposable home-performance package. Never opens media. */
final class HomeStartupRegression {
 static Bundle run(Instrumentation test)throws Exception{
  Context c=test.getTargetContext();if(!c.getPackageName().equals("app.streamnest.homeperf"))throw new AssertionError("Disposable package required");
  WatchSync.pending=true;WatchSync.testPairingCode="";WatchProgress.preferenceName="home-startup-test";
  long[] full=new long[3],saved=new long[3];MainActivity[] activity={null};
  try{
   JSONObject history=new JSONObject().put("episode:tt11280740:1:1",new JSONObject().put("positionMs",125000).put("durationMs",3435000).put("updatedAt",System.currentTimeMillis()));
   WatchProgress.merge(c,history);
   for(int n=0;n<3;n++){
    HomeLibrary.clear();long start=SystemClock.elapsedRealtime();List<Catalog.Title> pool=HomeLibrary.load(c);
    List<HomeFeed.Row> rows=HomeSync.build(c,pool,history,12);full[n]=SystemClock.elapsedRealtime()-start;
    HomeLibrary.savePreview(c,rows);start=SystemClock.elapsedRealtime();List<HomeFeed.Row> preview=HomeSync.build(c,HomeLibrary.quick(c),history,12);saved[n]=SystemClock.elapsedRealtime()-start;
    HomeRegression.check(!preview.get(0).cards.isEmpty(),"Saved Continue Watching missing");
   }
   HomeLibrary.clear();long launched=SystemClock.elapsedRealtime();
   activity[0]=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));MainActivity a=activity[0];
   for(int n=0;n<100&&a.homeFirstRowsMs<=0;n++)Thread.sleep(50);
   HomeRegression.check(a.homeFirstRowsMs>=0,"Home did not render saved rows");
   long launchToRows=SystemClock.elapsedRealtime()-launched,firstRows=a.homeFirstRowsMs;
   boolean[] card={false};StringBuilder observed=new StringBuilder();
   for(int n=0;n<100&&!card[0];n++){Thread.sleep(50);test.runOnMainSync(()->{observed.setLength(0);for(View v:HomeRegression.collect(a.content))if(v.getContentDescription()!=null){observed.append(v.getContentDescription()).append(';');if(v.getContentDescription().toString().contains("Resume at 2:05"))card[0]=true;}});}
   HomeRegression.check(card[0],"Saved UI did not use current progress: "+observed);
   WatchProgress.removeTitle(c,"tt11280740");boolean[] removed={false};
   for(int n=0;n<100&&!removed[0];n++){Thread.sleep(50);test.runOnMainSync(()->{removed[0]=HomeRegression.collect(a.content).stream().noneMatch(v->v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Resume at 2:05"));});}
   HomeRegression.check(removed[0],"Saved preview resurrected removed history");
   test.runOnMainSync(()->{a.homeTab=false;a.clearContent();a.content.addView(a.text("Other tab",16,-1));a.homeTab=true;a.loadHome();});
   boolean[] returned={false};for(int n=0;n<100&&!returned[0];n++){Thread.sleep(50);test.runOnMainSync(()->returned[0]=HomeRegression.collect(a.content).stream().anyMatch(v->v instanceof android.widget.TextView&&((android.widget.TextView)v).getText().toString().equals("Something for your next watch")));}
   HomeRegression.check(returned[0],"Returning Home retained another tab");
   Bundle result=new Bundle();result.putString("result","PASS: saved Home/current progress/removal/tab return; no playback or production history touched");
   result.putString("catalogRescanMs",Arrays.toString(full));result.putString("savedPreviewMs",Arrays.toString(saved));result.putLong("launchToSavedRowsMs",launchToRows);result.putLong("firstRowsMs",firstRows);return result;
  }finally{if(activity[0]!=null)test.runOnMainSync(activity[0]::finish);WatchSync.pending=false;WatchSync.testPairingCode=null;HomeLibrary.clear();}
 }
}
