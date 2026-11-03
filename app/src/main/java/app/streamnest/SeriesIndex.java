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
        new JSONObject(
            Net.text(
                base.replaceAll("/+$", "")
                    + "/shows?"
                    + Catalog.compat()
                    + "&short=1&page="
                    + page
                    + "&sort="
                    + sort
                    + (query.isBlank() ? "" : "&keywords=" + Net.q(query))));
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
          "regionalCatalog"
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
    return snapshot;
  }

  static List<JSONObject> buildFull(String base) throws Exception {
    Map<String, JSONObject> all = new LinkedHashMap<>();
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

  static List<Catalog.Title> select(
      List<JSONObject> records,
      int page,
      String q,
      String sort,
      String genre,
      String language,
      int order)
      throws Exception {
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
        JSONArray found = fetch(base, "seeds", 1, q);
        for (int i = 0; i < found.length(); i++)
          a.add(found.getJSONObject(i).put("language", "en"));
      }
      synchronized (SeriesIndex.class) {
        sessions.put(sessionKey, a);
        while (sessions.size() > 2) sessions.remove(sessions.keySet().iterator().next());
      }
    }
    return select(a, page, q, sort, genre, language, order);
  }
}
