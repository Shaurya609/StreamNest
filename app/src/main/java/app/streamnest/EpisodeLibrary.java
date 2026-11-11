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
      for(String field:new String[]{"kitsuId","kitsuEpisode","animeMapped","animeTitle"})if(m.has(field))e.put(field,m.get(field));
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
        FillerMetadata.apply(result);
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
    int identityMal = title.raw.optInt("malId");
    // Explicit TV identities avoid ambiguous IMDb reverse mappings to related OVAs.
    if (identityMal == 0 && id.equals("tt7441658")) identityMal = 34572;
    if (identityMal == 0 && id.equals("tt4644488")) identityMal = 30694;
    if (identityMal > 0)
      try {
        metadata =
            CanonicalEpisodes.fromMappings(
                CatalogHttp.object(
                    "https://api.ani.zip/mappings?mal_id=" + identityMal, null),
                id);
      } catch (Exception ignored) {
      }

    if(metadata.length()==0&&identityMal==0&&title.kind.equals("animes")&&id.matches("tt[0-9]+"))try{
      JSONObject mapped=CatalogHttp.object("https://api.ani.zip/mappings?imdb_id="+id,null);
      if(CanonicalEpisodes.trustedReverseMapping(mapped,id,title.title))metadata=CanonicalEpisodes.fromMappings(mapped,id);
    }catch(Exception ignored){}
    if (id.matches("tt[0-9]+"))
      try {
        JSONObject meta =
            CatalogHttp.object("https://v3-cinemeta.strem.io/meta/series/" + id + ".json", null)
                .getJSONObject("meta");
        JSONArray canonical=CanonicalEpisodes.fromCinemeta(meta,id);
        metadata=metadata.length()==0?canonical:CanonicalEpisodes.augmentMappings(metadata,canonical,identityMal==0);
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
    FillerMetadata.apply(rows);
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
