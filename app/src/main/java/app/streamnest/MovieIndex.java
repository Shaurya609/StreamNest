package app.streamnest;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

final class MovieIndex {
  static List<JSONObject> snapshot;
  static long refreshed;
  static File disk;

  static synchronized void configure(File folder) {
    disk = new File(folder, "movies-index-v5.json");
    if (snapshot != null || !disk.isFile() || disk.length() > 4 * 1024 * 1024) return;
    try {
      JSONObject saved =
          new JSONObject(
              new String(
                  Files.readAllBytes(disk.toPath()), java.nio.charset.StandardCharsets.UTF_8));
      long age = System.currentTimeMillis() - saved.getLong("saved");
      if (age < 0 || age > 24 * 60 * 60 * 1000L) return;
      JSONArray a = saved.getJSONArray("movies");
      List<JSONObject> loaded = new ArrayList<>();
      for (int i = 0; i < a.length(); i++) loaded.add(a.getJSONObject(i));
      snapshot = loaded;
      refreshed = saved.getLong("saved");
    } catch (Exception ignored) {
    }
  }

  static void save() {
    if (disk == null) return;
    try {
      JSONObject saved =
          new JSONObject().put("saved", refreshed).put("movies", new JSONArray(snapshot));
      File tmp = new File(disk.getParentFile(), "movies-index-v5.tmp");
      Files.write(tmp.toPath(), saved.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      Files.move(tmp.toPath(), disk.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception ignored) {
    }
  }

  static boolean refreshing;

  static synchronized List<JSONObject> pool() throws Exception {
    if (snapshot != null) {
      if (System.currentTimeMillis() - refreshed >= 600000 && !refreshing) {
        refreshing = true;
        Thread refresh =
            new Thread(
                () -> {
                  try {
                    List<JSONObject> fresh = build();
                    synchronized (MovieIndex.class) {
                      snapshot = fresh;
                      refreshed = System.currentTimeMillis();
                      save();
                    }
                  } catch (Exception ignored) {
                  } finally {
                    synchronized (MovieIndex.class) {
                      refreshing = false;
                    }
                  }
                },
                "catalog-refresh");
        refresh.setDaemon(true);
        refresh.start();
      }
      return snapshot;
    }
    snapshot = build();
    refreshed = System.currentTimeMillis();
    save();
    return snapshot;
  }

  static List<JSONObject> build() throws Exception {
    LinkedHashMap<String, JSONObject> all = new LinkedHashMap<>();
    Exception failure = null;
    String[] sorts = {"date_added", "seeds", "download_count"};
    for (String sort : sorts)
      try {
        JSONArray movies = fetch(sort, "");
        for (int i = 0; i < movies.length(); i++) {
          JSONObject m = FreshMovies.normalize(movies.getJSONObject(i));
          String lang = m.optString("language");
          if (!lang.equals("en") && !lang.equals("hi")) continue;
          String id = m.optString("imdb_code");
          JSONObject old = all.get(id);
          if (old != null) m = old;
          if (sort.equals("download_count")) m.put("popularityRank", i + 1);
          all.put(id, m);
        }
      } catch (Exception e) {
        failure = e;
      }
    List<Catalog.Title> regional = new ArrayList<>();
    for (int p = 1; p <= 2; p++)
      try {
        regional.addAll(
            HindiCatalog.parse(
                new JSONObject(Net.text(HindiCatalog.PROVIDER + HindiCatalog.path("movies", p))),
                "movies"));
      } catch (Exception e) {
        failure = e;
      }
    try {
      for (Catalog.Title t : FreshMovies.available(regional)) all.putIfAbsent(t.id, t.raw);
    } catch (Exception e) {
      failure = e;
    }
    if (all.isEmpty()) {
      throw failure == null ? new Exception("Movie catalog unavailable") : failure;
    }
    return new ArrayList<>(all.values());
  }

  static JSONArray fetch(String sort, String q) throws Exception {
    JSONObject d =
        new JSONObject(
                Net.text(
                    FreshMovies.BASE
                        + "list_movies.json?limit=50&quality=1080p&order_by=desc&sort_by="
                        + sort
                        + "&query_term="
                        + Net.q(q)))
            .getJSONObject("data");
    JSONArray a = d.optJSONArray("movies");
    return a == null ? new JSONArray() : a;
  }

  static List<Catalog.Title> list(
      int page, String q, String sort, String genre, String quality, String language, int order)
      throws Exception {
    List<JSONObject> records = new ArrayList<>(pool());
    if (!q.isBlank()) {
      JSONArray a = fetch("date_added", q);
      for (int i = 0; i < a.length(); i++) records.add(FreshMovies.normalize(a.getJSONObject(i)));
    }
    return BrowseOrder.select(records, page, q, sort, genre, quality, language, order);
  }
}
