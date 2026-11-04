package app.streamnest;

import android.content.Context;
import java.io.*;
import java.nio.file.*;

final class CatalogStorage {
  static File folder(Context c) {
    File folder = new File(c.getFilesDir(), "catalogs");
    folder.mkdirs();
    for (String name :
        new String[] {"series-index-v6.json", "anime-series-v8.json", "movies-index-v7.json"}) {
      File target = new File(folder, name), old = new File(c.getCacheDir(), name);
      if (!target.exists() && old.isFile() && old.length() < 16000000)
        try {
          Files.copy(old.toPath(), target.toPath());
        } catch (IOException ignored) {
        }
    }
    return folder;
  }
}
