package app.streamnest;

import java.util.*;
import org.json.*;

final class Catalog {
  static class Source {
    String label, url;
    int seeds;
    int fileIndex = -1;
    String filename = "";

    Source(String l, String u, int s) {
      label = l;
      url = u;
      seeds = s;
    }
  }

  static class Title {
    String id, title, year, poster, synopsis, kind;
    JSONObject raw;
    List<Source> sources = new ArrayList<>();
  }

  static List<Title> list(
      String base,
      String kind,
      int page,
      String query,
      String sort,
      String genre,
      String quality,
      int order)
      throws Exception {
    if (kind.equals("animes")
        && (base.contains("anime.api-fetch.website") || base.contains("accel.li")))
      return PagedMovies.list(page, query, sort, genre, quality, "all", order, true);
    if (base.isBlank()) throw new Exception("Set a catalog server in Settings");
    if (kind.equals("movies") && legacy(base))
      return PagedMovies.list(page, query, sort, genre, quality, "all", order, false);
    sort = BrowseOrder.key(sort);
    if (sort.equals("trending")) sort = "seeds";
    if (kind.equals("shows") && legacy(base))
      return SeriesIndex.list(base, page, query, sort, genre, "all", order);
    base = base.replaceAll("/+$", "") + "/";
    boolean yts = base.contains("yts.");
    String path;
    if (yts) {
      if (!kind.equals("movies")) throw new Exception("This server only provides movies");
      path =
          "api/v2/list_movies.json?limit=30&page="
              + page
              + "&sort_by="
              + (sort.equals("last added")
                  ? "date_added"
                  : sort.equals("seeds") ? "download_count" : sort)
              + "&query_term="
              + Net.q(query)
              + (genre.equals("All") ? "" : "&genre=" + Net.q(genre));
    } else if (legacy(base)) {
      if (kind.equals("animes"))
        throw new Exception(
            "This server has no verified anime catalog. Set a dedicated anime API in Settings.");
      path =
          (kind.equals("movies") ? "list" : "shows")
              + "?"
              + compat()
              + "&page="
              + page
              + "&short=1&sort="
              + Net.q(sort.equals("last added") ? "dateadded" : sort)
              + (query.isEmpty() ? "" : "&keywords=" + Net.q(query))
              + "&genre="
              + Net.q(genre.toLowerCase())
              + "&quality="
              + Net.q(quality);
    } else
      path =
          kind
              + "/"
              + page
              + "?sort="
              + Net.q(sort)
              + "&keywords="
              + Net.q(query)
              + "&locale=en&contentLocale=en&showAll=1"
              + (genre.equals("All") ? "" : "&genre=" + Net.q(genre.toLowerCase()));
    String body = Net.text(base + path);
    Object obj = new JSONTokener(body).nextValue();
    JSONArray a;
    if (obj instanceof JSONArray) a = (JSONArray) obj;
    else {
      JSONObject o = (JSONObject) obj;
      if (yts) {
        JSONObject data = o.optJSONObject("data");
        a = data == null ? null : data.optJSONArray("movies");
      } else {
        a = o.optJSONArray("MovieList");
        if (a == null) a = o.optJSONArray("results");
        if (a == null) a = o.optJSONArray("items");
      }
    }
    if (a == null) throw new Exception("Server response is not a supported catalog");
    List<Title> out = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) out.add(parse(a.getJSONObject(i), kind));
    return out;
  }

  static Title parse(JSONObject o, String kind) throws Exception {
    Title t = new Title();
    t.raw = o;
    t.kind = kind;
    t.id =
        o.optString(
            "imdb",
            o.optString(
                "imdb_id", o.optString("imdb_code", o.optString("_id", o.optString("id")))));
    t.title = o.optString("title", o.optString("name", "Untitled"));
    t.year = o.optString("year", "");
    t.synopsis =
        o.optString("synopsis", o.optString("description_full", o.optString("description", "")));
    JSONObject images = o.optJSONObject("images");
    t.poster =
        images == null
            ? o.optString("large_cover_image", o.optString("poster_big", ""))
            : images.optString("poster", "");
    t.poster = Artwork.primary(o);
    collect(o.opt("torrents"), "", t.sources);
    collect(o.opt("items"), "", t.sources);
    collect(o.opt("items_lang"), "", t.sources);
    return t;
  }

  static void collect(Object obj, String label, List<Source> out) throws Exception {
    if (obj instanceof JSONArray) {
      JSONArray a = (JSONArray) obj;
      for (int i = 0; i < a.length(); i++) collect(a.get(i), label, out);
    } else if (obj instanceof JSONObject) {
      JSONObject o = (JSONObject) obj;
      String u = "";
      for (String key : new String[] {"magnet", "torrent_magnet", "url", "torrent_url"}) {
        String value = o.optString(key, "");
        if (!value.isEmpty()) {
          u = value;
          break;
        }
      }
      String hash = o.optString("hash", "");
      if (u.isEmpty() && !hash.isEmpty()) u = "magnet:?xt=urn:btih:" + hash;
      if (!u.isEmpty()
          && (u.startsWith("magnet:") || u.startsWith("https://") || u.startsWith("http://"))) {
        if (ReleaseQuality.camera(
            o.optString("file", o.optString("filename", o.optString("name"))))) return;
        String q = o.optString("quality", label);
        out.add(
            new Source(
                (q.isEmpty() ? "Play" : q)
                    + (o.has("type") ? " · " + o.optString("type") : "")
                    + (o.has("video_codec") ? " · " + o.optString("video_codec") : ""),
                u,
                o.optInt("seeds", o.optInt("seed", o.optInt("torrent_seeds", 0)))));
      } else {
        Iterator<String> keys = o.keys();
        while (keys.hasNext()) {
          String k = keys.next();
          collect(o.opt(k), label.isEmpty() ? k : label + " · " + k, out);
        }
      }
    }
  }

  static boolean legacy(String base) {
    return base.contains("apiabcd.com");
  }

  static String compat() {
    return "app_id=T4P_AND&os=android14&ver=3.6.10&device_type=phone";
  }

  static JSONObject details(String base, Title t) throws Exception {
    if (t.kind.equals("movies") && (legacy(base) || t.raw.optBoolean("freshMovie"))) {
      JSONObject m = FreshMovies.find(t.id);
      if (m != null) return m;
    }
    if (t.raw.optBoolean("regionalCatalog") && !t.id.matches("tt[0-9]+"))
      throw new Exception(
          "This Hindi title is listed, but the selected torrent provider has no matched playback"
              + " source yet.");
    String path =
        legacy(base)
            ? (t.kind.equals("movies") ? "movie" : "show")
                + "?imdb="
                + Net.q(t.id)
                + "&"
                + compat()
                + "&quality=360p,480p,576p,720p,1080p,2160p"
            : (t.kind.equals("movies") ? "movie/" : t.kind.equals("animes") ? "anime/" : "show/")
                + Net.q(t.id)
                + "?locale=en&contentLocale=en";
    return CatalogHttp.object(base.replaceAll("/+$", "") + "/" + path, null);
  }

  static JSONArray episodes(JSONObject obj) throws Exception {
    JSONArray result = obj.optJSONArray("episodes");
    if (result != null) return result;
    result = new JSONArray();
    Iterator<String> keys = obj.keys();
    while (keys.hasNext()) {
      String k = keys.next();
      JSONArray season = obj.optJSONArray(k);
      if (season != null)
        for (int i = 0; i < season.length(); i++) {
          JSONObject ep = season.getJSONObject(i);
          if (!ep.has("season")) ep.put("season", k);
          result.put(ep);
        }
    }
    return result;
  }
}
