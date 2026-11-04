package app.streamnest;

import android.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Approved HTTPS mirrors distribute signed, immutable catalog snapshots. */
final class CatalogSync {
  static final String[] MIRRORS = {
    "https://raw.githubusercontent.com/Shaurya609/StreamNest/catalog-data/",
    "https://cdn.jsdelivr.net/gh/Shaurya609/StreamNest@catalog-data/"
  };
  static Context context;
  static File directory;
  static MovieRepository movies;
  static boolean running, bundled;
  static long attempted;

  static synchronized void configure(Context c, File folder, MovieRepository repository) {
    context = c.getApplicationContext();
    directory = folder;
    movies = repository;
  }

  static synchronized void refresh(boolean force) {
    if (context == null || running || StreamService.engine != null && !StreamService.engine.closed)
      return;
    long now = System.currentTimeMillis();
    if (!force && now - attempted < 1800000) return;
    attempted = now;
    running = true;
    Thread task =
        new Thread(
            () -> {
              try {
                bundle();
                sync();
              } catch (Exception e) {
                android.util.Log.w(
                    "StreamNest", "Catalog refresh retained last working data: " + e.getMessage());
              } finally {
                synchronized (CatalogSync.class) {
                  running = false;
                }
              }
            },
            "catalog-mirror");
    task.setDaemon(true);
    task.setPriority(Thread.MIN_PRIORITY);
    task.start();
  }

  static String publicKey() throws Exception {
    try (InputStream in = context.getAssets().open("catalog-public-key.pem")) {
      return new String(Net.read(in, 10000), StandardCharsets.UTF_8);
    }
  }

  static void providers(JSONObject manifest) throws Exception {
    JSONObject object = manifest.optJSONObject("providers");
    if (object == null) return;
    JSONArray values = object.optJSONArray("movies");
    if (values == null) return;
    List<String> hosts = new ArrayList<>();
    for (int i = 0; i < values.length(); i++) hosts.add(values.getString(i));
    MovieProvider.configureBases(hosts);
  }

  static void bundle() throws Exception {
    if (bundled) return;
    for (String kind : new String[] {"shows", "animes", "latest"})
      try (InputStream in = context.getAssets().open("catalog-" + kind + "-v1.json")) {
        File stage = new File(directory, "bundle-" + kind + ".pending");
        Files.write(stage.toPath(), Net.read(in, 16000000));
        installFile(kind, stage, version(stage), false);
        stage.delete();
      } catch (FileNotFoundException ignored) {
      }
    File accepted = new File(directory, "accepted-manifest.json");
    if (accepted.isFile())
      try {
        providers(CatalogSignature.manifest(Files.readAllBytes(accepted.toPath()), publicKey()));
      } catch (Exception ignored) {
      }
    bundled = true;
  }

  static void sync() throws Exception {
    String key = publicKey();
    JSONObject manifest = null;
    byte[] envelope = null;
    Exception failure = null;
    for (String base : MIRRORS)
      try {
        byte[] bytes =
            Net.catalogBytes(
                base + "catalogs/v1/manifest.json",
                100000,
                System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8));
        manifest = CatalogSignature.manifest(bytes, key);
        envelope = bytes;
        break;
      } catch (Exception e) {
        failure = e;
      }
    if (manifest == null) throw new IOException("Catalog mirrors unavailable", failure);
    SharedPreferences prefs = context.getSharedPreferences("catalog_sync", 0);
    if (manifest.getLong("generatedAt") < prefs.getLong("acceptedAt", 0))
      throw new IOException("Older catalog manifest rejected");
    providers(manifest);
    if (manifest.getString("version").equals(prefs.getString("version", ""))) return;
    JSONObject files = manifest.getJSONObject("files");
    Map<String, File> stages = new LinkedHashMap<>();
    try {
      for (String kind : new String[] {"movies", "shows", "animes", "latest"}) {
        JSONObject entry = files.getJSONObject(kind);
        String path = entry.getString("path");
        if (!path.matches("catalogs/v1/data/" + kind + "-[0-9a-f]{16}\\.json"))
          throw new IOException("Unsafe catalog path");
        File stage = new File(directory, "mirror-" + kind + ".pending");
        boolean verified = false;
        for (String base : MIRRORS)
          try {
            byte[] bytes =
                Net.catalogBytes(
                    base + path,
                    16000000,
                    System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(12));
            CatalogSignature.verifyFile(kind, entry, bytes);
            CatalogJson.inspect(kind, entry, new ByteArrayInputStream(bytes));
            Files.write(stage.toPath(), bytes);
            verified = true;
            break;
          } catch (Exception e) {
            failure = e;
          }
        if (!verified)
          throw new IOException("Catalog retained until every snapshot file is verified", failure);
        stages.put(kind, stage);
      }
      for (Map.Entry<String, File> item : stages.entrySet())
        installFile(
            item.getKey(),
            item.getValue(),
            files.getJSONObject(item.getKey()).getLong("saved"),
            manifest.optBoolean("rollback"));
      File accepted = new File(directory, "accepted-manifest.json"),
          tmp = new File(directory, "accepted-manifest.tmp");
      Files.write(tmp.toPath(), envelope);
      Files.move(tmp.toPath(), accepted.toPath(), StandardCopyOption.REPLACE_EXISTING);
      prefs
          .edit()
          .putString("version", manifest.getString("version"))
          .putLong("acceptedAt", manifest.getLong("generatedAt"))
          .apply();
    } finally {
      for (File file : stages.values()) file.delete();
    }
  }

  static void installFile(String kind, File stage, long saved, boolean rollback) throws Exception {
    if (kind.equals("movies")) {
      try (InputStream in = new FileInputStream(stage)) {
        movies.importStream(in, saved);
      }
      return;
    }
    String name =
        kind.equals("shows")
            ? "series-index-v6.json"
            : kind.equals("animes") ? "anime-series-v8.json" : "anime-latest-v1.json";
    File target = new File(directory, name);
    if (saved <= 0 || !rollback && saved <= version(target)) return;
    File tmp = new File(directory, name + ".mirror.tmp");
    Files.copy(stage.toPath(), tmp.toPath(), StandardCopyOption.REPLACE_EXISTING);
    Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
    if (kind.equals("shows"))
      synchronized (SeriesIndex.class) {
        SeriesIndex.snapshot = null;
        SeriesIndex.refreshed = 0;
      }
    if (kind.equals("animes"))
      synchronized (AnimeCatalog.class) {
        AnimeCatalog.snapshot = null;
        AnimeCatalog.refreshed = 0;
      }
  }

  static long version(File file) {
    if (!file.isFile()) return 0;
    try (InputStream in = new FileInputStream(file)) {
      return version(in);
    } catch (Exception ignored) {
      return 0;
    }
  }

  static long version(InputStream in) {
    Pattern pattern = Pattern.compile("\"saved\"\\s*:\\s*([0-9]+)");
    try {
      byte[] buffer = new byte[4096];
      String tail = "";
      int n;
      while ((n = in.read(buffer)) != -1) {
        String text = tail + new String(buffer, 0, n, StandardCharsets.UTF_8);
        Matcher match = pattern.matcher(text);
        if (match.find()) return Long.parseLong(match.group(1));
        tail = text.substring(Math.max(0, text.length() - 64));
      }
    } catch (Exception ignored) {
    }
    return 0;
  }
}
