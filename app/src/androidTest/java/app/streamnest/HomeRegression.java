package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import android.widget.*;import org.json.*;import java.util.*;import java.io.*;
final class HomeRegression {
 static void check(boolean value,String message){if(!value)throw new AssertionError(message);}
 static List<View> collect(View view){List<View> list=new ArrayList<>();list.add(view);if(view instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)view;for(int i=0;i<g.getChildCount();i++)list.addAll(collect(g.getChildAt(i)));}return list;}
 static Bundle filler()throws Exception{
  android.util.Log.i("HomeRegression","phase: JSONObject page");
  JSONObject page=CatalogHttp.object("https://api.jikan.moe/v4/anime/34572/episodes?page=2",null);JSONArray data=page.getJSONArray("data");check(data.length()>0&&data.getJSONObject(0).getInt("mal_id")>100,"Filler metadata second page missing");
  JSONObject episode=new JSONObject().put("season",1).put("episode",data.getJSONObject(0).getInt("mal_id")).put("skipMalId",34572);EpisodeLibrary.attachFiller(Arrays.asList(episode),34572,data);check(episode.has("filler")&&episode.has("recap"),"Mapped second-page labels missing");
  Bundle result=new Bundle();result.putString("result","PASS: mapped filler/recap labels on episode "+episode.optInt("episode"));return result;
 }
 static Bundle run(Instrumentation test)throws Exception{
  android.content.Context c=test.getTargetContext();WatchProgress.preferenceName="home-regression-history";c.getSharedPreferences(WatchProgress.preferenceName,0).edit().clear().commit();
  WatchProgress.merge(c,new JSONObject().put("episode:tt11280740:1:1",new JSONObject().put("positionMs",125000).put("durationMs",3435000).put("updatedAt",System.currentTimeMillis())));
  Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);MainActivity a=(MainActivity)test.startActivitySync(intent);
  View[] resume=new View[1];
  for(int n=0;n<120&&resume[0]==null;n++){Thread.sleep(250);test.runOnMainSync(()->{for(View v:collect(a.content))if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Severance")&&v.getContentDescription().toString().contains("Resume at 2:05"))resume[0]=v;});}
  check(resume[0]!=null,"Continue Watching card missing");test.runOnMainSync(()->{check(a.homeTab,"Startup did not select Home");check(a.filtersRow.getVisibility()==View.GONE,"Home filters should be hidden");resume[0].setFocusableInTouchMode(true);resume[0].requestFocus();});
  Thread.sleep(2500);android.graphics.Bitmap screen=test.getUiAutomation().takeScreenshot();if(screen!=null)try(FileOutputStream file=new FileOutputStream(new File(c.getCacheDir(),"home-regression.png"))){screen.compress(android.graphics.Bitmap.CompressFormat.PNG,100,file);screen.recycle();}
  WatchProgress.merge(c,new JSONObject().put("episode:tt11280740:1:1",new JSONObject().put("positionMs",150000).put("durationMs",3435000).put("updatedAt",System.currentTimeMillis()+1)));
  boolean[] updated={false};for(int n=0;n<60&&!updated[0];n++){Thread.sleep(250);test.runOnMainSync(()->{for(View v:collect(a.content))if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Resume at 2:30"))updated[0]=true;});}check(updated[0],"Synced timestamp did not refresh Home");
  Catalog.Title knownTitle=HomeLibrary.load(c).stream().filter(t->t.id.equals("tt11280740")).findFirst().get();HomeLibrary.rememberEpisodes(c,knownTitle,Arrays.asList(new JSONObject().put("season",1).put("episode",1),new JSONObject().put("season",1).put("episode",2)));
  WatchProgress.merge(c,new JSONObject().put("episode:tt11280740:1:1",new JSONObject().put("positionMs",0).put("durationMs",3435000).put("completed",true).put("updatedAt",System.currentTimeMillis()+2)));
  boolean[] upNext={false};for(int n=0;n<60&&!upNext[0];n++){Thread.sleep(250);test.runOnMainSync(()->{for(View v:collect(a.content))if(v.getContentDescription()!=null&&v.getContentDescription().toString().contains("Severance")&&v.getContentDescription().toString().contains("Up next · S1 · E2"))upNext[0]=true;});}check(upNext[0],"Completed episode did not offer known next episode");
  test.runOnMainSync(()->{a.tab("movies");check(!a.homeTab,"Movies navigation failed");a.homeTab=true;a.load();});
  android.util.Log.i("HomeRegression","phase: List<Catalog.Source> independent");
  List<Catalog.Source> independent=EztvEpisodes.find("tt11280740",1,1,System.nanoTime()+12000000000L);check(!independent.isEmpty(),"Live independent series sources missing");
  android.util.Log.i("HomeRegression","phase: List<SkipSegments.Segment> regular");
  List<SkipSegments.Segment> regular=SkipSegments.load("tt10919420",1,1,0,3655000,url->{if(!url.contains("skipdb.tv"))throw new IOException("Injected primary outage");return CatalogHttp.object(url,null);});check(!regular.isEmpty(),"Live series fallback timestamps missing");
  android.util.Log.i("HomeRegression","phase: List<SkipSegments.Segment> anime");
  List<SkipSegments.Segment> anime=SkipSegments.load("tt7441658",1,1,34572,1435000,url->{if(!url.contains("aniskip.com"))throw new IOException("Injected preceding-provider outage");return CatalogHttp.object(url,null);});check(anime.size()==2,"Live anime fallback timestamps missing");
  Bundle result=new Bundle();result.putBoolean("lowMemory",DeviceProfile.lite(c));result.putLong("javaHeapMB",(Runtime.getRuntime().totalMemory()-Runtime.getRuntime().freeMemory())/1024/1024);result.putString("result","PASS: Home startup/cards/remote focus/sync refresh/known-next/navigation; independent series="+independent.size()+"; live series fallback="+regular.size()+" anime="+anime.size());test.runOnMainSync(a::finish);return result;
 }
}
