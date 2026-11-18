package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import java.util.*;import java.util.concurrent.atomic.*;import org.json.*;
/** Real episode launch from the title dialog in a disposable application profile. */
final class TitleLaunchRegression {
 static Bundle run(Instrumentation test)throws Exception {
  android.content.Context context=test.getTargetContext();
  context.getSharedPreferences("diagnostics",0).edit().putBoolean("enabled",false).commit();
  MainActivity main=(MainActivity)test.startActivitySync(new Intent(context,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  Instrumentation.ActivityMonitor monitor=test.addMonitor(PlayerActivity.class.getName(),null,false);
  final TitleScreen[] dialog=new TitleScreen[1];final long[] callMs={0};PlayerActivity player=null;
  try {
   Catalog.Title title=new Catalog.Title();title.id="tt7441658";title.title="Black Clover";title.kind="animes";title.year="2017";title.poster="";title.synopsis="";title.raw=new JSONObject();
   JSONObject ep=new JSONObject().put("season",1).put("episode",108).put("absolute",108).put("liveRelease",true);
   List<Catalog.Source> sources=new ArrayList<>();
   for(int n=0;n<2000;n++){Catalog.Source s=new Catalog.Source("1080p H264","magnet:?xt=urn:btih:f58a9899b8dfc31ccbce4428909715474c70d07a&dn="+n,7);s.release=n==0?"AAC ENG":"AAC ITA Sub.ENG";s.filename="Black Clover - S01E108 - La danzatrice del campo di battaglia [1080p H264] [AAC ENG ITA JPN] [Sub ENG ITA] [tt7441658] [WIKIRIP].mkv";sources.add(s);}
   synchronized(EpisodeLibrary.class){EpisodeLibrary.cache.put(title.id,new JSONObject().put("rows",new JSONArray().put(ep)));EpisodeLibrary.ages.put(title.id,System.currentTimeMillis());}
   test.runOnMainSync(()->{dialog[0]=new TitleScreen(main,title);dialog[0].show();});
   AtomicBoolean loaded=new AtomicBoolean();long deadline=SystemClock.elapsedRealtime()+60000;
   while(!loaded.get()&&SystemClock.elapsedRealtime()<deadline){test.runOnMainSync(()->loaded.set(dialog[0].loaded&&dialog[0].selected!=null));Thread.sleep(100);}
   if(!loaded.get())throw new AssertionError("Cached episode list did not initialize");
   Thread.sleep(500);
   test.runOnMainSync(()->{TitleScreen d=dialog[0];if(d.alternateJob!=null)d.alternateJob.cancel(true);d.sources=sources;d.selected=ep;d.episodes=Arrays.asList(ep);d.loaded=true;d.selectedQuality="Auto";d.subtitlesEnabled=false;long before=System.nanoTime();d.watch();callMs[0]=(System.nanoTime()-before)/1000000;});
   player=(PlayerActivity)monitor.waitForActivityWithTimeout(45000);if(player==null){final String[] detail={""};test.runOnMainSync(()->{TitleScreen d=dialog[0];detail[0]="showing="+d.isShowing()+" generation="+d.launchGeneration+" quality="+d.selectedQuality+" selected="+(d.selected==null?"none":EpisodeLibrary.key(d.selected))+" jobDone="+d.launchJob.isDone()+" health="+d.health.getText();});throw new AssertionError("Episode selection never opened player: "+detail[0]);}
   if(callMs[0]>500)throw new AssertionError("Source planning blocked title dialog for "+callMs[0]+"ms");
   if(player.getIntent().getIntExtra("episode",0)!=108)throw new AssertionError("Wrong selected episode");
   Bundle result=new Bundle();result.putString("result","PASS: real Black Clover episode selection from 2000-source title dialog; watch handler="+callMs[0]+"ms; player launched with correct episode");return result;
  }finally{PlayerActivity current=player;if(current!=null)test.runOnMainSync(current::finish);test.runOnMainSync(()->{if(dialog[0]!=null)dialog[0].dismiss();main.finish();});test.removeMonitor(monitor);context.stopService(new Intent(context,StreamService.class));}
 }
}
