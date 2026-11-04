package app.streamnest;

import java.util.*;
import org.json.*;

final class SeriesIndex {
  static List<JSONObject> snapshot;
  static long refreshed;

  static long popularity(JSONObject m) {
    try {
      return Long.parseLong(m.optString("popularity", "0").split("-")[0]);
    } catch (Exception e) {
      return 0;
    }
  }

  static Comparator<JSONObject> compare(String sort) {
    switch (sort) {
      case "title":
        return Comparator.comparing(m -> m.optString("title").toLowerCase(Locale.ROOT));
      case "year":
        return Comparator.<JSONObject>comparingInt(m -> m.optInt("year")).reversed();
      case "rating":
        return Comparator.<JSONObject>comparingDouble(m -> m.optDouble("rating", 0)).reversed();
      case "popularity":
        return Comparator.comparingLong(SeriesIndex::popularity).reversed();
      case "last added":
        return Comparator.comparingInt(m -> m.optInt("latestRank", Integer.MAX_VALUE));
      default:
        return Comparator.comparingInt(m -> m.optInt("trendRank", Integer.MAX_VALUE));
    }
  }

  static JSONArray fetch(String base, String sort, int page, String query) throws Exception {
    JSONObject o =
        CatalogHttp.object(
            base.replaceAll("/+$", "")
                + "/shows?"
                + Catalog.compat()
                + "&short=1&page="
                + page
                + "&sort="
                + sort
                + (query.isBlank() ? "" : "&keywords=" + Net.q(query)),
            "MovieList");
    JSONArray a = o.optJSONArray("MovieList");
    return a == null ? new JSONArray() : a;
  }

  static android.content.res.AssetManager assets;
  static java.io.File disk;
  static boolean refreshing;
  static final Map<String, List<JSONObject>> sessions = new LinkedHashMap<>();

  static void configure(android.content.res.AssetManager a, java.io.File cache) {
    assets = a;
    disk = new java.io.File(cache, "series-index-v6.json");
  }

  static JSONObject compact(JSONObject raw) throws Exception {
    JSONObject m = new JSONObject();
    for (String k :
        new String[] {
          "imdb",
          "title",
          "year",
          "rating",
          "popularity",
          "poster_big",
          "genres",
          "runtime",
          "trendRank",
          "latestRank",
          "language",
          "regionalCatalog",
          "metadataOnly",
          "metadataSource"
        }) if (raw.has(k)) m.put(k, raw.get(k));
    String description = raw.optString("description");
    m.put("description", description.substring(0, Math.min(1200, description.length())));
    return m;
  }

  static List<JSONObject> read(java.io.InputStream stream) throws Exception {
    byte[] bytes;
    try (java.io.InputStream in = stream) {
      bytes = Net.read(in, 16000000);
    }
    JSONObject o = new JSONObject(new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
    JSONArray a = o.getJSONArray("series");
    List<JSONObject> rows = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) rows.add(a.getJSONObject(i));
    refreshed = o.getLong("saved");
    return rows;
  }

  static synchronized List<JSONObject> pool(String base) throws Exception {
    if (snapshot == null) {
      if (disk != null && disk.isFile() && disk.length() < 16000000)
        try {
          snapshot = read(new java.io.FileInputStream(disk));
        } catch (Exception ignored) {
        }
      if (snapshot == null && assets != null) snapshot = read(assets.open("series-index-v6.json"));
      if (snapshot == null) snapshot = buildFull(base);
    }
    if (System.currentTimeMillis() - refreshed > 21600000 && !refreshing) {
      refreshing = true;
      Thread task =
          new Thread(
              () -> {
                try {
                  List<JSONObject> rows = buildFull(base);
                  synchronized (SeriesIndex.class) {
                    snapshot = rows;
                    refreshed = System.currentTimeMillis();
                  }
                  if (disk != null) {
                    JSONObject doc =
                        new JSONObject().put("saved", refreshed).put("series", new JSONArray(rows));
                    java.io.File tmp =
                        new java.io.File(disk.getParentFile(), "series-index-v6.tmp");
                    java.nio.file.Files.write(
                        tmp.toPath(),
                        doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    java.nio.file.Files.move(
                        tmp.toPath(),
                        disk.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                  }
                } catch (Exception ignored) {
                } finally {
                  synchronized (SeriesIndex.class) {
                    refreshing = false;
                  }
                }
              },
              "series-index-refresh");
      task.setDaemon(true);
      task.start();
    }
    CatalogStatus.cached();
    return snapshot;
  }

  static List<JSONObject> buildFull(String base) throws Exception {
    Map<String, JSONObject> all = new LinkedHashMap<>();
    synchronized (SeriesIndex.class) {
      if (snapshot != null)
        for (JSONObject row : snapshot)
          all.put(row.optString("imdb"), new JSONObject(row.toString()));
    }
    for (String sort : new String[] {"seeds", "dateadded"}) {
      Set<String> seen = new HashSet<>();
      for (int p = 1; ; p++) {
        JSONArray rows = fetch(base, sort, p, "");
        int added = 0;
        for (int i = 0; i < rows.length(); i++) {
          JSONObject raw = rows.getJSONObject(i);
          String id = raw.optString("imdb");
          if (!id.matches("tt[0-9]+") || !seen.add(id)) continue;
          added++;
          JSONObject m = all.get(id);
          if (m == null) {
            m = compact(raw).put("language", "en");
            all.put(id, m);
          }
          m.put(sort.equals("seeds") ? "trendRank" : "latestRank", (p - 1) * 75 + i + 1);
        }
        if (rows.length() == 0 || added == 0) break;
      }
    }
    Set<String> regional = new HashSet<>();
    for (int p = 1; ; p++) {
      List<Catalog.Title> rows =
          HindiCatalog.parse(
              new JSONObject(Net.text(HindiCatalog.PROVIDER + HindiCatalog.path("shows", p))),
              "shows");
      int added = 0;
      for (Catalog.Title t : rows) {
        if (!t.id.matches("tt[0-9]+") || !regional.add(t.id)) continue;
        added++;
        if (all.containsKey(t.id)) all.get(t.id).put("language", "hi");
        else
          all.put(
              t.id,
              t.raw
                  .put("language", "hi")
                  .put("trendRank", 10000 + regional.size())
                  .put("latestRank", 10000 + regional.size()));
      }
      if (rows.isEmpty() || added == 0) break;
    }
    if (all.isEmpty()) throw new Exception("Series catalog unavailable");
    return new ArrayList<>(all.values());
  }

  static List<JSONObject> regionalRanks(List<JSONObject> records) throws Exception {
    List<JSONObject> out = new ArrayList<>();
    int regional = 0;
    for (JSONObject row : records) {
      if (row.optString("language").equals("hi")
          && row.optInt("trendRank", Integer.MAX_VALUE) >= 10000
          && row.optInt("trendRank", Integer.MAX_VALUE) < 1000000) {
        JSONObject copy = new JSONObject(row.toString());
        int rank = ++regional * 4;
        copy.put("trendRank", rank).put("latestRank", rank);
        out.add(copy);
      } else out.add(row);
    }
    return out;
  }

  static List<Catalog.Title> select(
      List<JSONObject> records,
      int page,
      String q,
      String sort,
      String genre,
      String language,
      int order)
      throws Exception {
    records = regionalRanks(records);
    List<JSONObject> a = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JSONObject m : records) {
      String id = m.optString("imdb");
      if (!seen.add(id)
          || (!language.equals("all") && !m.optString("language", "en").equals(language))) continue;
      if (!q.isBlank()
          && !m.optString("title").toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))
          && !id.equals(q)) continue;
      if (!genre.equals("All")) {
        JSONArray g = m.optJSONArray("genres");
        boolean match = false;
        if (g != null)
          for (int i = 0; i < g.length(); i++)
            if (BrowseOrder.genreKey(genre).equals(BrowseOrder.genreKey(g.optString(i))))
              match = true;
        if (!match) continue;
      }
      a.add(m);
    }
    Comparator<JSONObject> ordering = compare(sort).thenComparing(m -> m.optString("imdb"));
    if (order != (sort.equals("title") ? 1 : -1)) ordering = ordering.reversed();
    if (sort.equals("rating"))
      ordering =
          Comparator.<JSONObject>comparingInt(m -> m.optDouble("rating", 0) > 0 ? 0 : 1)
              .thenComparing(ordering);
    a.sort(ordering);
    int start = Math.min(a.size(), Math.max(0, page - 1) * 24),
        end = Math.min(a.size(), start + 24);
    List<Catalog.Title> out = new ArrayList<>();
    for (JSONObject m : a.subList(start, end)) out.add(Catalog.parse(m, "shows"));
    return out;
  }

  static final java.util.concurrent.ThreadPoolExecutor searchWorker =
      new java.util.concurrent.ThreadPoolExecutor(
          1,
          1,
          30,
          java.util.concurrent.TimeUnit.SECONDS,
          new java.util.concurrent.ArrayBlockingQueue<>(2),
          r -> {
            Thread t = new Thread(r, "series-search-refresh");
            t.setDaemon(true);
            return t;
          },
          new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());
  static final Map<String, Long> searchAttempts = new java.util.concurrent.ConcurrentHashMap<>();

  static List<JSONObject> discover(String base, String query) {
    Map<String, JSONObject> result = new LinkedHashMap<>();
    try {
      JSONArray found = fetch(base, "seeds", 1, query);
      for (int i = 0; i < found.length(); i++) {
        JSONObject row = compact(found.getJSONObject(i)).put("language", "en");
        if (row.optString("imdb").matches("tt[0-9]+")) result.put(row.getString("imdb"), row);
      }
    } catch (Exception e) {
      CatalogStatus.cached();
    }
    try {
      for (JSONObject meta : SeriesDiscovery.search(query)) {
        String id = meta.getString("imdb");
        JSONObject row = result.get(id);
        if (row == null) result.put(id, meta);
        else row.put("language", meta.getString("language"));
      }
    } catch (Exception ignored) {
    }
    return new ArrayList<>(result.values());
  }

  static void refreshSearch(String base, String query) {
    String key = base + "|" + query;
    long now = System.currentTimeMillis();
    Long previous = searchAttempts.get(key);
    if (previous != null && now - previous < 300000) return;
    if (searchAttempts.size() > 32) searchAttempts.clear();
    searchAttempts.put(key, now);
    try {
      searchWorker.execute(
          () -> {
            try {
              List<JSONObject> found = discover(base, query);
              boolean changed = false;
              synchronized (SeriesIndex.class) {
                if (snapshot == null) return;
                Set<String> ids = new HashSet<>();
                for (JSONObject row : snapshot) ids.add(row.optString("imdb"));
                List<JSONObject> merged = new ArrayList<>(snapshot);
                for (JSONObject row : found)
                  if (ids.add(row.optString("imdb"))) {
                    merged.add(row);
                    changed = true;
                  }
                if (changed) {
                  snapshot = merged;
                  if (disk != null) {
                    java.io.File tmp = new java.io.File(disk.getParentFile(), "series-search.tmp");
                    java.nio.file.Files.write(
                        tmp.toPath(),
                        new JSONObject()
                            .put("saved", refreshed)
                            .put("series", new JSONArray(merged))
                            .toString()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
                    java.nio.file.Files.move(
                        tmp.toPath(),
                        disk.toPath(),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                  }
                }
              }
              if (changed) CatalogUpdates.notify("series-search:" + query);
            } catch (Exception ignored) {
            }
          });
    } catch (java.util.concurrent.RejectedExecutionException e) {
      searchAttempts.remove(key);
    }
  }

  static List<Catalog.Title> list(
      String base, int page, String q, String sort, String genre, String language, int order)
      throws Exception {
    String sessionKey = base + "|" + q + "|" + sort + "|" + genre + "|" + language + "|" + order;
    List<JSONObject> a;
    synchronized (SeriesIndex.class) {
      a = sessions.get(sessionKey);
    }
    if (a == null || page == 1) {
      a = new ArrayList<>(pool(base));
      if (!q.isBlank()) {
        if (!select(a, 1, q, sort, genre, language, order).isEmpty()) {
          CatalogStatus.cached();
          refreshSearch(base, q);
        } else a.addAll(discover(base, q));
      }
      synchronized (SeriesIndex.class) {
        sessions.put(sessionKey, a);
        while (sessions.size() > 2) sessions.remove(sessions.keySet().iterator().next());
      }
    }
    return select(a, page, q, sort, genre, language, order);
  }
}
