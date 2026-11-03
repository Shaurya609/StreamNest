package app.streamnest;

import java.util.*;
import org.json.*;

final class ReleaseQuality {
  static boolean camera(String name) {
    return name.toLowerCase(Locale.ROOT)
        .matches(
            ".*(?:\\bcam\\b|\\btelesync\\b|\\bhdcam\\b|\\bcamrip\\b|\\btelesync\\b|\\bhdts\\b|\\btsrip\\b).*");
  }

  static boolean hd(String quality, String name) {
    return (quality.equals("1080p") || quality.equals("2160p")) && !camera(name);
  }

  static List<JSONObject> episodes(JSONObject doc, String quality) throws Exception {
    JSONArray a = Catalog.episodes(doc);
    List<JSONObject> out = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) {
      JSONObject ep = new JSONObject(a.getJSONObject(i).toString());
      List<Catalog.Source> sources = new ArrayList<>();
      Catalog.collect(ep.opt("items"), "", sources);
      Catalog.collect(ep.opt("torrents"), "", sources);
      Catalog.collect(ep.opt("items_lang"), "", sources);
      sources.removeIf(
          s ->
              (!s.label.contains("1080p") && !s.label.contains("2160p"))
                  || camera(s.label)
                  || (!quality.equals("All") && !s.label.contains(quality)));
      if (sources.isEmpty()) continue;
      ep.put("hdSourceCount", sources.size());
      out.add(ep);
    }
    out.sort(
        Comparator.comparingInt((JSONObject e) -> e.optInt("season"))
            .thenComparingInt(e -> e.optInt("episode")));
    return out;
  }
}
