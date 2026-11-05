package app.streamnest;

import java.util.*;
import org.json.*;

final class AnimeCatalog {
  static android.content.res.AssetManager assets;
  static java.io.File disk;
  static List<JSONObject> snapshot;
  static long refreshed;
  static boolean refreshing;
  static final Map<String, List<JSONObject>> sessions = new LinkedHashMap<>();

  static void configure(android.content.res.AssetManager a, java.io.File cache) {
    assets = a;
    disk = new java.io.File(cache, "anime-series-v8.json");
  }

  static List<JSONObject> read(java.io.InputStream input) throws Exception {
    JSONObject doc;
    try (java.io.InputStream in = input) {
      doc =
          new JSONObject(
              new String(Net.read(in, 4000000), java.nio.charset.StandardCharsets.UTF_8));
    }
    refreshed = doc.optLong("saved");
    List<JSONObject> rows = new ArrayList<>();
    JSONArray a = doc.getJSONArray("series");
    for (int i = 0; i < a.length(); i++) rows.add(a.getJSONObject(i));
    return rows;
  }

  static synchronized List<JSONObject> pool() throws Exception {
    if (snapshot == null) {
      if (disk != null && disk.isFile())
        try {
          snapshot = read(new java.io.FileInputStream(disk));
        } catch (Exception ignored) {
        }
      if (snapshot == null) snapshot = read(assets.open("anime-series-v6.json"));
    }
    if (System.currentTimeMillis() - refreshed > 21600000 && !refreshing) {
      refreshing = true;
      Thread task =
          new Thread(
              () -> {
                try {
                  refresh();
                } catch (Exception ignored) {
                } finally {
                  synchronized (AnimeCatalog.class) {
                    refreshing = false;
                  }
                }
              },
              "anime-discovery");
      task.setDaemon(true);
      task.setPriority(Thread.MIN_PRIORITY);
      task.start();
    }
    CatalogStatus.cached();
    return snapshot;
  }

  static JSONObject identity(JSONObject meta, JSONObject source, JSONObject canonical)
      throws Exception {
    if (!canonical.optString("type").equals("Animation")
        || !(canonical.optString("language").equals("Japanese")
            || canonical.optJSONArray("genres") != null
                && canonical.getJSONArray("genres").toString().contains("Anime"))) return null;
    JSONObject raw =
        new JSONObject()
            .put("imdb", meta.getString("imdb_id"))
            .put("title", canonical.getString("name"))
            .put(
                "year",
                canonical.optString("premiered").length() >= 4
                    ? canonical.optString("premiered").substring(0, 4)
                    : "")
            .put(
                "description",
                canonical
                    .optString("summary", meta.optString("description"))
                    .replaceAll("<[^>]+>", " "))
            .put("genres", canonical.optJSONArray("genres"))
            .put("language", "ja")
            .put("animeSeries", true)
            .put("identitySource", "TVmaze / IMDb")
            .put("rating", meta.optDouble("imdbRating", 0))
            .put("poster_big", meta.optString("poster"));
    int playable = 0, hdCount = 0;
    Set<String> available = new LinkedHashSet<>();
    JSONArray episodeRows = Catalog.episodes(source);
    for (int n = 0; n < episodeRows.length(); n++) {
      List<Catalog.Source> episodeSources = PlaybackPlan.sources(episodeRows.getJSONObject(n));
      List<String> qualities = PlaybackPlan.episodeQualities(episodeSources);
      if (!qualities.isEmpty()) playable++;
      if (!PlaybackPlan.qualities(episodeSources).isEmpty()) hdCount++;
      available.addAll(qualities);
    }
    if (playable == 0) return null;
    raw.put("verifiedHdEpisodes", hdCount).put("verifiedEpisodes", playable);
    raw.put("verifiedQualities", new JSONArray(available));
    return raw;
  }

  static void refresh() throws Exception {
    Map<String, JSONObject> all = new LinkedHashMap<>();
    synchronized (AnimeCatalog.class) {
      for (JSONObject m : snapshot) all.put(m.optString("imdb"), new JSONObject(m.toString()));
    }
    Set<String> attempted = new HashSet<>();
    int rank = 0;
    for (String catalog : new String[] {"kitsu-anime-trending", "kitsu-anime-airing"}) {
      JSONObject response =
          CatalogHttp.object(
              "https://anime-kitsu.strem.fun/catalog/series/" + catalog + ".json", "metas");
      JSONArray a = response.optJSONArray("metas");
      if (a == null) continue;
      for (int i = 0; i < a.length(); i++) {
        JSONObject meta = a.getJSONObject(i);
        String id = meta.optString("imdb_id");
        if (!id.matches("tt[0-9]+") || !attempted.add(id)) continue;
        String type = meta.optString("animeType");
        if (!(type.equals("TV") || type.equals("ONA") || type.equals("OVA"))) continue;
        if (all.containsKey(id)) {
          all.get(id).put("trendRank", ++rank);
          continue;
        }
        try {
          JSONObject source =
              new JSONObject(
                  Net.text(
                      "https://api.apiabcd.com/show?" + Catalog.compat() + "&imdb=" + Net.q(id)));
          if (ReleaseQuality.episodes(source, "All").isEmpty()) continue;
          JSONObject canonical =
              new JSONObject(Net.text("https://api.tvmaze.com/lookup/shows?imdb=" + Net.q(id)));
          JSONObject raw = identity(meta, source, canonical);
          if (raw != null) all.put(id, raw.put("trendRank", ++rank).put("latestRank", ++rank));
        } catch (Exception ignored) {
        }
      }
    }
    long now = System.currentTimeMillis();
    List<JSONObject> rows = new ArrayList<>(all.values());
    if (disk != null) {
      java.io.File tmp = new java.io.File(disk.getParentFile(), "anime-series-v6.tmp");
      java.nio.file.Files.write(
          tmp.toPath(),
          new JSONObject()
              .put("saved", now)
              .put("series", new JSONArray(rows))
              .toString()
              .getBytes(java.nio.charset.StandardCharsets.UTF_8));
      java.nio.file.Files.move(
          tmp.toPath(), disk.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }
    synchronized (AnimeCatalog.class) {
      snapshot = rows;
      refreshed = now;
    }
  }

  static List<Catalog.Title> list(
      int page, String q, String sort, String genre, String quality, int order) throws Exception {
    String key = q + "|" + sort + "|" + genre + "|" + quality + "|" + order;
    List<JSONObject> source;
    synchronized (AnimeCatalog.class) {
      source = sessions.get(key);
    }
    if (source == null || page == 1) {
      source = new ArrayList<>(pool());
      if (!q.isBlank()) try {
        Set<String> ids = new HashSet<>();
        for (JSONObject row : source) ids.add(row.optString("imdb"));
        for (JSONObject row : SeriesDiscovery.search(q))
          if (row.optBoolean("animeSeries") && ids.add(row.optString("imdb")))
            source.add(row);
      } catch (Exception ignored) {}

      synchronized (AnimeCatalog.class) {
        sessions.put(key, source);
        while (sessions.size() > 4) sessions.remove(sessions.keySet().iterator().next());
      }
    }
    List<JSONObject> rows = new ArrayList<>();
    for (JSONObject m : source) {
      JSONArray qualities = m.optJSONArray("verifiedQualities");
      if (!quality.equals("All")
          && (qualities == null
              ? !quality.equals("1080p")
              : !qualities.toString().contains(quality))) continue;
      rows.add(m);
    }
    return SeriesIndex.select(rows, page, q, sort, genre, "all", order);
  }
}
