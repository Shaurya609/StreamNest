package app.streamnest;

import android.content.*;
import java.io.*;
import java.util.*;

/** Called on StreamService's serial worker, after all engine writes have stopped. */
final class PlaybackCache {
  static volatile boolean exiting;

  static synchronized void prepareEpisode(Context c, String identity, File current, boolean activate) throws Exception {
    if (identity == null || identity.isEmpty()) return;
    SharedPreferences prefs = c.getSharedPreferences("playback_cache", 0);
    org.json.JSONObject saved = new org.json.JSONObject(prefs.getString("episodes", "{}"));
    Map<String, String> episodes = new HashMap<>();
    for (Iterator<String> keys = saved.keys(); keys.hasNext(); ) {
      String path = keys.next();
      episodes.put(path, saved.getString(path));
    }
    String currentPath = current.getCanonicalPath();
    Set<String> recorded = new HashSet<>(prefs.getStringSet("entries", Collections.emptySet()));
    for (String path : activate ? EpisodeCachePolicy.obsolete(episodes, identity, currentPath) : Collections.<String>emptyList()) {
      if(EpisodePreload.protects(new File(path)))continue;
      try {
        File entry = new File(path);
        CachePurger.deleteEntry(entry.getParentFile(), entry);
        saved.remove(path);
        recorded.remove(path);
      } catch (IOException error) {
        android.util.Log.w("StreamNest", "Previous episode cleanup will retry", error);
      }
    }
    saved.put(currentPath, identity);
    if (!prefs
        .edit()
        .putString("episodes", saved.toString())
        .putStringSet("entries", recorded)
        .commit()) throw new IOException("Could not record episode cache");
  }

  static synchronized void discard(Context c,File entry) {
    if(entry==null)return;
    try {
      TorrentEngine active=StreamService.engine;
      if((active!=null&&active.root!=null&&active.root.getCanonicalFile().equals(entry.getCanonicalFile()))||EpisodePreload.protects(entry))return;
      CachePurger.deleteEntry(entry.getParentFile(),entry);
      SharedPreferences prefs=c.getSharedPreferences("playback_cache",0);
      Set<String> paths=new HashSet<>(prefs.getStringSet("entries",Collections.emptySet()));paths.remove(entry.getCanonicalPath());
      org.json.JSONObject saved=new org.json.JSONObject(prefs.getString("episodes","{}"));saved.remove(entry.getCanonicalPath());
      prefs.edit().putStringSet("entries",paths).putString("episodes",saved.toString()).commit();
    }catch(Exception ignored){}
  }

  static synchronized void record(Context c, File root, File entry) throws IOException {
    File base = root.getCanonicalFile();
    File target = entry.getCanonicalFile();
    if (!base.equals(target.getParentFile())) throw new IOException("Invalid cache entry");
    SharedPreferences prefs = c.getSharedPreferences("playback_cache", 0);
    Set<String> paths = new HashSet<>(prefs.getStringSet("entries", Collections.emptySet()));
    paths.add(target.getPath());
    // Persist before the engine writes, so a killed process can recover next launch.
    if (!prefs.edit().putStringSet("entries", paths).commit())
      throw new IOException("Could not record video cache");
  }

  static void enter(Context context) {
    Context c = context.getApplicationContext();
    exiting = false;
    StreamService.worker.execute(
        () -> {
          if (StreamService.engine == null) purge(c);
        });
  }

  static void exit(Context context) {
    EpisodePreload.cancel();
    Context c = context.getApplicationContext();
    exiting = true;
    c.stopService(new Intent(c, StreamService.class));
    StreamService.worker.execute(
        () -> {
          if (StreamService.engine == null) purge(c);
        });
  }

  static synchronized void purge(Context c) {
    SharedPreferences prefs = c.getSharedPreferences("playback_cache", 0);
    Set<String> pending = new HashSet<>(prefs.getStringSet("entries", Collections.emptySet()));
    // The internal default folder is exclusively owned by this app, including older builds.
    File internal = new File(c.getFilesDir(), "torrents");
    File[] old = internal.listFiles();
    if (old != null) for (File f : old) pending.add(f.getAbsolutePath());
    Set<String> failed = new HashSet<>();
    for (String path : pending) {
      File entry = new File(path);
      try {
        if(EpisodePreload.protects(entry)){failed.add(path);continue;}
        CachePurger.deleteEntry(entry.getParentFile(), entry);
      } catch (IOException e) {
        failed.add(path);
        android.util.Log.w("StreamNest", "Video cache cleanup will retry", e);
      }
    }
    prefs.edit().putStringSet("entries", failed).remove("episodes").commit();
    File[] subtitles =
        c.getCacheDir()
            .listFiles(
                (dir, name) ->
                    name.equals("selected-subtitles.srt")
                        || name.matches("opensubtitles-(org-)?[0-9]+\\.srt"));
    if (subtitles != null)
      for (File file : subtitles) {
        try {
          CachePurger.deleteEntry(c.getCacheDir(), file);
        } catch (IOException ignored) {
        }
      }
  }
}
