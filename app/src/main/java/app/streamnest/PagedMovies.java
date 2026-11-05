package app.streamnest;

import java.util.*;
import org.json.*;

/** Provider-ordered pages, scanned until enough matching HD titles exist. No fixed pool ceiling. */
final class PagedMovies {
  interface Fetcher {
    JSONObject get(String path) throws Exception;
  }

  static Fetcher fetcher = MovieProvider::browse;
  static final ThreadLocal<Boolean> remaining = new ThreadLocal<>();
  static final LinkedHashMap<String, Feed> feeds = new LinkedHashMap<>();

  static class Feed {
    long created = System.currentTimeMillis();
    String token = UUID.randomUUID().toString();
    Boolean savedOrigin;
    int next = 1;
    boolean done;
    List<JSONObject> rows = new ArrayList<>();
    Set<String> ids = new HashSet<>();
    Set<String> pages = new HashSet<>();
    Map<String, JSONObject> byId = new HashMap<>();
  }

  static String providerSort(String sort) {
    switch (BrowseOrder.key(sort)) {
      case "trending":
        return "date_added";
      case "popularity":
        return "download_count";
      case "last added":
        return "date_added";
      default:
        return sort;
    }
  }

  static void merge(JSONObject old, JSONObject newer) throws Exception {
    JSONArray items = old.getJSONArray("torrents"), incoming = newer.getJSONArray("torrents");
    Set<String> keys = new HashSet<>();
    for (int i = 0; i < items.length(); i++) {
      JSONObject t = items.getJSONObject(i);
      keys.add(t.optString("hash") + "|" + t.optString("url") + "|" + t.optString("quality"));
    }
    for (int i = 0; i < incoming.length(); i++) {
      JSONObject t = incoming.getJSONObject(i);
      if (keys.add(t.optString("hash") + "|" + t.optString("url") + "|" + t.optString("quality")))
        items.put(t);
    }
  }

  static synchronized List<Catalog.Title> list(
      int page,
      String query,
      String sort,
      String genre,
      String quality,
      String language,
      int order,
      boolean anime)
      throws Exception {
    return list(page, query, sort, genre, quality, language, order, anime, 24);
  }

  static synchronized List<Catalog.Title> list(
      int page,
      String query,
      String sort,
      String genre,
      String quality,
      String language,
      int order,
      boolean anime,
      int pageSize)
      throws Exception {
    String g = anime ? "Animation" : genre;
    String prefix =
        "list_movies.json?limit=50&quality="
            + (quality.equals("2160p") ? "2160p" : "1080p")
            + "&sort_by="
            + Net.q(providerSort(sort))
            + "&order_by="
            + (order == 1 ? "asc" : "desc")
            + "&query_term="
            + Net.q(query)
            + (g.equals("All") ? "" : "&genre=" + Net.q(g));
    String key =
        prefix + "|" + quality + "|" + language + "|" + anime + "|" + genre + "|" + pageSize;
    Feed feed = feeds.get(key);
    if (feed == null || page == 1 && System.currentTimeMillis() - feed.created > 600000) {
      feed = new Feed();
      feeds.put(key, feed);
      while (feeds.size() > 2) feeds.remove(feeds.keySet().iterator().next());
    }
    int end = Math.max(1, page) * pageSize;
    while (feed.rows.size() < end && !feed.done) {
      if (Thread.currentThread().isInterrupted())
        throw new InterruptedException("Catalog request replaced");
      JSONObject data;
      boolean force = Boolean.TRUE.equals(MovieProvider.forceLive.get());
      MovieProvider.feedToken.set(feed.token);
      MovieProvider.forceLive.set(force || Boolean.FALSE.equals(feed.savedOrigin));
      try {
        data = fetcher.get(prefix + "&page=" + feed.next);
      } finally {
        MovieProvider.feedToken.remove();
        MovieProvider.forceLive.set(force);
      }
      if (feed.savedOrigin == null || data.optBoolean("catalog_saved"))
        feed.savedOrigin = data.optBoolean("catalog_saved");
      JSONArray raw = data.optJSONArray("movies");
      if (raw == null) raw = new JSONArray();
      StringBuilder signature = new StringBuilder();
      for (int i = 0; i < raw.length(); i++) {
        JSONObject m = raw.getJSONObject(i);
        signature.append(m.optString("imdb_code")).append(':');
        JSONArray ts = m.optJSONArray("torrents");
        if (ts != null)
          for (int j = 0; j < ts.length(); j++)
            signature
                .append(ts.getJSONObject(j).optString("hash"))
                .append(ts.getJSONObject(j).optString("quality"));
        signature.append('|');
      }
      if (!feed.pages.add(signature.toString())) {
        feed.done = true;
        break;
      }

      List<JSONObject> batch = new ArrayList<>();
      for (int i = 0; i < raw.length(); i++) batch.add(raw.getJSONObject(i));
      if (BrowseOrder.key(sort).equals("trending")) {
        batch.sort(
            (a, b) -> {
              long aSeeds = BrowseOrder.seeds(a), bSeeds = BrowseOrder.seeds(b);
              return order == 1 ? Long.compare(aSeeds, bSeeds) : Long.compare(bSeeds, aSeeds);
            });
      }
      for (int i = 0; i < batch.size(); i++) {
        JSONObject m = batch.get(i);
        String lang = FreshMovies.language(m.optString("language"));
        if (anime
            ? !lang.equals("ja")
            : !(lang.equals("en") || lang.equals("hi") || lang.equals("ko"))
                || (!language.equals("all") && !language.equals(lang))) continue;
        if (anime && !genre.equals("All") && !genre.equals("Animation")) {
          JSONArray genres = m.optJSONArray("genres");
          boolean matched = false;
          if (genres != null)
            for (int j = 0; j < genres.length(); j++)
              if (BrowseOrder.genreKey(genres.optString(j)).equals(BrowseOrder.genreKey(genre)))
                matched = true;
          if (!matched) continue;
        }
        JSONObject n = FreshMovies.normalize(m);
        JSONArray items = n.getJSONArray("torrents"), chosen = new JSONArray();
        for (int j = 0; j < items.length(); j++) {
          JSONObject item = items.getJSONObject(j);
          if (quality.equals("All") || item.optString("quality").equals(quality)) chosen.put(item);
        }
        String id = n.optString("imdb_code");
        if (chosen.length() == 0 || !id.matches("tt[0-9]+")) continue;
        if (!feed.ids.add(id)) {
          merge(feed.byId.get(id), n.put("torrents", chosen));
          continue;
        }
        feed.byId.put(id, n);
        n.put("torrents", chosen);
        if (anime) n.put("animeMovie", true);
        feed.rows.add(n);
      }
      feed.next++;
      feed.done =
          raw.length() < 50
              || (data.optInt("movie_count") > 0
                  && (feed.next - 1) * 50 >= data.optInt("movie_count"));
    }
    List<Catalog.Title> out = new ArrayList<>();
    int start = Math.min(feed.rows.size(), end - pageSize);
    for (int i = start; i < Math.min(feed.rows.size(), end); i++)
      out.add(Catalog.parse(feed.rows.get(i), "movies"));
    if (Boolean.TRUE.equals(feed.savedOrigin)) CatalogStatus.cached();
    remaining.set(feed.rows.size() > end || !feed.done);
    return out;
  }
}
