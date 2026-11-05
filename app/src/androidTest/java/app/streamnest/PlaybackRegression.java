package app.streamnest;

import android.app.*;
import android.content.*;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.*;
import android.view.*;
import java.io.File;
import java.util.*;
import org.json.*;
import org.videolan.libvlc.Media;

/** Device-only regression; fixture is supplied locally, never bundled in the app. */
public class PlaybackRegression extends Instrumentation {
  PlayerActivity activity;
  String watchSyncCode;
  boolean skipLive, catalog, subtitles, storage, resilience, episodes, appUpdates, watchSyncUi, resumeUi;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    watchSyncCode = args == null ? null : args.getString("watchSyncCode");
    catalog = args != null && "true".equals(args.getString("catalog"));
    subtitles = args != null && "true".equals(args.getString("subtitles"));
    storage = args != null && "true".equals(args.getString("storage"));
    resilience = args != null && "true".equals(args.getString("resilience"));
    episodes = args != null && "true".equals(args.getString("episodes"));
    appUpdates = args != null && "true".equals(args.getString("appUpdates"));
    watchSyncUi = args != null && "true".equals(args.getString("watchSyncUi"));
    resumeUi = args != null && "true".equals(args.getString("resumeUi"));
    skipLive = args != null && "true".equals(args.getString("skipLive"));
    start();
  }

  @Override
  public void onStart() {
    if (skipLive) {
      Bundle result = new Bundle();
      try {
        String url = "https://api.skipdb.tv/api/segments?imdb_id=tt10919420&season=1&episode=1&duration=3655&adjust=none";
        JSONObject doc = CatalogHttp.object(url, null);
        List<SkipSegments.Segment> rows = SkipSegments.skipdb(doc, "tt10919420", 1, 1, 3655000);
        if (rows.isEmpty()) throw new AssertionError("No exact-match live fallback segment");
        if (!SkipSegments.skipdb(doc, "tt10919420", 1, 2, 3655000).isEmpty())
          throw new AssertionError("Wrong episode accepted");
        result.putLong("creditsStartMs", rows.get(0).start);
        result.putLong("creditsEndMs", rows.get(0).end);
        result.putString("result", "PASS: live anonymous fallback parsed on installed Android; wrong episode rejected");
        finish(Activity.RESULT_OK, result);
      } catch (Throwable error) {
        result.putString("result", "FAIL: " + error);
        finish(Activity.RESULT_CANCELED, result);
      }
      return;
    }
    if (resumeUi) {
      Bundle result=new Bundle();
      try { finish(Activity.RESULT_OK, ResumeSelectionRegression.run(this)); }
      catch(Throwable error) {result.putString("result","FAIL: "+error);finish(Activity.RESULT_CANCELED,result);}
      return;
    }
    if (watchSyncUi) {
      Bundle result = new Bundle();
      try {
        finish(Activity.RESULT_OK, WatchSyncUiRegression.run(this));
      } catch (Throwable error) {
        result.putString("result", "FAIL: " + error);
        finish(Activity.RESULT_CANCELED, result);
      }
      return;
    }
    if (watchSyncCode != null) {
      Bundle result = new Bundle();
      try {
        result = WatchSyncRegression.run(this, watchSyncCode);
        finish(Activity.RESULT_OK, result);
      } catch (Throwable error) {
        result.putString("result", "FAIL: " + error);
        finish(Activity.RESULT_CANCELED, result);
      }
      return;
    }

    if (appUpdates) {
      Bundle result = new Bundle();
      AppUpdateActivity screen = null;
      try {
        screen =
            (AppUpdateActivity)
                startActivitySync(
                    new Intent(getTargetContext(), AppUpdateActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        long deadline = SystemClock.elapsedRealtime() + 30000;
        while (SystemClock.elapsedRealtime() < deadline
            && (!AppUpdateActivity.message.contains("up to date") || AppUpdateActivity.busy))
          SystemClock.sleep(100);
        if (!AppUpdateActivity.message.contains("up to date"))
          throw new AssertionError(AppUpdateActivity.message);
        File invalid = new File(getTargetContext().getFilesDir(), "invalid-update-test.apk");
        java.nio.file.Files.write(invalid.toPath(), new byte[] {1, 2, 3});
        try {
          AppUpdateActivity.verifyApk(getTargetContext(), invalid, 999);
          throw new AssertionError("Invalid APK accepted");
        } catch (java.io.IOException expected) {
        } finally {
          invalid.delete();
        }
        File own = new File(getTargetContext().getApplicationInfo().sourceDir);
        try {
          AppUpdateActivity.verifyApk(getTargetContext(), own, getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(), 0).getLongVersionCode());
          throw new AssertionError("Same-version APK accepted");
        } catch (java.io.IOException expected) {
        }
        File candidate = new File(getTargetContext().getFilesDir(), "app-updates/fixture.apk");
        File wrong = new File(getTargetContext().getFilesDir(), "app-updates/wrong.apk");
        if (!candidate.exists() || !wrong.exists())
          throw new AssertionError("Supply signed future-version fixtures first");
        long futureCode=getTargetContext().getPackageManager().getPackageArchiveInfo(candidate.getAbsolutePath(), 0).getLongVersionCode();
        AppUpdateActivity.verifyApk(getTargetContext(), candidate, futureCode);
        try {
          AppUpdateActivity.verifyApk(getTargetContext(), wrong, futureCode);
          throw new AssertionError("Wrong signing certificate accepted");
        } catch (java.io.IOException expected) {
        }
        Intent handoff = AppUpdateActivity.installerIntent(getTargetContext(), candidate);
        if (!"content".equals(handoff.getData().getScheme())
            || (handoff.getFlags() & Intent.FLAG_GRANT_READ_URI_PERMISSION) == 0)
          throw new AssertionError("Invalid installer grant");
        try (java.io.InputStream stream =
            getTargetContext().getContentResolver().openInputStream(handoff.getData())) {
          if (stream.read() < 0) throw new AssertionError("APK content URI empty");
        }
        candidate.delete();
        wrong.delete();
        result.putBoolean("installPermissionGranted", getTargetContext().getPackageManager().canRequestPackageInstalls());
        result.putString(
            "result",
            "PASS: signed feed read; valid future APK accepted; wrong certificate/invalid/non-newer"
                + " APKs rejected; installer content URI readable");
        finish(Activity.RESULT_OK, result);
      } catch (Throwable e) {
        result.putString("result", "FAIL: " + e);
        finish(Activity.RESULT_CANCELED, result);
      } finally {
        if (screen != null) {
          AppUpdateActivity close = screen;
          runOnMainSync(close::finish);
        }
      }
      return;
    }
    if (episodes) {
      Bundle result = new Bundle();
      try {
        result = EpisodeControlRegression.run(this);
        finish(Activity.RESULT_OK, result);
      } catch (Throwable e) {
        result.putString("result", "FAIL: " + e);
        finish(Activity.RESULT_CANCELED, result);
      }
      return;
    }
    if (resilience) {
      Bundle result = new Bundle();
      try {
        result = CatalogRegression.run(this);
        finish(Activity.RESULT_OK, result);
      } catch (Throwable e) {
        result.putString("result", "FAIL: " + e);
        finish(Activity.RESULT_CANCELED, result);
      }
      return;
    }
    if (subtitles) {
      subtitleProbe();
      return;
    }
    if (storage) {
      storageProbe();
      return;
    }
    if (catalog) {
      catalogProbe();
      return;
    }
    Bundle result = new Bundle();
    try {
      File fixture = new File(getTargetContext().getCacheDir(), "playback-regression.mp4");
      if (!fixture.isFile()) throw new AssertionError("Supply the local playback fixture first");
      Intent intent =
          new Intent(getTargetContext(), PlayerActivity.class)
              .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
              .putExtra("source", "magnet:?xt=urn:btih:0000000000000000000000000000000000000000")
              .putExtra("title", "Playback regression")
              .putExtra("subtitlesEnabled", false);
      activity = (PlayerActivity) startActivitySync(intent);
      main(
          () -> {
            activity.mediaSet = true;
            Media media = new Media(activity.vlc, Uri.fromFile(fixture));
            media.setHWDecoderEnabled(true, false);
            activity.player.setMedia(media);
            media.release();
            activity.player.play();
          });
      waitForFrame();
      main(() -> activity.player.setTime(10000));
      SystemClock.sleep(1200);
      assertFrame("initial");
      for (String segmentType : new String[] {"intro", "outro"}) {
        long start = segmentType.equals("intro") ? 5000 : 15000;
        long end = start + 5000;
        main(() -> {
          activity.segments = new ArrayList<>();
          activity.segments.add(new SkipSegments.Segment(segmentType, start, end));
          activity.player.setTime(start + 1000);
          activity.updateSegment(start + 1000);
          if (activity.skipSegment.getVisibility() != View.VISIBLE)
            throw new AssertionError("Missing " + segmentType + " control");
          activity.skipSegment.performClick();
        });
        SystemClock.sleep(700);
        if (activity.player.getTime() < end - 1000)
          throw new AssertionError(segmentType + " did not seek");
        assertFrame(segmentType + " seek");
      }
      main(() -> {
        activity.segments.clear();
        activity.updateSegment(activity.player.getTime());
      });

      main(() -> activity.subtitleMenu());
      SystemClock.sleep(500);
      // Close the actual subtitle overlay via Back, just as a remote/tablet does.
      sendKeyDownUpSync(KeyEvent.KEYCODE_BACK);
      SystemClock.sleep(1000);
      assertFrame("subtitle menu return");
      final long[] before = {0};
      main(() -> before[0] = activity.player.getTime());
      Activity settings =
          startActivitySync(
              new Intent(getTargetContext(), SubtitleSettingsActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      SystemClock.sleep(500);
      main(settings::finish);
      SystemClock.sleep(1500);
      assertFrame("account screen return");
      main(
          () -> {
            if (!activity.player.isPlaying() || activity.player.getTime() <= before[0])
              throw new AssertionError("Playback did not resume");
            activity.userPaused = true;
            activity.player.pause();
          });
      Activity pausedSettings =
          startActivitySync(
              new Intent(getTargetContext(), SubtitleSettingsActivity.class)
                  .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      SystemClock.sleep(300);
      main(pausedSettings::finish);
      SystemClock.sleep(700);
      main(
          () -> {
            if (activity.player.isPlaying()) throw new AssertionError("User pause was lost");
          });
      result.putString(
          "result",
          "PASS: decoded frames after subtitle menu and settings return; position advances; user"
              + " pause preserved");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable error) {
      result.putString("result", "FAIL: " + error);
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (activity != null) main(() -> activity.finish());
    }
  }

  void main(Runnable action) {
    Throwable[] error = {null};
    runOnMainSync(
        () -> {
          try {
            action.run();
          } catch (Throwable e) {
            error[0] = e;
          }
        });
    if (error[0] != null) throw new AssertionError(error[0]);
  }

  void waitForFrame() {
    for (int i = 0; i < 30; i++) {
      try {
        assertFrame("startup");
        return;
      } catch (Throwable ignored) {
        SystemClock.sleep(200);
      }
    }
    throw new AssertionError("Video did not render");
  }

  TextureView texture(View view) {
    if (view instanceof TextureView) return (TextureView) view;
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) {
        TextureView found = texture(group.getChildAt(i));
        if (found != null) return found;
      }
    }
    return null;
  }

  void assertFrame(String step) {
    main(
        () -> {
          TextureView view = texture(activity.video);
          if (view == null || !view.isAvailable())
            throw new AssertionError(step + ": no video surface");
          Bitmap image = view.getBitmap(64, 36);
          if (image == null) throw new AssertionError(step + ": no decoded frame");
          int bright = 0;
          for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
              int p = image.getPixel(x, y);
              if (((p >> 16) & 255) + ((p >> 8) & 255) + (p & 255) > 90) bright++;
            }
          image.recycle();
          if (bright < 50) throw new AssertionError(step + ": black video frame");
        });
  }

  void catalogProbe() {
    Bundle result = new Bundle();
    try {
      MovieIndex.configure(getTargetContext().getAssets(), getTargetContext().getCacheDir());
      List<JSONObject> movies = MovieIndex.pool();
      List<String> names = new ArrayList<>();
      for (JSONObject m : movies) if (MovieIndex.confirmedHindi(m)) names.add(m.optString("title"));
      result.putInt("confirmedHindiMovies", names.size());
      List<Catalog.Title> mixed = HindiCatalog.mixedMovies(1, "trending", "All", "All", -1);
      result.putInt("mixedMoviePage", mixed.size());
      result.putInt(
          "mixedHindiCount",
          (int) mixed.stream().filter(t -> t.raw.optString("language").equals("hi")).count());
      result.putString("examples", names.subList(0, Math.min(8, names.size())).toString());
      java.io.InputStream in = getTargetContext().getAssets().open("series-index-v6.json");
      List<JSONObject> series = SeriesIndex.read(in);
      List<Catalog.Title> hindi = SeriesIndex.select(series, 1, "", "trending", "All", "hi", -1);
      result.putInt("hindiSeriesFirstPage", hindi.size());
      result.putString("status", names.isEmpty() ? "No Hindi HD movie records returned" : "PASS");
      finish(Activity.RESULT_OK, result);
    } catch (Exception e) {
      result.putString("status", "Provider unavailable: " + e.getMessage());
      finish(Activity.RESULT_CANCELED, result);
    }
  }

  void subtitleProbe() {
    Bundle result = new Bundle();
    OrgSubtitles api = null;
    try {
      Map<String, String> values = new HashMap<>();
      api =
          new OrgSubtitles(
              new OrgSubtitles.Store() {
                public String get(String key) {
                  return values.getOrDefault(key, "");
                }

                public void put(String key, String value) {
                  values.put(key, value);
                }
              });
      api.ensureAnonymous(false);
      if (values.getOrDefault("orgToken", "").isEmpty())
        throw new AssertionError("Fresh anonymous session missing");
      values.put("orgAnonymousUntil", "0");
      List<SubtitleQuery.Result> percy =
          api.search(
              "tt12324366", 1, 1, "Percy Jackson · S1 E1", "en", "0123456789abcdef", 500000000);
      List<SubtitleQuery.Result> scorpion =
          api.search("tt33473834", 0, 0, "Scorpion", "en", "0123456789abcdef", 500000000);
      List<SubtitleQuery.Result> squid =
          api.search("tt10919420", 1, 1, "Squid Game", "en", "", 0);
      List<SubtitleQuery.Result> parasite =
          api.search("tt6751668", 0, 0, "Parasite", "en", "", 0);
      if (squid.isEmpty() || parasite.isEmpty())
        throw new AssertionError("Expected English Korean-title subtitle matches");
      result.putInt("squidEnglish", squid.size());
      result.putInt("parasiteEnglish", parasite.size());
      if (percy.isEmpty() || scorpion.isEmpty())
        throw new AssertionError("Expected English subtitle matches");
      File directory = new File(getTargetContext().getCacheDir(), "org-subtitle-regression");
      if (!directory.isDirectory() && !directory.mkdirs())
        throw new AssertionError("Cannot create subtitle test cache");
      File file = api.download(percy.get(0), directory);
      String text =
          new String(
              java.nio.file.Files.readAllBytes(file.toPath()),
              java.nio.charset.StandardCharsets.UTF_8);
      if (!text.contains("-->")) throw new AssertionError("Downloaded file is not timed subtitles");
      result.putInt("percyEnglish", percy.size());
      result.putInt("scorpionEnglish", scorpion.size());
      result.putLong("downloadBytes", file.length());
      file.delete();
      directory.delete();
      result.putString(
          "result",
          "PASS: independent IMDb fallback works despite an unmatched file hash; timed subtitle"
              + " download verified");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("result", "FAIL: " + e);
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      if (api != null)
        try {
          api.logout();
        } catch (Exception ignored) {
        }
    }
  }

  View textView(View root, String label) {
    if (root instanceof android.widget.TextView
        && label.contentEquals(((android.widget.TextView) root).getText())) return root;
    if (root instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) root;
      for (int i = 0; i < group.getChildCount(); i++) {
        View found = textView(group.getChildAt(i), label);
        if (found != null) return found;
      }
    }
    return null;
  }

  void storageProbe() {
    Bundle result = new Bundle();
    CacheFolderActivity picker = null;
    File child = null;
    android.content.SharedPreferences prefs = getTargetContext().getSharedPreferences("storage", 0);
    String previous = prefs.getString("torrentRoot", "");
    try {
      picker =
          (CacheFolderActivity)
              startActivitySync(
                  new Intent(getTargetContext(), CacheFolderActivity.class)
                      .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
      CacheFolderActivity selected = picker;
      CacheLocation.Volume root = CacheLocation.volumes(getTargetContext()).get(0);
      child = FolderPolicy.child(root.appFolder, "picker-regression");
      CacheLocation.writable(child);
      File target = child;
      main(
          () -> {
            selected.open(root, target, false);
            View use = textView(selected.getWindow().getDecorView(), "Use this folder");
            if (use == null) throw new AssertionError("Missing folder action");
            use.performClick();
          });
      if (!CacheLocation.current(getTargetContext())
          .getCanonicalFile()
          .equals(child.getCanonicalFile()))
        throw new AssertionError("Folder selection was not saved");
      if (CacheLocation.description(getTargetContext()).contains("/data/"))
        throw new AssertionError("Raw path exposed");
      result.putString(
          "result",
          "PASS: native folder selection saved writable app-storage subfolder with a friendly"
              + " label; prior selection restored");
      finish(Activity.RESULT_OK, result);
    } catch (Throwable e) {
      result.putString("result", "FAIL: " + e);
      finish(Activity.RESULT_CANCELED, result);
    } finally {
      prefs.edit().putString("torrentRoot", previous).commit();
      if (child != null) child.delete();
      if (picker != null) {
        CacheFolderActivity selected = picker;
        main(selected::finish);
      }
    }
  }
}
