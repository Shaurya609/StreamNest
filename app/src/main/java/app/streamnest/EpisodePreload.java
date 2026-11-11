package app.streamnest;

import android.content.*;
import java.io.*;
import java.util.*;
import org.json.*;

/** At most one speculative engine. A claimed engine becomes the foreground stream. */
final class EpisodePreload {
  static final class Slot {
    final TorrentEngine engine=new TorrentEngine();
    final String identity;
    volatile List<Catalog.Source> choices;
    volatile boolean claimed,readyToClaim;
    Slot(String identity){this.identity=identity;}
  }
  static Slot slot;
  static final Set<TorrentEngine> inFlight=new HashSet<>();
  static synchronized void released(TorrentEngine e){inFlight.remove(e);}
  static synchronized boolean protects(File path) throws IOException {
    for(TorrentEngine e:inFlight)if(e.root!=null&&e.root.getCanonicalFile().equals(path.getCanonicalFile()))return true;
    TorrentEngine old=StreamService.retiring;
    return old!=null&&old.root!=null&&old.root.getCanonicalFile().equals(path.getCanonicalFile());
  }
  static String attempted="",message="";
  static boolean enabled(Context c){return c.getSharedPreferences("playback",0).getBoolean("preloadNext",false);}
  static synchronized List<Catalog.Source> choices(String identity){return slot!=null&&slot.identity.equals(identity)&&!slot.engine.closed?slot.choices:null;}
  static synchronized TorrentEngine claim(Context c,Intent request) {
    Slot value=slot;
    if(value!=null&&value.readyToClaim&&enabled(c)&&value.identity.equals(request.getStringExtra("episodeIdentity"))
        &&Objects.equals(value.engine.sourceUrl,request.getStringExtra("source"))
        &&value.engine.fileIndex==request.getIntExtra("fileIndex",-1)&&!value.engine.closed){
      slot=null;value.claimed=true;value.engine.preloadOnly=false;message="";return value.engine;
    }
    cancel();return null;
  }
  static synchronized void cancel(){if(slot!=null){slot.engine.closed=true;slot=null;}attempted="";message="";}
  static synchronized void consider(Context context,TorrentEngine active,JSONObject queue,JSONObject next) {
    if(!enabled(context)){cancel();return;}
    if(active==null||active.closed||!active.complete()||queue==null||next==null||PlaybackCache.exiting)return;
    for(TorrentEngine other:inFlight)if(other!=active&&(slot==null||other!=slot.engine))return;
    String identity=queue.optString("imdb")+":"+EpisodeLibrary.key(next);
    String attempt=active.sourceUrl+"|"+identity;
    if(attempt.equals(attempted))return;
    cancel();attempted=attempt;
    Slot value=new Slot(identity);slot=value;inFlight.add(value.engine);
    Context c=context.getApplicationContext();
    new Thread(()->{
      try {
        if(CacheLocation.freeSpace(CacheLocation.current(c))<512L*1024*1024)throw new IOException("Not enough free storage");
        List<Catalog.Source> found=EpisodeQueue.resolve(queue,next,DeviceProfile.lite(c));
        synchronized(EpisodePreload.class){if(slot!=value||value.engine.closed)return;value.choices=found;}
        Catalog.Source best=found.get(0);
        Intent request=new Intent().putExtra("source",best.url).putExtra("filename",best.filename)
            .putExtra("fileIndex",best.fileIndex).putExtra("episodeIdentity",identity)
            .putExtra("season",next.optInt("season")).putExtra("episode",next.optInt("episode"))
            .putExtra("absolute",next.optInt("absolute")).putExtra("catalogSource",queue.optString("imdb").matches("tt[0-9]+"));
        StreamService.configure(value.engine,request);value.engine.lite=DeviceProfile.lite(c);value.engine.preloadOnly=true;
        File dir=StreamService.directory(c,request);value.engine.root=dir;
        synchronized(PlaybackCache.class){PlaybackCache.prepareEpisode(c,identity,dir,false);PlaybackCache.record(c,CacheLocation.current(c),dir);}
        synchronized(EpisodePreload.class){if(value.engine.closed||slot!=value)return;value.readyToClaim=true;}
        message="Preloading next episode";
        value.engine.start(best.url,dir);
      }catch(Throwable error){value.engine.startup.done();if(!value.claimed)message="Next episode preload unavailable";else value.engine.message="Unable to stream preloaded episode";}
      finally {
        if(!value.claimed||value.engine.closed)value.engine.close();
        if(!value.claimed){released(value.engine);synchronized(EpisodePreload.class){if(slot==value)slot=null;}PlaybackCache.discard(c,value.engine.root);}
      }
    },"episode-preload").start();
  }
}
