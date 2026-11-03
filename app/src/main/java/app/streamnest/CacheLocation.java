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
    File valid = writable(f);
    c.getSharedPreferences("storage", 0).edit().putString("torrentRoot", valid.getPath()).apply();
  }
}
