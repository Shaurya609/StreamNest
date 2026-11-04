package app.streamnest;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Network faults are injected into metadata adapters; device networking is not disabled. */
final class CatalogRegression {
  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  static Bundle run(Instrumentation test) throws Exception {
    Context context = test.getTargetContext();
    String name = "catalog-regression-" + System.nanoTime() + ".db";
    File folder = new File(context.getCacheDir(), "catalog-regression-" + System.nanoTime());
    folder.mkdirs();
    MovieRepository repository = new MovieRepository(context, name), reopened = null;
    MovieProvider.Fetcher oldMovies = MovieProvider.fetcher;
    MovieProvider.Store oldStore = MovieProvider.store;
    CatalogHttp.Fetcher oldHttp = CatalogHttp.fetcher;
    File oldHttpDirectory = CatalogHttp.directory;
    List<JSONObject> oldSeries = SeriesIndex.snapshot, oldAnime = AnimeCatalog.snapshot;
    long oldSeriesTime = SeriesIndex.refreshed, oldAnimeTime = AnimeCatalog.refreshed;
    File oldSeriesDisk = SeriesIndex.disk, oldLatest = AnimeReleases.snapshotFile;
    android.content.res.AssetManager oldAssets = AnimeReleases.assets;
    AnimeReleases.Fetcher oldRss = AnimeReleases.fetcher;
    Bundle result = new Bundle();
    try {
      repository.initialize(context);
      MovieProvider.store = repository;
      MovieProvider.health.reset();
      MovieProvider.forceLive.set(true);
      int[] calls = {0};
      MovieProvider.fetcher =
          url -> {
            calls[0]++;
            throw new IOException("Injected offline catalog");
          };
      PagedMovies.feeds.clear();
      CatalogStatus.begin();
      Set<String> ids = new HashSet<>();
      Catalog.Title first = null;
      for (int page = 1; page <= 5; page++) {
        List<Catalog.Title> rows =
            PagedMovies.list(page, "", "rating", "All", "All", "all", -1, false);
        require(rows.size() == 24, "Saved movie page is incomplete: " + page);
        for (Catalog.Title row : rows) {
          require(ids.add(row.id), "Repeated saved movie across pages");
          require(
              !PlaybackPlan.qualities(row.sources).isEmpty(), "Non-HD movie entered saved catalog");
          if (first == null) first = row;
        }
      }
      require(calls[0] == 3, "Cooling endpoints were probed repeatedly");
      require(CatalogStatus.note().contains("Saved"), "Saved catalog notice missing");
      result.putInt("offlineUniqueMovies", ids.size());
      String selectedId = first.id;
      List<Catalog.Title> search =
          PagedMovies.list(1, first.title, "title", "All", "All", "all", 1, false);
      require(search.stream().anyMatch(t -> t.id.equals(selectedId)), "Saved title search failed");
      result.putInt("offlineSearchMatches", search.size());
      JSONObject fixture =
          new JSONObject()
              .put("imdb_code", "tt999000001")
              .put("title", "Literal 100%_match")
              .put("language", "en")
              .put("genres", new JSONArray().put("Drama"))
              .put("rating", 10)
              .put("year", 2026)
              .put(
                  "torrents",
                  new JSONArray()
                      .put(
                          new JSONObject()
                              .put("quality", "1080p")
                              .put("type", "web")
                              .put("hash", "ffffffffffffffffffffffffffffffffffffffff")
                              .put("seeds", 5)));
      repository.remember(
          "list_movies.json?query_term=tt999000001",
          new JSONObject().put("movie_count", 1).put("movies", new JSONArray().put(fixture)));
      JSONObject literal = repository.saved("list_movies.json?query_term=100%25_&sort_by=title");
      require(
          literal.getJSONArray("movies").length() == 1,
          "Cached SQL search did not escape literal wildcards");
      MovieProvider.health.reset();
      MovieProvider.fetcher =
          url -> "{\"status\":\"ok\",\"data\":{\"movie_count\":0,\"movies\":[]}}";
      CatalogStatus.begin();
      JSONObject empty = MovieProvider.data("list_movies.json?query_term=NoSuchSavedTitleXYZ");
      require(
          empty.getJSONArray("movies").length() == 0 && !empty.optBoolean("catalog_saved"),
          "Legitimate live empty search was rejected");
      MovieProvider.health.reset();
      JSONObject protectedRoot =
          MovieProvider.data("list_movies.json?limit=50&page=1&quality=1080p");
      require(
          protectedRoot.optBoolean("catalog_saved")
              && protectedRoot.getJSONArray("movies").length() > 0,
          "Empty root response erased the bundled catalog");
      repository.close();
      reopened = new MovieRepository(context, name);
      reopened.initialize(context);
      require(
          reopened.saved("list_movies.json?query_term=100%25_").getJSONArray("movies").length()
              == 1,
          "Movie data did not survive closing and reopening the database");
      result.putBoolean("coldRestartSearch", true);
      CatalogHttp.configure(folder);
      CatalogHttp.health.reset();
      CatalogHttp.fetcher =
          url -> {
            if (url.contains("api.tvmaze.com/search"))
              return "[{\"show\":{\"name\":\"Independent Hindi Series\",\"language\":\"Hindi\",\"externals\":{\"imdb\":\"tt999000002\"},\"genres\":[\"Drama\"]}}]"
                  .getBytes(StandardCharsets.UTF_8);
            throw new IOException("Injected provider failure");
          };
      SeriesIndex.snapshot = new ArrayList<>();
      SeriesIndex.snapshot.add(
          new JSONObject()
              .put("imdb", "tt999000003")
              .put("title", "Existing cached series")
              .put("language", "en")
              .put("year", 2025));
      SeriesIndex.refreshed = System.currentTimeMillis();
      SeriesIndex.disk = null;
      SeriesIndex.sessions.clear();
      String base = "https://api.apiabcd.com/", query = "Existing cached";
      SeriesIndex.searchAttempts.put(base + "|" + query, System.currentTimeMillis());
      require(
          SeriesIndex.list(base, 1, query, "title", "All", "all", 1).size() == 1,
          "Cached series search depends on the server");
      List<Catalog.Title> independent =
          SeriesIndex.list(base, 1, "Independent Hindi", "title", "All", "hi", 1);
      require(
          independent.size() == 1 && independent.get(0).raw.optBoolean("metadataOnly"),
          "Independent Hindi metadata fallback failed");
      result.putInt("independentHindiSeries", independent.size());
      AnimeCatalog.snapshot = new ArrayList<>();
      AnimeCatalog.snapshot.add(
          new JSONObject()
              .put("imdb", "tt999000004")
              .put("title", "Cached anime")
              .put("language", "ja")
              .put("animeSeries", true)
              .put("verifiedQualities", new JSONArray().put("720p")));
      AnimeCatalog.refreshed = System.currentTimeMillis();
      AnimeCatalog.sessions.clear();
      require(
          AnimeCatalog.list(1, "Cached anime", "title", "All", "720p", 1).size() == 1,
          "Saved anime browsing failed");
      result.putBoolean("offlineAnime", true);
      AnimeReleases.configure(folder, context.getAssets());
      AnimeReleases.cache.clear();
      AnimeReleases.ages.clear();
      AnimeReleases.sessions.clear();
      AnimeReleases.health.reset();
      AnimeReleases.fetcher =
          (url, deadline) -> {
            throw new IOException("Injected RSS failure");
          };
      require(
          !AnimeReleases.list(1, "", "last added", "All", -1).isEmpty(),
          "Saved anime release feed failed");
      AnimeReleases.worker.submit(() -> {}).get(20, java.util.concurrent.TimeUnit.SECONDS);
      result.putBoolean("offlineAnimeReleases", true);
      String publicKey;
      try (InputStream in = context.getAssets().open("catalog-public-key.pem")) {
        publicKey = new String(Net.read(in, 10000), StandardCharsets.UTF_8);
      }
      JSONObject manifest = null;
      String mirror = "";
      Exception last = null;
      for (String server : CatalogSync.MIRRORS)
        try {
          manifest =
              CatalogSignature.manifest(
                  Net.catalogBytes(
                      server + "catalogs/v1/manifest.json",
                      100000,
                      System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)),
                  publicKey);
          mirror = server;
          break;
        } catch (Exception e) {
          last = e;
        }
      if (manifest == null) throw new IOException("Live signed mirror could not be verified", last);
      JSONObject metadata = manifest.getJSONObject("files").getJSONObject("movies");
      byte[] bytes =
          Net.catalogBytes(
              mirror + metadata.getString("path"),
              16000000,
              System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(15));
      CatalogSignature.verifyFile("movies", metadata, bytes);
      CatalogJson.inspect("movies", metadata, new ByteArrayInputStream(bytes));
      result.putInt("verifiedMirrorMovies", metadata.getInt("count"));
      result.putString(
          "result",
          "PASS: saved movies/search/paging, cold database reopen, empty-response protection,"
              + " independent Hindi series, saved anime and signed live mirror");
      return result;
    } finally {
      MovieProvider.fetcher = oldMovies;
      MovieProvider.store = oldStore;
      MovieProvider.forceLive.remove();
      MovieProvider.feedToken.remove();
      MovieProvider.health.reset();
      PagedMovies.feeds.clear();
      CatalogHttp.fetcher = oldHttp;
      CatalogHttp.directory = oldHttpDirectory;
      CatalogHttp.health.reset();
      SeriesIndex.snapshot = oldSeries;
      SeriesIndex.refreshed = oldSeriesTime;
      SeriesIndex.disk = oldSeriesDisk;
      SeriesIndex.sessions.clear();
      AnimeCatalog.snapshot = oldAnime;
      AnimeCatalog.refreshed = oldAnimeTime;
      AnimeCatalog.sessions.clear();
      AnimeReleases.fetcher = oldRss;
      AnimeReleases.snapshotFile = oldLatest;
      AnimeReleases.assets = oldAssets;
      AnimeReleases.cache.clear();
      AnimeReleases.ages.clear();
      AnimeReleases.sessions.clear();
      AnimeReleases.health.reset();
      if (reopened != null) reopened.close();
      else repository.close();
      context.deleteDatabase(name);
      CachePurger.deleteEntry(context.getCacheDir(), folder);
    }
  }
}
