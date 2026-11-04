package app.streamnest;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.json.*;

final class MovieIndex {
  static List<JSONObject> snapshot;
  static long refreshed;
  static File disk;

  static synchronized void configure(android.content.res.AssetManager assets, File folder) {
    configure(folder);
    if (snapshot != null) return;
    try (InputStream in = assets.open("movies-hindi-v1.json")) {
      JSONObject doc =
          new JSONObject(
              new String(Net.read(in, 4000000), java.nio.charset.StandardCharsets.UTF_8));
      List<JSONObject> rows = new ArrayList<>();
      JSONArray movies = doc.getJSONArray("movies");
      for (int i = 0; i < movies.length(); i++) {
        JSONObject m = FreshMovies.normalize(movies.getJSONObject(i));
        if (confirmedHindi(m)) rows.add(m);
      }
      if (!rows.isEmpty()) {
        snapshot = rows;
        refreshed = doc.getLong("saved");
      }
    } catch (Exception ignored) {
    }
  }

  static synchronized void configure(File folder) {
    disk = new File(folder, "movies-index-v7.json");
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
      File tmp = new File(disk.getParentFile(), "movies-index-v7.tmp");
      Files.write(tmp.toPath(), saved.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      Files.move(tmp.toPath(), disk.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception ignored) {
    }
  }

  static boolean refreshing;

  static synchronized List<JSONObject> pool() throws Exception {
    if (snapshot != null) {
      if (System.currentTimeMillis() - refreshed >= 21600000 && !refreshing) {
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
    int responding = 0;
    String[] sorts = {"date_added", "seeds", "download_count"};
    for (String sort : sorts)
      try {
        JSONArray movies = fetch(sort, "");
        responding++;
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
    if (responding == 0 && failure != null) throw failure;
    if (snapshot != null)
      for (JSONObject m : snapshot)
        if (confirmedHindi(m)) all.putIfAbsent(m.optString("imdb_code"), m);
    List<Catalog.Title> regional = new ArrayList<>();
    for (int p = 1; p <= 6; p++) {
      try {
        for (Catalog.Title t :
            HindiCatalog.parse(
                new JSONObject(Net.text(HindiCatalog.PROVIDER + HindiCatalog.path("movies", p))),
                "movies")) if (!all.containsKey(t.id)) regional.add(t);
      } catch (Exception e) {
        failure = e;
      }
    }
    try {
      for (Catalog.Title t : FreshMovies.available(regional))
        if (confirmedHindi(t.raw)) all.putIfAbsent(t.id, t.raw);
    } catch (Exception e) {
      failure = e;
    }
    if (all.isEmpty()) {
      throw failure == null ? new Exception("Movie catalog unavailable") : failure;
    }
    return new ArrayList<>(all.values());
  }

  static boolean confirmedHindi(JSONObject m) {
    return m.optString("language").equals("hi")
        && m.optJSONArray("torrents") != null
        && m.optJSONArray("torrents").length() > 0;
  }

  static JSONArray fetch(String sort, String q) throws Exception {
    JSONObject d =
        MovieProvider.data(
            "list_movies.json?limit=50&quality=1080p&order_by=desc&sort_by="
                + sort
                + "&query_term="
                + Net.q(q));
    JSONArray a = d.optJSONArray("movies");
    return a == null ? new JSONArray() : a;
  }

  static final Map<String, List<JSONObject>> sessions = new LinkedHashMap<>();

  static synchronized List<JSONObject> records(String key, int page) throws Exception {
    List<JSONObject> rows = sessions.get(key);
    if (rows == null || page == 1) {
      rows = new ArrayList<>(pool());
      sessions.put(key, rows);
      while (sessions.size() > 4) sessions.remove(sessions.keySet().iterator().next());
    }
    return rows;
  }

  static List<Catalog.Title> list(
      int page, String q, String sort, String genre, String quality, String language, int order)
      throws Exception {
    List<JSONObject> records =
        new ArrayList<>(
            records(
                q + "|" + sort + "|" + genre + "|" + quality + "|" + language + "|" + order, page));
    if (!q.isBlank()) {
      JSONArray a = fetch("date_added", q);
      for (int i = 0; i < a.length(); i++) records.add(FreshMovies.normalize(a.getJSONObject(i)));
    }
    return BrowseOrder.select(records, page, q, sort, genre, quality, language, order);
  }
}
