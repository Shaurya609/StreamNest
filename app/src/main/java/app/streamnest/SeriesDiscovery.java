package app.streamnest;

import java.util.*;
import org.json.*;

/** Independent metadata adapter, joined only through explicit IMDb identifiers. */
final class SeriesDiscovery {
  static List<JSONObject> parse(JSONArray results) throws Exception {
    List<JSONObject> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (int i = 0; i < results.length(); i++) {
      JSONObject show = results.getJSONObject(i).optJSONObject("show");
      if (show == null) continue;
      JSONObject ids = show.optJSONObject("externals");
      String id = ids == null ? "" : ids.optString("imdb");
      if (!id.matches("tt[0-9]+") || !seen.add(id)) continue;
      String language = show.optString("language"),
          lang =
              language.equals("Hindi")
                  ? "hi"
                  : language.equals("English") ? "en" : language.equals("Japanese") ? "ja" : language.equals("Korean") ? "ko" : "";
      if (lang.isEmpty()) continue;
      JSONObject image = show.optJSONObject("image"), rating = show.optJSONObject("rating");
      String premiered = show.optString("premiered");
      out.add(
          new JSONObject()
              .put("imdb", id)
              .put("title", show.optString("name"))
              .put("language", lang)
              .put("year", premiered.length() >= 4 ? premiered.substring(0, 4) : "")
              .put("rating", rating == null ? 0 : rating.optDouble("average", 0))
              .put(
                  "poster_big",
                  image == null ? "" : image.optString("original", image.optString("medium")))
              .put("description", show.optString("summary").replaceAll("<[^>]*>", " "))
              .put("genres", show.optJSONArray("genres"))
              .put("metadataOnly", true)
              .put("metadataSource", "TVmaze")
              .put("trendRank", Integer.MAX_VALUE)
              .put("latestRank", Integer.MAX_VALUE));
    }
    return out;
  }

  static List<JSONObject> search(String query) throws Exception {
    return parse(CatalogHttp.array("https://api.tvmaze.com/search/shows?q=" + Net.q(query)));
  }
}
