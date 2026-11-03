package app.streamnest;

import java.util.*;
import org.json.*;

final class BrowseOrder {
  static final String[] KEYS = {"trending", "popularity", "last added", "year", "title", "rating"};
  static final String[] LABELS = {
    "Trending", "Popularity", "Last added", "Year", "Title", "Rating"
  };

  static String genreKey(String s) {
    return s.toLowerCase(Locale.ROOT)
        .replace("science fiction", "sci-fi")
        .replace("science-fiction", "sci-fi");
  }

  static String key(String s) {
    return s.equals("seeds") ? "trending" : s;
  }

  static long seeds(JSONObject m) {
    JSONArray a = m.optJSONArray("torrents");
    long n = 0;
    if (a != null)
      for (int i = 0; i < a.length(); i++)
        if (FreshMovies.hd(a.optJSONObject(i)))
          n = Math.max(n, a.optJSONObject(i).optLong("seeds"));
    return n;
  }

  static Comparator<JSONObject> comparator(String s) {
    switch (key(s)) {
      case "title":
        return Comparator.comparing(m -> m.optString("title").toLowerCase(Locale.ROOT));
      case "year":
        return Comparator.<JSONObject>comparingInt(m -> m.optInt("year")).reversed();
      case "rating":
        return Comparator.<JSONObject>comparingDouble(m -> m.optDouble("rating", 0)).reversed();
      case "last added":
        return Comparator.<JSONObject>comparingLong(m -> m.optLong("date_uploaded_unix"))
            .reversed();
      case "popularity":
        return Comparator.<JSONObject>comparingInt(
                m -> m.optInt("popularityRank", Integer.MAX_VALUE))
            .thenComparing(Comparator.comparingLong(BrowseOrder::seeds).reversed());
      default:
        return Comparator.comparingLong(BrowseOrder::seeds).reversed();
    }
  }

  static List<Catalog.Title> select(
      List<JSONObject> pool,
      int page,
      String q,
      String sort,
      String genre,
      String quality,
      String language)
      throws Exception {
    return select(pool, page, q, sort, genre, quality, language, sort.equals("title") ? 1 : -1);
  }

  static List<Catalog.Title> select(
      List<JSONObject> pool,
      int page,
      String q,
      String sort,
      String genre,
      String quality,
      String language,
      int order)
      throws Exception {
    List<JSONObject> chosen = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    for (JSONObject m : pool) {
      String lang = m.optString("language"), id = m.optString("imdb_code");
      if (!ids.add(id)
          || !(lang.equals("en") || lang.equals("hi"))
          || (!language.equals("all") && !lang.equals(language))) continue;
      if (!q.isBlank()
          && !m.optString("title").toLowerCase(Locale.ROOT).contains(q.toLowerCase(Locale.ROOT))
          && !id.equals(q)) continue;
      if (!genre.equals("All")) {
        JSONArray genres = m.optJSONArray("genres");
        boolean match = false;
        if (genres != null)
          for (int i = 0; i < genres.length(); i++)
            if (genreKey(genres.optString(i)).equals(genreKey(genre))) match = true;
        if (!match) continue;
      }
      JSONObject copy = new JSONObject(m.toString());
      JSONArray a = m.optJSONArray("torrents"), out = new JSONArray();
      if (a != null)
        for (int i = 0; i < a.length(); i++) {
          JSONObject t = a.getJSONObject(i);
          if (FreshMovies.hd(t)
              && (quality.equals("All")
                  || quality.contains(",")
                  || quality.equals(t.optString("quality")))) out.put(t);
        }
      if (out.length() == 0) continue;
      copy.put("torrents", out);
      chosen.add(copy);
    }
    Comparator<JSONObject> ordering = comparator(sort).thenComparing(m -> m.optString("imdb_code"));
    if (order != (sort.equals("title") ? 1 : -1)) ordering = ordering.reversed();
    chosen.sort(ordering);
    int start = Math.min(chosen.size(), Math.max(0, page - 1) * 24),
        end = Math.min(chosen.size(), start + 24);
    List<Catalog.Title> out = new ArrayList<>();
    for (JSONObject m : chosen.subList(start, end)) out.add(Catalog.parse(m, "movies"));
    return out;
  }
}
