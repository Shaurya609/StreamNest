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
    button(box, "Projector / memory mode")
        .setOnClickListener(
            v ->
                new AlertDialog.Builder(this)
                    .setTitle("Memory mode")
                    .setItems(
                        new String[] {"Automatic", "Projector / low memory", "Standard"},
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
        "Projector mode uses smaller posters, fewer workers, fewer peers and shorter read-ahead."
            + " Choose 1080p H.264 for a 1 GB projector; 4K or HEVC may exceed its decoder"
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
            + f.getPath()
            + "\nFree space: "
            + android.text.format.Formatter.formatFileSize(this, f.getUsableSpace()));
    mode.setText(
        "Memory mode: "
            + getSharedPreferences("performance", 0).getString("mode", "auto")
            + " · "
            + (DeviceProfile.lite(this) ? "low memory active" : "standard active"));
  }

  void folders() {
    List<File> choices = CacheLocation.choices(this);
    List<String> labels = new ArrayList<>();
    for (int i = 0; i < choices.size(); i++)
      labels.add(
          (i == 0 ? "Internal app storage" : "App storage " + i) + "\n" + choices.get(i).getPath());
    labels.add("Custom writable folder…");
    new AlertDialog.Builder(this)
        .setTitle("Cache folder")
        .setItems(
            labels.toArray(new String[0]),
            (d, n) -> {
              if (n < choices.size()) save(choices.get(n));
              else {
                EditText path = new EditText(this);
                path.setSingleLine(true);
                path.setHint("Absolute folder path");
                new AlertDialog.Builder(this)
                    .setTitle("Custom cache folder")
                    .setMessage(
                        "Choose a folder this app can write to. Android may restrict shared or USB"
                            + " folders; the app-storage choices above do not need broad file"
                            + " access.")
                    .setView(path)
                    .setPositiveButton(
                        "Use folder",
                        (dialog, which) -> save(new File(path.getText().toString().trim())))
                    .setNegativeButton("Cancel", null)
                    .show();
              }
            })
        .show();
  }

  void save(File f) {
    try {
      CacheLocation.select(this, f);
      refresh();
      Toast.makeText(this, "Cache folder changed", Toast.LENGTH_SHORT).show();
    } catch (IOException e) {
      new AlertDialog.Builder(this)
          .setTitle("Folder unavailable")
          .setMessage(e.getMessage())
          .setPositiveButton("OK", null)
          .show();
    }
  }
}
