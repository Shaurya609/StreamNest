package app.streamnest;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

public class PlaybackSettingsActivity extends Activity {

  TextView path, mode;

  protected void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout box = new LinearLayout(this);
    box.setPadding(28, 24, 28, 24);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setBackgroundColor(0xff17181b);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(box);
    setContentView(scroll);
    label(box, "Playback & storage", 25);
    Switch autoNext = new Switch(this);
    autoNext.setText("Play next episode automatically");
    autoNext.setTextColor(Color.WHITE);
    autoNext.setChecked(getSharedPreferences("playback", 0).getBoolean("autoNext", true));
    autoNext.setOnCheckedChangeListener(
        (v, on) -> getSharedPreferences("playback", 0).edit().putBoolean("autoNext", on).apply());
    box.addView(autoNext);
    CheckBox preload = new CheckBox(this);
    preload.setText("Pre-load next episode");preload.setTextColor(Color.WHITE);
    preload.setChecked(EpisodePreload.enabled(this));
    preload.setOnCheckedChangeListener((v,on)->{getSharedPreferences("playback",0).edit().putBoolean("preloadNext",on).apply();if(!on)EpisodePreload.cancel();});
    box.addView(preload);
    label(box,"Starts after the current episode is fully downloaded. Uses additional storage and bandwidth. Previous episode data is removed only after the next video starts playing.",14);
    Switch skip = new Switch(this);
    skip.setText("Show intro and credits skip buttons");
    skip.setTextColor(Color.WHITE);
    skip.setChecked(getSharedPreferences("playback", 0).getBoolean("skipButtons", true));
    skip.setOnCheckedChangeListener(
        (v, on) ->
            getSharedPreferences("playback", 0).edit().putBoolean("skipButtons", on).apply());
    box.addView(skip);
    Switch autoSkip = new Switch(this);
    autoSkip.setText("Automatically skip intros and credits after five seconds");
    autoSkip.setTextColor(Color.WHITE);
    autoSkip.setChecked(getSharedPreferences("playback", 0).getBoolean("autoSkip", true));
    autoSkip.setOnCheckedChangeListener((v, on) ->
        getSharedPreferences("playback", 0).edit().putBoolean("autoSkip", on).apply());
    box.addView(autoSkip);
    label(
        box,
        "Skip buttons use community timestamps from IntroDB, SkipDB (ODbL 1.0 + reciprocity) and AniSkip when available. Coverage"
            + " and release timings vary. Auto-skip can be disabled in these settings; scenes after"
            + " credits are preserved when the timestamps identify them.",
        14);
    path = label(box, "", 15);
    Button folder = button(box, "Change cache folder");
    folder.setOnClickListener(v -> folders());
    label(
        box,
        "Video cache is cleared when you exit StreamNest. Settings and accounts are kept. New"
            + " playback uses the chosen folder. A removable drive must stay connected during"
            + " playback.",
        14);
    mode = label(box, "", 15);
    button(box, "Low Memory mode")
        .setOnClickListener(
            v ->
                new AlertDialog.Builder(this)
                    .setTitle("Low Memory mode")
                    .setItems(
                        new String[] {"Automatic", "Low Memory mode", "Standard"},
                        (d, n) -> {
                          getSharedPreferences("performance", 0)
                              .edit()
                              .putString("mode", new String[] {"auto", "lite", "standard"}[n])
                              .apply();
                          refresh();
                        })
                    .show());
    label(
        box,
        "Low Memory mode uses smaller posters, fewer workers, fewer peers and shorter read-ahead."
            + " Choose 1080p H.264 for a 1 GB device; 4K or HEVC may exceed its decoder"
            + " capability. Restart playback after changing mode.",
        14);
    button(box, "Done").setOnClickListener(v -> finish());
    refresh();
  }

  TextView label(LinearLayout p, String s, int size) {
    TextView t = new TextView(this);
    t.setText(s);
    t.setTextSize(size);
    t.setTextColor(Color.WHITE);
    t.setPadding(0, 12, 0, 12);
    p.addView(t);
    return t;
  }

  Button button(LinearLayout p, String s) {
    Button b = new Button(this);
    b.setText(s);
    p.addView(b);
    return b;
  }

  void refresh() {
    File f = CacheLocation.current(this);
    path.setText(
        "Cache folder\n"
            + CacheLocation.description(this)
            + "\nFree space: "
            + android.text.format.Formatter.formatFileSize(this, CacheLocation.freeSpace(f)));
    mode.setText(
        "Low Memory mode: "
            + modeLabel()
            + " · "
            + (DeviceProfile.lite(this) ? "low memory active" : "standard active"));
  }

  String modeLabel() {
    String setting = getSharedPreferences("performance", 0).getString("mode", "auto");
    return setting.equals("auto") ? "Automatic" : setting.equals("lite") ? "On" : "Off";
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (path != null) refresh();
  }

  void folders() {
    startActivity(new Intent(this, CacheFolderActivity.class));
  }
}
