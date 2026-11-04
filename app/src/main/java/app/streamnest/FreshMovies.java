package app.streamnest;

import java.util.*;
import java.util.concurrent.*;
import org.json.*;

final class FreshMovies {
  static int discoveryThreads = 4;
  static final String BASE = "https://yts.gg/api/v2/";
  static final Map<String, JSONObject> cache = new ConcurrentHashMap<>();
  static final Map<String, Long> ages = new ConcurrentHashMap<>();

  static boolean hd(JSONObject t) {
    String q = t.optString("quality"), type = t.optString("type").toLowerCase(Locale.ROOT);
    return ReleaseQuality.hd(q, t.optString("file", t.optString("filename", t.optString("name"))))
        && (type.equals("web") || type.equals("bluray"))
        && (!t.optString("hash").isEmpty() || !t.optString("url").isEmpty());
  }

  static String language(String value) {
    switch (value) {
      case "eng":
        return "en";
      case "hin":
        return "hi";
      case "jpn":
        return "ja";
      default:
        return value;
    }
  }

  static JSONObject normalize(JSONObject m) throws Exception {
    JSONArray a = m.optJSONArray("torrents"), out = new JSONArray();
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        JSONObject t = a.getJSONObject(i);
        if (!hd(t)) continue;
        JSONObject c = new JSONObject(t.toString());
        String h = t.optString("hash");
        if (!h.isEmpty())
          c.put(
              "magnet",
              "magnet:?xt=urn:btih:"
                  + h
                  + "&dn="
                  + Net.q(m.optString("title"))
                  + "&tr=udp%3A%2F%2Ftracker.opentrackr.org%3A1337%2Fannounce");
        out.put(c);
      }
    for (int i = 0; i < out.length(); i++)
      if (out.getJSONObject(i).optString("url").startsWith("https://yts.gg/torrent/download/"))
        out.getJSONObject(i).remove("magnet");
    return new JSONObject(m.toString())
        .put("torrents", out)
        .put("freshMovie", true)
        .put("language", language(m.optString("language")));
  }

  static JSONObject find(String imdb) throws Exception {
    if (!imdb.matches("tt[0-9]+")) return null;
    if (cache.containsKey(imdb)
        && System.currentTimeMillis() - ages.getOrDefault(imdb, 0L) < 300000)
      return new JSONObject(cache.get(imdb).toString());
    JSONObject data = MovieProvider.data("list_movies.json?query_term=" + Net.q(imdb));
    JSONArray a = data.optJSONArray("movies");
    JSONObject combined = null;
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        JSONObject m = a.getJSONObject(i);
        if (!imdb.equals(m.optString("imdb_code"))) continue;
        JSONObject n = normalize(m);
        if (n.getJSONArray("torrents").length() == 0) continue;
        if (combined == null) combined = n;
        else PagedMovies.merge(combined, n);
      }
    if (combined == null) return null;
    if (cache.size() >= 160) {
      cache.clear();
      ages.clear();
    }
    cache.put(imdb, combined);
    ages.put(imdb, System.currentTimeMillis());
    return new JSONObject(combined.toString());
  }

  static List<Catalog.Title> list(int page, String query, String sort, String genre)
      throws Exception {
    String s =
        sort.equals("last added") ? "date_added" : sort.equals("seeds") ? "download_count" : sort;
    JSONObject data =
        MovieProvider.data(
            "list_movies.json?limit=50&page="
                + page
                + "&quality=1080p&order_by=desc&sort_by="
                + Net.q(s)
                + "&query_term="
                + Net.q(query)
                + (genre.equals("All") ? "" : "&genre=" + Net.q(genre)));
    JSONArray a = data.optJSONArray("movies");
    List<Catalog.Title> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    if (a != null)
      for (int i = 0; i < a.length(); i++) {
        JSONObject m = a.getJSONObject(i);
        if (!m.optString("language").equals("en")) continue;
        JSONObject n = normalize(m);
        if (n.getJSONArray("torrents").length() == 0 || !seen.add(n.optString("imdb_code")))
          continue;
        out.add(Catalog.parse(n, "movies"));
      }
    return out;
  }

  static List<Catalog.Title> available(List<Catalog.Title> titles) throws Exception {
    ExecutorService pool = Executors.newFixedThreadPool(discoveryThreads);
    try {
      List<Callable<Catalog.Title>> jobs = new ArrayList<>();
      for (Catalog.Title t : titles)
        jobs.add(
            () -> {
              JSONObject m = find(t.id);
              if (m == null) return null;
              return Catalog.parse(m, "movies");
            });
      List<Catalog.Title> out = new ArrayList<>();
      Exception failure = null;
      for (Future<Catalog.Title> f : pool.invokeAll(jobs)) {
        try {
          Catalog.Title t = f.get();
          if (t != null) out.add(t);
        } catch (ExecutionException e) {
          failure = new Exception("HD source provider unavailable", e.getCause());
        }
      }
      if (out.isEmpty() && failure != null) throw failure;
      return out;
    } finally {
      pool.shutdownNow();
    }
  }
}
