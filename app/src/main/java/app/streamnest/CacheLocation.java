package app.streamnest;

import android.content.*;
import java.io.*;
import java.util.*;

final class CacheLocation {
  static File current(Context c) {
    String p = c.getSharedPreferences("storage", 0).getString("torrentRoot", "");
    return p.isEmpty() ? new File(c.getFilesDir(), "torrents") : new File(p);
  }

  static List<File> choices(Context c) {
    List<File> out = new ArrayList<>();
    out.add(new File(c.getFilesDir(), "torrents"));
    for (File dir : c.getExternalFilesDirs(null))
      if (dir != null) {
        File f = new File(dir, "torrents");
        if (!out.contains(f)) out.add(f);
      }
    return out;
  }

  static final class Volume {
    final String label;
    final File root, appFolder;
    final boolean privateStorage;

    Volume(String label, File root, File appFolder, boolean privateStorage) {
      this.label = label;
      this.root = root;
      this.appFolder = appFolder;
      this.privateStorage = privateStorage;
    }
  }

  static List<Volume> volumes(Context c) {
    List<Volume> out = new ArrayList<>();
    File internal = new File(c.getFilesDir(), "torrents");
    out.add(new Volume("Internal app storage", internal, internal, true));
    android.os.storage.StorageManager manager =
        c.getSystemService(android.os.storage.StorageManager.class);
    if (android.os.Build.VERSION.SDK_INT >= 30) {
      for (android.os.storage.StorageVolume volume : manager.getStorageVolumes()) {
        File root = volume.getDirectory();
        if (root == null || !android.os.Environment.MEDIA_MOUNTED.equals(volume.getState()))
          continue;
        File app = null;
        for (File dir : c.getExternalFilesDirs(null))
          try {
            if (dir != null && FolderPolicy.inside(root, dir)) {
              app = new File(dir, "torrents");
              break;
            }
          } catch (IOException ignored) {
          }
        if (app == null)
          app = new File(root, "Android/data/" + c.getPackageName() + "/files/torrents");
        String label = volume.isPrimary() ? "Device storage" : volume.getDescription(c);
        out.add(new Volume(label, root, app, false));
      }
    } else {
      int n = 0;
      for (File dir : c.getExternalFilesDirs(null))
        if (dir != null) {
          File app = new File(dir, "torrents");
          out.add(new Volume(n++ == 0 ? "Device storage" : "Removable storage", app, app, true));
        }
    }
    return out;
  }

  static String description(Context c) {
    File selected = current(c);
    for (Volume volume : volumes(c))
      try {
        if (FolderPolicy.inside(volume.appFolder, selected)) {
          String relative =
              selected.getCanonicalPath().substring(volume.appFolder.getCanonicalPath().length());
          return volume.label + " · StreamNest cache" + relative.replace(File.separator, " / ");
        }
        if (FolderPolicy.inside(volume.root, selected)) {
          String relative =
              selected.getCanonicalPath().substring(volume.root.getCanonicalPath().length());
          return volume.label + relative.replace(File.separator, " / ");
        }
      } catch (IOException ignored) {
      }
    return "Custom storage · " + selected.getName();
  }

  static long freeSpace(File folder) {
    File existing = folder;
    while (!existing.exists() && existing.getParentFile() != null)
      existing = existing.getParentFile();
    return existing.getUsableSpace();
  }

  static File writable(File selected) throws IOException {
    if (!selected.isAbsolute()) throw new IOException("Enter an absolute folder path");
    File f = selected.getCanonicalFile();
    if (!f.isDirectory() && !f.mkdirs()) throw new IOException("Cannot create this folder");
    File probe = File.createTempFile("streamnest-check-", ".tmp", f);
    try (FileOutputStream stream = new FileOutputStream(probe)) {
      stream.write(0);
    } finally {
      if (!probe.delete()) probe.deleteOnExit();
    }
    return f;
  }

  static void select(Context c, File f) throws IOException {
    if (StreamService.engine != null && !StreamService.engine.closed)
      throw new IOException("Stop playback before changing the cache folder");
    File valid;
    try{valid=writable(f);}catch(IOException e){Diagnostics.failure("cache.failed","storage","cache",e);throw e;}
    Diagnostics.event("cache.changed");
    c.getSharedPreferences("storage", 0).edit().putString("torrentRoot", valid.getPath()).apply();
  }
}
