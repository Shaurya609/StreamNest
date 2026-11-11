package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.io.*;import java.util.*;
/** Render-only checks in a disposable package; display overrides are owned by the host harness. */
final class HomeLayoutRegression {
 static Bundle run(Instrumentation test)throws Exception{
  Context c=test.getTargetContext();WatchSync.pending=true;WatchSync.testPairingCode="";WatchProgress.preferenceName="home-layout-test";
  try{
   List<Catalog.Title> pool=new ArrayList<>();for(String kind:new String[]{"movies","shows","animes"})for(int i=0;i<15;i++)pool.add(Catalog.parse(new JSONObject().put("imdb","tt"+(10000+pool.size())).put("title",kind+" title "+i).put("genres",new JSONArray().put("drama")).put("year",2020).put("poster_big","https://images.metahub.space/poster/medium/tt11280740/img"),kind));
   pool.add(Catalog.parse(new JSONObject().put("imdb","tt11280740").put("title","Severance").put("poster_big","https://images.metahub.space/poster/medium/tt11280740/img"),"shows"));
   WatchProgress.merge(c,new JSONObject().put("episode:tt11280740:1:1",new JSONObject().put("positionMs",125000).put("durationMs",3600000).put("updatedAt",System.currentTimeMillis())));
   HomeLibrary.cached=pool;HomeLibrary.cachedAt=System.currentTimeMillis();MainActivity a=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
   List<View> cards=new ArrayList<>();for(int n=0;n<100&&cards.isEmpty();n++){Thread.sleep(150);test.runOnMainSync(()->{cards.clear();for(View v:HomeRegression.collect(a.content))if(v.getContentDescription()!=null&&v instanceof LinearLayout&&v.isClickable())cards.add(v);});}HomeRegression.check(cards.size()>10,"Home rows missing");
   float density=a.getResources().getDisplayMetrics().density;int expected=DeviceProfile.lite(c)?112:138;test.runOnMainSync(()->{for(View card:cards){HomeRegression.check(Math.abs(card.getWidth()/density-expected)<2,"Card width not bounded");for(View child:HomeRegression.collect(card))if(child instanceof ImageView){HomeRegression.check(child.getWidth()>0&&child.getWidth()<=a.dp(expected),"Poster width invalid");HomeRegression.check(child.getHeight()>0&&child.getHeight()<=a.dp(210),"Poster height invalid");} }cards.get(0).requestFocus();});
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(300);boolean[] remote={false};test.runOnMainSync(()->{View focus=a.getCurrentFocus();remote[0]=focus!=null&&focus!=cards.get(0)&&focus.getContentDescription()!=null;});HomeRegression.check(remote[0],"D-pad did not move into another Home row");
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_RIGHT);Thread.sleep(300);test.runOnMainSync(()->{View focus=a.getCurrentFocus();HomeRegression.check(focus!=null&&focus.getContentDescription()!=null,"D-pad lost a card");});
   android.graphics.Bitmap screenshot=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(c.getCacheDir(),"home-layout.png"))){screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}screenshot.recycle();
   Bundle result=new Bundle();result.putString("result","PASS: bounded Home posters/cards, native D-pad rows and horizontal navigation");result.putInt("widthDp",Math.round(a.getResources().getDisplayMetrics().widthPixels/density));result.putInt("heightDp",Math.round(a.getResources().getDisplayMetrics().heightPixels/density));result.putBoolean("lowMemory",DeviceProfile.lite(c));result.putInt("cards",cards.size());test.runOnMainSync(a::finish);return result;
  }finally{WatchSync.pending=false;WatchSync.testPairingCode=null;HomeLibrary.clear();}
 }
}
