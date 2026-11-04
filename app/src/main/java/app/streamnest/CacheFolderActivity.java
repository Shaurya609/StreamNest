package app.streamnest;

import android.app.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Native directory selection. The torrent engine needs writable file paths, not document URIs. */
public class CacheFolderActivity extends Activity {
  final ExecutorService worker = Executors.newSingleThreadExecutor();
  final Handler ui = new Handler(Looper.getMainLooper());
  LinearLayout content;
  TextView message;
  CacheLocation.Volume volume, pending;
  File folder;
  boolean shared;
  int generation;

  @Override
  public void onCreate(Bundle state) {
    super.onCreate(state);
    if (Build.VERSION.SDK_INT >= 33)
      getOnBackInvokedDispatcher()
          .registerOnBackInvokedCallback(
              android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::back);
    storageList();
  }

  LinearLayout screen(String title) {
    generation++;
    LinearLayout box = new LinearLayout(this);
    box.setOrientation(LinearLayout.VERTICAL);
    box.setPadding(28, 20, 28, 20);
    box.setBackgroundColor(0xff17181b);
    ScrollView scroll = new ScrollView(this);
    scroll.addView(box);
    setContentView(scroll);
    text(box, title, 24);
    return box;
  }

  TextView text(LinearLayout box, String label, int size) {
    TextView t = new TextView(this);
    t.setText(label);
    t.setTextColor(-1);
    t.setTextSize(size);
    t.setPadding(0, 12, 0, 12);
    box.addView(t);
    return t;
  }

  Button button(LinearLayout box, String label, Runnable action) {
    Button b = new Button(this);
    b.setText(label);
    b.setAllCaps(false);
    box.addView(b);
    b.setOnClickListener(v -> action.run());
    return b;
  }

  void storageList() {
    volume = null;
    folder = null;
    pending = null;
    shared = false;
    LinearLayout box = screen("Choose cache storage");
    text(box, "Current: " + CacheLocation.description(this), 15);
    text(
        box,
        "Choose internal storage, an SD card or a connected USB drive. Only StreamNest's video"
            + " cache is placed here.",
        15);
    for (CacheLocation.Volume item : CacheLocation.volumes(this))
      button(
          box,
          item.label
              + "\n"
              + android.text.format.Formatter.formatFileSize(
                  this, CacheLocation.freeSpace(item.root))
              + " free",
          () -> choose(item));
    message =
        text(
            box,
            "Removable storage appears here when Android mounts it. App storage works without"
                + " additional file access.",
            14);
    button(box, "Refresh storage list", this::storageList);
    button(box, "Cancel", this::finish);
  }

  boolean fileAccess() {
    return Build.VERSION.SDK_INT >= 30 && Environment.isExternalStorageManager();
  }

  void choose(CacheLocation.Volume selected) {
    if (selected.privateStorage) {
      open(selected, selected.appFolder, false);
      return;
    }
    new AlertDialog.Builder(this)
        .setTitle(selected.label)
        .setItems(
            new String[] {
              "Use StreamNest app folder (recommended)", "Choose a folder on this storage"
            },
            (d, n) -> {
              if (n == 0) save(selected.appFolder);
              else if (fileAccess()) open(selected, selected.root, true);
              else requestAccess(selected);
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void requestAccess(CacheLocation.Volume selected) {
    new AlertDialog.Builder(this)
        .setTitle("Choose shared folders")
        .setMessage(
            "Android requires Files access for the torrent player to write outside StreamNest's app"
                + " folder. Enable it in Android settings, then return here. You can use the"
                + " recommended app folder without this permission.")
        .setPositiveButton(
            "Open Android settings",
            (d, n) -> {
              pending = selected;
              try {
                startActivity(
                    new Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.parse("package:" + getPackageName())));
              } catch (ActivityNotFoundException e) {
                try {
                  startActivity(new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION));
                } catch (ActivityNotFoundException missing) {
                  pending = null;
                  error(
                      "This device does not provide shared-folder access. Use StreamNest's app"
                          + " folder on the selected storage.");
                }
              }
            })
        .setNegativeButton("Use app folder", (d, n) -> save(selected.appFolder))
        .show();
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (pending != null && fileAccess()) {
      CacheLocation.Volume selected = pending;
      pending = null;
      open(selected, selected.root, true);
    }
  }

  void open(CacheLocation.Volume selected, File current, boolean sharedFolder) {
    pending = null;
    volume = selected;
    folder = current;
    shared = sharedFolder;
    try {
      File root = shared ? volume.root : volume.appFolder;
      if (!FolderPolicy.inside(root, current))
        throw new IOException("Folder is outside the selected storage");
      if (!shared && !current.exists()) CacheLocation.writable(current);
    } catch (IOException e) {
      error("This storage is unavailable. Reconnect it or choose another storage.");
      return;
    }
    LinearLayout box = screen("Choose cache folder");
    String relative = "";
    try {
      relative =
          current
              .getCanonicalPath()
              .substring((shared ? volume.root : volume.appFolder).getCanonicalPath().length());
    } catch (IOException ignored) {
    }
    text(
        box,
        volume.label + (relative.isEmpty() ? " · Root" : relative.replace(File.separator, " / ")),
        17);
    text(
        box,
        shared
            ? "StreamNest will create its own cache folder here. Other files are kept."
            : "Select the app-storage root or a subfolder.",
        14);
    button(
        box, "Use this folder", () -> save(shared ? new File(folder, "StreamNest Cache") : folder));
    button(box, "New folder", this::newFolder);
    button(box, "Up / change storage", this::back);
    content = new LinearLayout(this);
    content.setOrientation(LinearLayout.VERTICAL);
    box.addView(content);
    TextView loading = text(content, "Loading folders...", 15);
    final int request = generation;
    final File selectedFolder = folder;
    final File boundary = shared ? volume.root : volume.appFolder;
    worker.execute(
        () -> {
          try {
            File[] children = selectedFolder.listFiles(f -> f.isDirectory() && !f.isHidden());
            if (children == null)
              throw new IOException(
                  "Android did not allow this folder to be opened. Choose another folder or use"
                      + " StreamNest's app folder.");
            Arrays.sort(
                children, Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
            List<File> allowed = new ArrayList<>();
            File root = boundary;
            for (File child : children) if (FolderPolicy.inside(root, child)) allowed.add(child);
            ui.post(
                () -> {
                  if (isDestroyed() || request != generation) return;
                  renderFolders(allowed, 0);
                });
          } catch (IOException e) {
            ui.post(
                () -> {
                  if (!isDestroyed() && request == generation) loading.setText(e.getMessage());
                });
          }
        });
  }

  void renderFolders(List<File> entries, int start) {
    content.removeAllViews();
    int count = DeviceProfile.lite(this) ? 24 : 64, end = Math.min(entries.size(), start + count);
    if (entries.isEmpty())
      text(content, "No subfolders. You can use this folder or create one.", 15);
    if (start > 0)
      button(content, "Previous folders", () -> renderFolders(entries, Math.max(0, start - count)));
    for (File child : entries.subList(start, end))
      button(content, "Folder: " + child.getName(), () -> open(volume, child, shared));
    if (end < entries.size()) button(content, "More folders", () -> renderFolders(entries, end));
  }

  void newFolder() {
    EditText name = new EditText(this);
    name.setSingleLine(true);
    name.setHint("Folder name");
    new AlertDialog.Builder(this)
        .setTitle("New folder")
        .setView(name)
        .setPositiveButton(
            "Create",
            (d, n) -> {
              try {
                File next = FolderPolicy.child(folder, name.getText().toString());
                CacheLocation.writable(next);
                open(volume, next, shared);
              } catch (IOException e) {
                error(e.getMessage());
              }
            })
        .setNegativeButton("Cancel", null)
        .show();
  }

  void save(File chosen) {
    try {
      if (shared && folder != null && !FolderPolicy.inside(folder, chosen))
        throw new IOException("Choose a folder on the selected storage");
      CacheLocation.select(this, chosen);
      Toast.makeText(this, "Cache folder changed", Toast.LENGTH_SHORT).show();
      finish();
    } catch (IOException e) {
      error(
          StreamService.engine != null && !StreamService.engine.closed
              ? "Stop playback before changing the cache folder."
              : "Android could not write to this folder. Choose another folder, reconnect the drive"
                  + " or use StreamNest's app folder.");
    }
  }

  void error(String detail) {
    new AlertDialog.Builder(this)
        .setTitle("Folder unavailable")
        .setMessage(detail)
        .setPositiveButton("OK", null)
        .show();
  }

  void back() {
    if (folder == null) {
      finish();
      return;
    }
    File root = shared ? volume.root : volume.appFolder;
    try {
      if (folder.getCanonicalFile().equals(root.getCanonicalFile())) storageList();
      else open(volume, folder.getParentFile(), shared);
    } catch (IOException e) {
      storageList();
    }
  }

  @Override
  public void onBackPressed() {
    back();
  }

  @Override
  protected void onDestroy() {
    worker.shutdownNow();
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
