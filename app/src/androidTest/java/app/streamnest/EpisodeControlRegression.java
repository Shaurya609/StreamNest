package app.streamnest;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import java.io.*;
import java.util.*;
import org.json.*;
import org.videolan.libvlc.Media;

final class EpisodeControlRegression {
  static void waitEpisode(PlaybackRegression p, int season, int episode) throws Exception {
    for (int i = 0; i < 80; i++) {
      final boolean[] ready = {false};
      p.main(
          () ->
              ready[0] =
                  p.activity.getIntent().getIntExtra("season", -1) == season
                      && p.activity.getIntent().getIntExtra("episode", -1) == episode
                      && !p.activity.changingEpisode);
      if (ready[0]) return;
      Thread.sleep(100);
    }
    throw new AssertionError("Episode transition timed out");
  }

  static void fixture(PlaybackRegression p, File file) {
    p.main(
        () -> {
          PlayerActivity a = p.activity;
          if (StreamService.engine != null) StreamService.engine.closed = true;
          a.mediaSet = true;
          a.skipRequested = true;
          Media m = new Media(a.vlc, Uri.fromFile(file));
          m.setHWDecoderEnabled(true, false);
          a.player.setMedia(m);
          m.release();
          a.player.play();
        });
    p.waitForFrame();
    SystemClock.sleep(700);
  }

  static Bundle run(PlaybackRegression p) throws Exception {
    Context c = p.getTargetContext();
    File file = new File(c.getCacheDir(), "playback-regression.mp4");
    if (!file.isFile()) throw new AssertionError("Playback fixture required");
    SharedPreferences prefs = c.getSharedPreferences("playback", 0);
    boolean hadAuto = prefs.contains("autoNext"),
        oldAuto = prefs.getBoolean("autoNext", true),
        hadSkip = prefs.contains("skipButtons"),
        oldSkip = prefs.getBoolean("skipButtons", true);
    EpisodeQueue.Finder old = EpisodeQueue.finder;
    String token = null;
    Bundle result = new Bundle();
    try {
      prefs.edit().putBoolean("autoNext", true).putBoolean("skipButtons", false).commit();
      EpisodeQueue.finder =
          (id, s, e) -> {
            Catalog.Source source =
                new Catalog.Source(
                    "720p test fixture",
                    "magnet:?xt=urn:btih:"
                        + String.format(java.util.Locale.ROOT, "%040d", s * 10 + e),
                    10);
            source.fileIndex = e;
            source.filename = "episode-" + s + "-" + e + ".mp4";
            return Arrays.asList(source);
          };
      Catalog.Title title = new Catalog.Title();
      title.id = "tt999999999";
      title.title = "Episode controls regression";
      title.raw = new JSONObject();
      List<JSONObject> rows =
          Arrays.asList(
              new JSONObject().put("season", 0).put("episode", 1),
              new JSONObject().put("season", 1).put("episode", 1),
              new JSONObject().put("season", 1).put("episode", 2),
              new JSONObject().put("season", 2).put("episode", 1));
      token = EpisodeQueue.create(c, title, rows, "Auto");
      p.activity =
          (PlayerActivity)
              p.startActivitySync(
                  new Intent(c, PlayerActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                      .putExtra(
                          "source", "magnet:?xt=urn:btih:0000000000000000000000000000000000000011")
                      .putExtra("title", title.title)
                      .putExtra("imdb", title.id)
                      .putExtra("season", 1)
                      .putExtra("episode", 1)
                      .putExtra("episodeIdentity", title.id + ":1:1")
                      .putExtra("episodeQueue", token)
                      .putExtra("subtitlesEnabled", false));
      for (int i = 0; i < 50 && p.activity.episodeQueue == null; i++) Thread.sleep(100);
      if (p.activity.episodeQueue == null) throw new AssertionError("Queue did not load");
      p.main(
          () -> {
            if (p.activity.previousEpisode.isEnabled() || !p.activity.nextEpisode.isEnabled())
              throw new AssertionError("Boundary controls incorrect");
          });
      android.util.Log.i("EpisodeTest", "Starting local media fixture");
      fixture(p, file);
      p.main(
          () -> {
            p.activity.segments = Arrays.asList(new SkipSegments.Segment("intro", 8000, 18000));
            p.activity.player.setTime(10000);
            p.activity.updateSegment(10000);
            if (p.activity.skipSegment.getVisibility() != View.VISIBLE)
              throw new AssertionError("Intro control hidden");
            p.activity.skipSegment.performClick();
          });
      Thread.sleep(900);
      p.main(
          () -> {
            if (p.activity.player.getTime() < 17500) throw new AssertionError("Intro seek failed");
          });
      p.assertFrame("after intro skip");
      p.main(
          () -> {
            long len = p.activity.player.getLength();
            p.activity.segments =
                Arrays.asList(new SkipSegments.Segment("outro", len - 12000, len - 5000));
            p.activity.updateSegment(len - 11000);
            p.activity.skipSegment.performClick();
          });
      Thread.sleep(500);
      p.main(
          () -> {
            long len = p.activity.player.getLength(), pos = p.activity.player.getTime();
            if (pos < len - 6000 || pos >= len - 2000)
              throw new AssertionError("Credits skipped past later content");
            p.activity.nextEpisode.performClick();
          });
      waitEpisode(p, 1, 2);
      p.main(
          () -> {
            if (p.activity.getIntent().getIntExtra("fileIndex", -1) != 2)
              throw new AssertionError("Pack member was lost");
            p.activity.previousEpisode.performClick();
          });
      waitEpisode(p, 1, 1);
      fixture(p, file);
      p.main(() -> p.activity.player.setTime(p.activity.player.getLength() - 2500));
      waitEpisode(p, 1, 2);
      Thread.sleep(1500);
      waitEpisode(p, 1, 2);
      result.putBoolean("naturalEndAutoplay", true);
      EpisodeQueue.finder = (id, s, e) -> Collections.emptyList();
      p.main(() -> p.activity.nextEpisode.performClick());
      Thread.sleep(1200);
      waitEpisode(p, 1, 2);
      p.main(
          () -> {
            if (p.activity.episodeMessage == null
                || !p.activity.episodeMessage.contains("No source"))
              throw new AssertionError("Unavailable episode not reported");
          });
      prefs.edit().putBoolean("autoNext", false).commit();
      fixture(p, file);
      p.main(() -> p.activity.player.setTime(p.activity.player.getLength() - 2500));
      Thread.sleep(5000);
      waitEpisode(p, 1, 2);
      result.putString(
          "result",
          "PASS: decoded video after intro/credits skip, previous/next and exact pack member,"
              + " natural EOF autoplay once, unavailable-next protection and autoplay-off"
              + " behavior");
      return result;
    } catch (Throwable error) {
      android.util.Log.e("EpisodeTest", "Regression failed", error);
      throw error;
    } finally {
      EpisodeQueue.finder = old;
      SharedPreferences.Editor edit = prefs.edit();
      if (hadAuto) edit.putBoolean("autoNext", oldAuto);
      else edit.remove("autoNext");
      if (hadSkip) edit.putBoolean("skipButtons", oldSkip);
      else edit.remove("skipButtons");
      edit.commit();
      if (p.activity != null) p.main(p.activity::finish);
      if (token != null)
        new File(new File(c.getFilesDir(), "episode-sessions"), token + ".json").delete();
    }
  }
}
