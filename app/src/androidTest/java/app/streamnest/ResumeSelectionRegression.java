package app.streamnest;

import android.app.*;
import android.content.*;
import android.os.*;
import java.util.*;
import org.json.*;

/** Real title/season UI with cached canonical fixtures and isolated watch history. */
final class ResumeSelectionRegression {
  static Bundle run(PlaybackRegression p) throws Exception {
    String imdb="tt999999991",namespace="resume-selection-"+UUID.randomUUID();
    String oldNamespace=WatchProgress.preferenceName;
    boolean oldPending=WatchSync.pending;
    MainActivity main=null;TitleScreen screen=null;
    WatchSync.pending=true;WatchProgress.preferenceName=namespace;
    SharedPreferences prefs=p.getTargetContext().getSharedPreferences("settings",0);
    String oldSelected=null;boolean hadSelected=false;
    try {
      main=(MainActivity)p.startActivitySync(new Intent(p.getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      prefs=main.prefs;hadSelected=prefs.contains("episode_"+imdb);oldSelected=prefs.getString("episode_"+imdb,"");
      JSONArray rows=new JSONArray();
      for(int[] pair:new int[][]{{1,1},{1,2},{2,1}}) {
        rows.put(new JSONObject().put("season",pair[0]).put("episode",pair[1]).put("title","Fixture episode")
            .put("torrents",new JSONObject().put("720p",new JSONObject().put("url","magnet:?xt=urn:btih:0000000000000000000000000000000000000011").put("seed",10))));
        synchronized(AlternateEpisodes.cache){String key=imdb+":"+pair[0]+":"+pair[1];AlternateEpisodes.cache.put(key,Collections.emptyList());AlternateEpisodes.ages.put(key,System.currentTimeMillis());}
      }
      synchronized(EpisodeLibrary.class){EpisodeLibrary.cache.put(imdb,new JSONObject().put("rows",rows));EpisodeLibrary.ages.put(imdb,System.currentTimeMillis());}
      for(String kind:new String[]{"shows","animes"}) {
        for(boolean completed:new boolean[]{false,true}) {
          WatchProgress.save(main,WatchProgress.identity(imdb,1,2,""),completed?60000:12000,60000);
          Catalog.Title title=Catalog.parse(new JSONObject().put("imdb",imdb).put("title","Resume fixture").put("year","2020"),kind);
          MainActivity home=main;TitleScreen[] dialog={null};p.runOnMainSync(()->{dialog[0]=new TitleScreen(home,title);dialog[0].show();});screen=dialog[0];
          long until=SystemClock.elapsedRealtime()+8000;boolean[] ready={false};
          while(SystemClock.elapsedRealtime()<until){p.runOnMainSync(()->ready[0]=dialog[0].selected!=null);if(ready[0])break;SystemClock.sleep(50);}
          int season=completed?2:1,episode=completed?1:2;
          p.runOnMainSync(()->{if(dialog[0].selected==null||dialog[0].activeSeason!=season||dialog[0].selected.optInt("episode")!=episode||!dialog[0].watch.isEnabled())throw new AssertionError("Wrong selected episode / disabled playback for "+kind);});
          if(!completed&&WatchProgress.read(main,WatchProgress.identity(imdb,season,episode,""),0)!=12000)throw new AssertionError("Resume timestamp lost");
          p.runOnMainSync(screen::dismiss);screen=null;
        }
      }
      Bundle out=new Bundle();out.putString("result","PASS: real series/anime title UI selected resumed season/episode; timestamp retained; completed episode advanced across season boundary");return out;
    } finally {
      if(screen!=null)p.runOnMainSync(screen::dismiss);
      if(main!=null){SharedPreferences.Editor edit=main.prefs.edit();if(hadSelected)edit.putString("episode_"+imdb,oldSelected);else edit.remove("episode_"+imdb);edit.commit();p.runOnMainSync(main::finish);}
      synchronized(EpisodeLibrary.class){EpisodeLibrary.cache.remove(imdb);EpisodeLibrary.ages.remove(imdb);}
      synchronized(AlternateEpisodes.cache){for(int[] pair:new int[][]{{1,1},{1,2},{2,1}}){String key=imdb+":"+pair[0]+":"+pair[1];AlternateEpisodes.cache.remove(key);AlternateEpisodes.ages.remove(key);}}
      p.getTargetContext().deleteSharedPreferences(namespace);WatchProgress.preferenceName=oldNamespace;WatchSync.pending=oldPending;
    }
  }
}
