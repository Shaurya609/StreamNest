package app.streamnest;

import java.util.*;
import org.json.*;

final class CanonicalEpisodes {
  static JSONArray fromCinemeta(JSONObject meta, String imdb) throws Exception {
    if (!imdb.equals(meta.optString("imdb_id", meta.optString("id"))))
      throw new Exception("Episode metadata identity mismatch");
    JSONArray videos = meta.optJSONArray("videos");
    if (videos == null) return new JSONArray();
    List<JSONObject> rows = new ArrayList<>();
    for (int i = 0; i < videos.length(); i++) {
      JSONObject v = videos.getJSONObject(i);
      String released = v.optString("released", v.optString("firstAired", ""));
      rows.add(
          new JSONObject()
              .put("id", v.optString("id", v.optString("tvdb_id")))
              .put("season", v.optInt("season"))
              .put("number", v.optInt("episode", v.optInt("number")))
              .put("name", v.optString("name", v.optString("title", "Episode")))
              .put("summary", v.optString("overview", v.optString("description", "")))
              .put("airdate", released.length() >= 10 ? released.substring(0, 10) : released));
    }
    rows.sort(
        Comparator.comparingInt((JSONObject e) -> e.optInt("season"))
            .thenComparingInt(e -> e.optInt("number")));
    return new JSONArray(rows);
  }

  static JSONArray fromMappings(JSONObject doc, String imdb) throws Exception {
    JSONObject mappings = doc.getJSONObject("mappings");
    if (!imdb.equals(mappings.optString("imdb_id")))
      throw new Exception("Anime episode identity mismatch");
    JSONObject episodes = doc.getJSONObject("episodes");
    List<JSONObject> rows = new ArrayList<>();
    Iterator<String> keys = episodes.keys();
    while (keys.hasNext()) {
      JSONObject e = episodes.optJSONObject(keys.next());
      if (e == null || !e.has("seasonNumber") || !e.has("episodeNumber")) continue;
      JSONObject titles = e.optJSONObject("title");
      String name = titles == null ? "" : titles.optString("en", titles.optString("x-jat", ""));
      JSONObject row =
          new JSONObject()
              .put("id", e.optString("tvdbId"))
              .put("season", e.optInt("seasonNumber"))
              .put("number", e.optInt("episodeNumber"))
              .put("name", name.isEmpty() ? "Episode " + e.optInt("episodeNumber") : name)
              .put("summary", e.optString("overview", ""))
              .put("airdate", e.optString("airDate", e.optString("airdate", "")));
      if (e.optInt("absoluteEpisodeNumber") > 0)
        row.put("absolute", e.optInt("absoluteEpisodeNumber"));
      rows.add(row);
    }
    rows.sort(
        Comparator.comparingInt((JSONObject e) -> e.optInt("season"))
            .thenComparingInt(e -> e.optInt("number")));
    return new JSONArray(rows);
  }

  static String normalized(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }

  static int releaseNumber(String release, String title) {
    String clean = release.replaceAll("^(?:\\[[^\\]]+\\]\\s*)+", "");
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("^(.+?)\\s-\\s([0-9]{1,4})(?:v[0-9]+)?(?:\\s|\\[|\\(|$)")
            .matcher(clean);
    if (!m.find() || !normalized(m.group(1)).equals(normalized(title))) return -1;
    return Integer.parseInt(m.group(2));
  }

  static void attachLive(List<JSONObject> episodes, List<Catalog.Title> releases, String name)
      throws Exception {
    Map<Integer, JSONObject> byAbsolute = new HashMap<>();
    for (JSONObject e : episodes)
      if (e.optInt("absolute") > 0) byAbsolute.put(e.optInt("absolute"), e);
    for (Catalog.Title release : releases) {
      int number = releaseNumber(release.title, name);
      JSONObject ep = byAbsolute.get(number);
      if (ep == null) continue;
      JSONArray sources = ep.optJSONArray("torrents");
      if (sources == null) sources = new JSONArray();
      JSONArray items = release.raw.optJSONArray("torrents");
      if (items == null) continue;
      Set<String> urls = new HashSet<>();
      for (int i = 0; i < sources.length(); i++)
        urls.add(sources.getJSONObject(i).optString("url"));
      for (int i = 0; i < items.length(); i++)
        if (urls.add(items.getJSONObject(i).optString("url"))) sources.put(items.getJSONObject(i));
      ep.put("torrents", sources).put("liveRelease", true);
    }
  }
}
