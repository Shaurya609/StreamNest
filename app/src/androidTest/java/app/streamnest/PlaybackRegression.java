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
  boolean catalog;

  @Override
  public void onCreate(Bundle args) {
    super.onCreate(args);
    catalog = args != null && "true".equals(args.getString("catalog"));
    start();
  }

  @Override
  public void onStart() {
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
}
