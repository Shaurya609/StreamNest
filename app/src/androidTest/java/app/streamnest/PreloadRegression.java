package app.streamnest;
import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.*;
import org.json.*;

final class PreloadRegression {
  interface Check{boolean ready() throws Exception;}
  static File fixture;
  static final java.util.Set<TorrentEngine> partialFilled=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
  static final java.util.Set<TorrentEngine> filled=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
  static final java.util.Set<TorrentEngine> configured=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
  static void peers(){
    java.util.List<TorrentEngine> engines=new java.util.ArrayList<>();
    if(StreamService.engine!=null)engines.add(StreamService.engine);
    synchronized(EpisodePreload.class){if(EpisodePreload.slot!=null)engines.add(EpisodePreload.slot.engine);}
    for(TorrentEngine e:engines)try{if(!e.closed&&e.handle!=null&&e.handle.isValid()){
      if(configured.add(e)){
        org.libtorrent4j.SettingsPack settings=new org.libtorrent4j.SettingsPack().listenInterfaces("127.0.0.1:0");
        settings.setString(org.libtorrent4j.swig.settings_pack.string_types.outgoing_interfaces.swigValue(),"127.0.0.1");
        e.session.applySettings(settings);
        android.util.Log.i("PreloadFixture","Session paused="+e.session.isPaused()+" state="+e.handle.status().state()+" error="+e.handle.status().errorCode().getMessage());
      }
      if(e.requestedEpisode!=3&&e.info!=null&&e.pieceLength>0&&fixture!=null&&(e.requestedEpisode==2&&e.preloadOnly?partialFilled:filled).add(e)){
        try(RandomAccessFile input=new RandomAccessFile(fixture,"r")){
          if(input.length()!=e.size)throw new AssertionError("Fixture size does not match torrent");
          for(int piece=0;piece<e.info.numPieces();piece++){
            byte[] bytes=new byte[(int)Math.min(e.pieceLength,input.length()-input.getFilePointer())];input.readFully(bytes);
            if(!(e.requestedEpisode==2&&e.preloadOnly)||piece*(long)e.pieceLength<2*1024*1024||(piece+1L)*e.pieceLength>e.size-2*1024*1024)
              e.handle.addPiece(piece,bytes,org.libtorrent4j.TorrentHandle.OVERWRITE_EXISTING);
          }
        }
      }
      e.handle.swig().connect_peer(new org.libtorrent4j.TcpEndpoint("127.0.0.1",18768).swig());}}catch(Exception ignored){}
  }
  static void waitFor(Check check,String description) throws Exception {long end=SystemClock.elapsedRealtime()+90000;while(SystemClock.elapsedRealtime()<end){peers();if(check.ready())return;Thread.sleep(100);}throw new AssertionError(description);}
  static Bundle run(PlaybackRegression p) throws Exception {
    Context c=p.getTargetContext();
    if(!c.getPackageName().endsWith(".verification"))throw new AssertionError("Preload test requires an isolated verification app");
    fixture=new File(c.getCacheDir(),"playback-regression.mp4");
    if(!fixture.isFile())throw new AssertionError("Local fixture required");
    EpisodeQueue.Finder previous=EpisodeQueue.finder;String token=null;
    try {
      c.getSharedPreferences("playback",0).edit().putBoolean("preloadNext",false).putBoolean("autoNext",false).putBoolean("skipButtons",false).commit();
      EpisodeQueue.finder=(id,s,e)->Collections.emptyList();
      List<JSONObject> rows=new ArrayList<>();
      for(int e=1;e<=3;e++)rows.add(new JSONObject().put("season",1).put("episode",e).put("torrents",new JSONObject().put("720p",new JSONObject().put("url","http://127.0.0.1:18767/episode"+e+".torrent").put("quality","720p").put("seed",10))));
      Catalog.Title title=new Catalog.Title();title.id="tt90000001";title.title="Local preload fixture";title.raw=new JSONObject();
      token=EpisodeQueue.create(c,title,rows,"Auto");
      Intent request=new Intent(c,PlayerActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
          .putExtra("source","http://127.0.0.1:18767/episode1.torrent").putExtra("title",title.title).putExtra("imdb",title.id)
          .putExtra("season",1).putExtra("episode",1).putExtra("episodeIdentity",title.id+":1:1").putExtra("episodeQueue",token).putExtra("subtitlesEnabled",false);
      p.activity=(PlayerActivity)p.startActivitySync(request);
      try{waitFor(()->StreamService.engine!=null&&StreamService.engine.complete()&&p.activity.playedVideo,"Current episode did not download/render");}
      catch(AssertionError error){TorrentEngine e=StreamService.engine;throw new AssertionError(error.getMessage()+" / "+(e==null?"no engine":e.message+" / "+e.status()+" size="+e.size+" complete="+e.complete()+" url="+e.url)+" frames="+p.activity.videoOutputs+" position="+p.activity.player.getTime());}
      if(EpisodePreload.slot!=null)throw new AssertionError("Preloading enabled by default");
      p.assertFrame("first fixture video");
      File first=StreamService.engine.root;
      c.getSharedPreferences("playback",0).edit().putBoolean("preloadNext",true).commit();
      waitFor(()->EpisodePreload.slot!=null&&EpisodePreload.slot.engine.url!=null&&!EpisodePreload.slot.engine.complete(),"Partial next-episode preload did not become playable");
      TorrentEngine warm=EpisodePreload.slot.engine;
      if(!first.exists())throw new AssertionError("Active episode deleted while preloading");
      p.main(()->{p.activity.player.detachViews();p.activity.viewsAttached=false;p.activity.changeEpisode(1,false);});
      waitFor(()->StreamService.engine==warm&&!p.activity.changingEpisode,"Preloaded engine was not adopted");
      Thread.sleep(500);
      if(!first.exists())throw new AssertionError("Previous episode purged before next video played");
      p.main(()->{p.activity.player.attachViews(p.activity.video,null,true,true);p.activity.viewsAttached=true;});
      waitFor(()->p.activity.playedVideo&&!first.exists(),"Previous episode not purged after decoded playback");
      p.assertFrame("preloaded next video");
      waitFor(()->EpisodePreload.slot!=null&&EpisodePreload.slot.engine.root!=null&&EpisodePreload.slot.engine.handle!=null,"Third episode did not begin preloading");
      TorrentEngine third=EpisodePreload.slot.engine;File speculative=third.root;
      c.getSharedPreferences("playback",0).edit().putBoolean("preloadNext",false).commit();EpisodePreload.cancel();
      waitFor(()->!speculative.exists(),"Cancelled preload cache was not purged");
      if(StreamService.engine!=warm||warm.closed||!warm.root.exists())throw new AssertionError("Disabling preload damaged active playback");
      PlaybackCache.exit(c);
      waitFor(()->StreamService.engine==null&&!warm.root.exists(),"Exit did not close and purge active cache");
      Bundle result=new Bundle();result.putString("result","PASS: native hash-verified fixture pieces; opt-in preload; partial engine reuse; previous cache retained until decoded video; cancellation preserves active episode; exit purges after engine closure");return result;
    }finally{
      EpisodePreload.cancel();EpisodeQueue.finder=previous;
      if(p.activity!=null)p.main(p.activity::finish);
      if(token!=null)new File(new File(c.getFilesDir(),"episode-sessions"),token+".json").delete();
    }
  }
}
