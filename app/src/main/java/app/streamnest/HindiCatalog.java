package app.streamnest;

import android.content.Context;
import java.util.*;
import org.json.*;

final class HindiCatalog {
  static final String PROVIDER = "https://indian-regional-catalog.vercel.app/";

  static List<Catalog.Title> parse(JSONObject response, String kind) throws Exception {
    JSONArray a = response.getJSONArray("metas");
    List<Catalog.Title> out = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) {
      JSONObject m = a.getJSONObject(i);
      String id = m.optString("imdb_id", m.optString("id", ""));
      String title = m.optString("name", m.optString("title", "Untitled"));
      JSONObject normalized =
          new JSONObject()
              .put("imdb", id)
              .put("title", title)
              .put("year", m.optString("releaseInfo", ""))
              .put("poster_big", m.optString("poster", ""))
              .put(
                  "description",
                  m.optString(
                      "description",
                      "Hindi title. Playback sources will be checked when you choose this title."))
              .put("regionalCatalog", true)
              .put("language", "hi");
      out.add(Catalog.parse(normalized, kind));
    }
    return out;
  }

  static String path(String kind, int page) {
    String type = kind.equals("movies") ? "movie" : "series",
        id = kind.equals("movies") ? "hi" : "hi_series";
    return "catalog/" + type + "/" + id + (page <= 1 ? "" : "/skip=" + ((page - 1) * 20)) + ".json";
  }

  static List<Catalog.Title> mixedMovies(
      int page, String sort, String quality, String genre, int order) throws Exception {
    List<Catalog.Title> english =
        PagedMovies.list(page, "", sort, genre, quality, "en", order, false, 18);
    boolean more = Boolean.TRUE.equals(PagedMovies.remaining.get());
    List<JSONObject> pool =
        MovieIndex.records("mixed|" + sort + "|" + quality + "|" + genre + "|" + order, page);
    List<Catalog.Title> hindi =
        BrowseOrder.select(pool, page, "", sort, genre, quality, "hi", order, 6);
    more |= !BrowseOrder.select(pool, page + 1, "", sort, genre, quality, "hi", order, 6).isEmpty();
    List<Catalog.Title> out = new ArrayList<>();
    int h = 0;
    for (int i = 0; i < english.size(); i++) {
      out.add(english.get(i));
      if ((i + 1) % 3 == 0 && h < hindi.size()) out.add(hindi.get(h++));
    }
    while (h < hindi.size()) out.add(hindi.get(h++));
    PagedMovies.remaining.set(more);
    return out;
  }

  static List<Catalog.Title> list(
      Context context,
      String base,
      String kind,
      int page,
      String query,
      String sort,
      String quality,
      String genre,
      int order)
      throws Exception {
    if (kind.equals("movies"))
      return MovieIndex.list(page, query, sort, genre, quality, "hi", order);
    if (kind.equals("shows")) return SeriesIndex.list(base, page, query, sort, genre, "hi", order);
    if (!kind.equals("movies") && !kind.equals("shows")) return Collections.emptyList();
    List<Catalog.Title> out = parse(new JSONObject(Net.text(PROVIDER + path(kind, page))), kind);
    if (kind.equals("movies")) out = FreshMovies.available(out);
    if (!query.isBlank()) {
      String q = query.toLowerCase(Locale.ROOT);
      out.removeIf(t -> !t.title.toLowerCase(Locale.ROOT).contains(q));
    }
    if (sort.equals("title")) out.sort(Comparator.comparing(t -> t.title));
    else if (sort.equals("year")) out.sort((a, b) -> b.year.compareTo(a.year));
    return out;
  }
}
