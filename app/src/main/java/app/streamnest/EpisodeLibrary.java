package app.streamnest;

import java.util.*;
import org.json.*;

final class EpisodeLibrary {
  static final Map<String, JSONObject> cache = new LinkedHashMap<>();
  static final Map<String, Long> ages = new HashMap<>();

  static String key(JSONObject e) {
    return e.optInt("episode") > 0
        ? e.optInt("season") + ":" + e.optInt("episode")
        : "meta:" + e.optString("metadataId", e.optString("id"));
  }

  static List<JSONObject> merge(JSONArray metadata, JSONObject source) throws Exception {
    Map<String, JSONObject> rows = new LinkedHashMap<>();
    JSONArray a = Catalog.episodes(source);
    for (int i = 0; i < a.length(); i++) {
      JSONObject e = new JSONObject(a.getJSONObject(i).toString());
      if (e.optInt("episode") > 0) rows.put(key(e), e);
    }
    Set<String> seen = new HashSet<>();
    List<JSONObject> result = new ArrayList<>();
    for (int i = 0; i < metadata.length(); i++) {
      JSONObject m = metadata.getJSONObject(i),
          e =
              new JSONObject()
                  .put("season", m.optInt("season"))
                  .put("episode", m.optInt("number"))
                  .put("metadataId", m.optString("id"))
                  .put("title", m.optString("name", "Episode " + m.optInt("number")))
                  .put("synopsis", m.optString("summary", "").replaceAll("<[^>]+>", " "))
                  .put("airdate", m.optString("airdate", ""));
      if (m.has("skipMalId")) e.put("skipMalId", m.getInt("skipMalId"));
      if (m.has("absolute")) e.put("absolute", m.getInt("absolute"));
      String key = key(e);
      if (!seen.add(key)) continue;
      JSONObject match = rows.remove(key);
      if (match != null)
        for (String field : new String[] {"items", "torrents", "items_lang"})
          if (match.has(field)) e.put(field, match.get(field));
      result.add(e);
    }
    for (JSONObject e : rows.values()) if (seen.add(key(e))) result.add(e);
    result.sort(
        Comparator.comparingInt(
                (JSONObject e) -> e.optInt("season") == 0 ? Integer.MAX_VALUE : e.optInt("season"))
            .thenComparingInt(e -> e.optInt("episode"))
            .thenComparing(e -> e.optString("airdate")));
    return result;
  }

  static Set<Integer> fillerPages(List<JSONObject> rows, int mal) {
    Set<Integer> pages = new TreeSet<>();
    if (mal <= 0) return pages;
    for (JSONObject row : rows)
      if (row.optInt("skipMalId") == mal && row.optInt("episode") > 0)
        pages.add((row.optInt("episode") - 1) / 100 + 1);
    return pages;
  }

  static void attachFiller(List<JSONObject> rows, int mal, JSONArray data) throws Exception {
    for (int i = 0; i < data.length(); i++) {
      JSONObject episode = data.getJSONObject(i);
      for (JSONObject row : rows)
        if (mal > 0 && row.optInt("skipMalId") == mal
            && row.optInt("episode") == episode.optInt("mal_id")) {
          for (String flag : new String[] {"filler", "recap"})
            if (episode.opt(flag) instanceof Boolean) row.put(flag, episode.getBoolean(flag));
        }
    }
  }

  static List<JSONObject> load(String base, Catalog.Title title) throws Exception {
    String id = title.id;
    JSONObject saved;
    synchronized (EpisodeLibrary.class) {
      saved = cache.get(id);
      if (saved != null && System.currentTimeMillis() - ages.getOrDefault(id, 0L) < 900000) {
        List<JSONObject> result = new ArrayList<>();
        JSONArray rows = saved.getJSONArray("rows");
        for (int i = 0; i < rows.length(); i++)
          result.add(new JSONObject(rows.getJSONObject(i).toString()));
        return result;
      }
    }
    JSONObject source = new JSONObject();
    Exception sourceError = null;
    try {
      source = Catalog.details(base, title);
    } catch (Exception e) {
      sourceError = e;
    }
    JSONArray metadata = new JSONArray();
    if (title.raw.optInt("malId") > 0)
      try {
        metadata =
            CanonicalEpisodes.fromMappings(
                CatalogHttp.object(
                    "https://api.ani.zip/mappings?mal_id=" + title.raw.optInt("malId"), null),
                id);
      } catch (Exception ignored) {
      }

    if (metadata.length() == 0 && id.matches("tt[0-9]+"))
      try {
        JSONObject meta =
            CatalogHttp.object("https://v3-cinemeta.strem.io/meta/series/" + id + ".json", null)
                .getJSONObject("meta");
        metadata = CanonicalEpisodes.fromCinemeta(meta, id);
      } catch (Exception ignored) {
      }
    if (metadata.length() == 0 && id.matches("tt[0-9]+"))
      try {
        JSONObject identity =
            CatalogHttp.object("https://api.tvmaze.com/lookup/shows?imdb=" + id, null);
        int show = identity.getInt("id");
        Object response =
            CatalogHttp.array("https://api.tvmaze.com/shows/" + show + "/episodes?specials=1");
        if (response instanceof JSONArray) metadata = (JSONArray) response;
      } catch (Exception ignored) {
      }
    List<JSONObject> rows = merge(metadata, source);
    if (title.raw.optBoolean("animeSeries"))
      try {
        List<Catalog.Title> releases = new ArrayList<>();
        for (int page = 1; ; page++) {
          List<Catalog.Title> found =
              AnimeReleases.list(page, title.title, "last added", "All", -1);
          releases.addAll(found);
          if (found.size() < 24) break;
        }
        CanonicalEpisodes.attachLive(rows, releases, title.title);
      } catch (Exception ignored) {
      }
    // Only rows with an explicit MAL episode mapping may receive Jikan flags.
    Set<Integer> mappedAnime = new LinkedHashSet<>();
    for (JSONObject row : rows) if (row.optInt("skipMalId") > 0)
      mappedAnime.add(row.optInt("skipMalId"));
    for (int mal : mappedAnime) {
      Set<Integer> pages = fillerPages(rows, mal);
      for (int page : pages) try {
      Thread.sleep(400);
      JSONObject response = CatalogHttp.object(
          "https://api.jikan.moe/v4/anime/" + mal + "/episodes?page=" + page, null);
      JSONArray data = response.optJSONArray("data");
      if (data == null) continue;
      attachFiller(rows, mal, data);
      } catch (InterruptedException interrupted) {
        Thread.currentThread().interrupt();
        throw interrupted;
      } catch (Exception ignored) {}
    }
    if (rows.isEmpty() && sourceError != null) throw sourceError;
    synchronized (EpisodeLibrary.class) {
      cache.put(id, new JSONObject().put("rows", new JSONArray(rows)));
      ages.put(id, System.currentTimeMillis());
      while (cache.size() > 3) {
        String first = cache.keySet().iterator().next();
        cache.remove(first);
        ages.remove(first);
      }
    }
    return rows;
  }
}
