package app.streamnest;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import org.json.*;
import java.util.*;

/** Observe real D-pad focus traversal, rather than calling the action directly. */
final class RemoteNavigationRegression {
 static String sizes(View v){String s=v.getClass().getSimpleName()+"("+v.getWidth()+","+v.getHeight()+",visibility="+v.getVisibility()+")";if(v instanceof ViewGroup){for(int n=0;n<((ViewGroup)v).getChildCount();n++)s+="["+sizes(((ViewGroup)v).getChildAt(n))+"]";}return s;}
 static Bundle run(Instrumentation test) throws Exception {
  Context c=test.getTargetContext();
  HomeRegression.check(!c.getPackageName().equals("app.streamnest"),"Isolated test package required");
  WatchProgress.preferenceName="remote-navigation-test";
  Catalog.Title title=Catalog.parse(new JSONObject().put("imdb","tt99999999").put("title","Disposable remote check"),"shows");
  WatchProgress.merge(c,new JSONObject().put("episode:tt99999999:1:1",new JSONObject().put("positionMs",12000).put("durationMs",120000).put("updatedAt",System.currentTimeMillis())));
  MainActivity a=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK));
  Thread.sleep(1000);
  boolean[] reachable={false}; StringBuilder observations=new StringBuilder();
  try {
   List<HomeFeed.Row> rows=HomeFeed.build(Arrays.asList(title),WatchProgress.records(c),12);
   test.runOnMainSync(()->{
    a.homeTab=false;a.generation++;if(a.browseJob!=null)a.browseJob.cancel(true);
    a.renderHome(rows);
   });
   test.waitForIdleSync();Thread.sleep(300);
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);
   test.runOnMainSync(()->{for(View v:HomeRegression.collect(a.content))if(v instanceof LinearLayout&&String.valueOf(v.getContentDescription()).startsWith(title.title))HomeRegression.check(v.requestFocus(),"Cannot focus disposable card");});
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(150);test.waitForIdleSync();
   test.runOnMainSync(()->{View v=a.getCurrentFocus();reachable[0]=v instanceof Button&&String.valueOf(v.getContentDescription()).startsWith("Remove Disposable");});
   HomeRegression.check(reachable[0],"Down skipped Remove");
   test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_CENTER);Thread.sleep(200);test.waitForIdleSync();
   HomeRegression.check(WatchProgress.read(c,"episode:tt99999999:1:1",12000)==0,"Remote Select did not remove disposable history");
   observations.append(" removeReachable=").append(reachable[0]);
   Catalog.Title real=Catalog.parse(new JSONObject().put("imdb","tt0386676").put("title","The Office"),"shows");
   TitleScreen[] screen={null};
   test.runOnMainSync(()->{screen[0]=new TitleScreen(a,real);screen[0].show();});
   for(int n=0;n<120;n++){boolean[] ready={false};test.runOnMainSync(()->ready[0]=screen[0].visible.size()>5);if(ready[0])break;Thread.sleep(250);}
   int[] step={0};
   for(int key:new int[]{KeyEvent.KEYCODE_DPAD_DOWN,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_RIGHT,KeyEvent.KEYCODE_DPAD_LEFT,KeyEvent.KEYCODE_DPAD_UP}){
    test.sendKeyDownUpSync(key);Thread.sleep(100);test.waitForIdleSync();
    test.runOnMainSync(()->{View f=screen[0].getWindow().getDecorView().findFocus();if(step[0]==0||step[0]==2||step[0]==4)HomeRegression.check(screen[0].episodesView.hasFocus(),"Episode entry failed at step "+step[0]+" listHeight="+screen[0].episodesView.getHeight()+" rootHeight="+screen[0].root.getHeight()+" tree="+sizes(screen[0].root)+" focus="+(f==null?"none":f.getClass().getSimpleName()+":"+f.getContentDescription()));else if(step[0]==1)HomeRegression.check(f==(screen[0].seasonsView!=null?screen[0].seasonsView:screen[0].seasonSpinner),"Season return failed");else if(step[0]==3)HomeRegression.check(f==screen[0].episodeQualityPicker,"Quality exit failed");else HomeRegression.check(f==screen[0].closeDetails,"Header return failed");step[0]++;observations.append(" entryKey=").append(key).append(" entryFocus=").append(f==null?"none":f.getClass().getSimpleName()+":"+f.getContentDescription()).append(" episodesFocused=").append(screen[0].episodesView.hasFocus());});
   }
   int[] first={-1},last={-1},count={0},top={-1},bottom={-1};
   test.runOnMainSync(()->{if(screen[0].seasons.contains(2)){if(screen[0].seasonSpinner!=null)screen[0].seasonSpinner.setSelection(screen[0].seasons.indexOf(2));screen[0].selectSeason(2,null);}count[0]=screen[0].visible.size();observations.append(" episodeFocusRequested=").append(screen[0].episodesView.requestFocus());screen[0].episodesView.setSelection(0);});
   Thread.sleep(400);test.waitForIdleSync();test.runOnMainSync(()->{first[0]=screen[0].episodesView.getSelectedItemPosition();top[0]=screen[0].episodesView.getFirstVisiblePosition();});
   for(int n=0;n<18;n++){test.sendKeyDownUpSync(KeyEvent.KEYCODE_DPAD_DOWN);Thread.sleep(100);test.waitForIdleSync();}
   test.runOnMainSync(()->{last[0]=screen[0].episodesView.getSelectedItemPosition();bottom[0]=screen[0].episodesView.getFirstVisiblePosition();View focus=screen[0].getWindow().getDecorView().findFocus();observations.append(" episodes=").append(count[0]).append(" selection=").append(first[0]).append("->").append(last[0]).append(" scroll=").append(top[0]).append("->").append(bottom[0]).append(" finalCount=").append(screen[0].visible.size()).append(" listHasFocus=").append(screen[0].episodesView.hasFocus()).append(" finalFocus=").append(focus==null?"none":focus.getClass().getSimpleName()+":"+focus.getContentDescription());screen[0].dismiss();});
   HomeRegression.check(last[0]==18&&bottom[0]>top[0],"Remote did not scroll beyond visible episodes: "+observations);
   Bundle out=new Bundle();out.putString("result",observations.toString());return out;
  } finally {test.runOnMainSync(a::finish);}
 }
}
