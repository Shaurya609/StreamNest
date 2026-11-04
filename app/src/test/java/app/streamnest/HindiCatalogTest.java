package app.streamnest;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class HindiCatalogTest {
  @Test
  public void pagesAdvanceForMovies() {
    assertEquals("catalog/movie/hi.json", HindiCatalog.path("movies", 1));
    assertEquals("catalog/movie/hi/skip=20.json", HindiCatalog.path("movies", 2));
  }

  @Test
  public void usesDedicatedHindiSeriesFeed() {
    assertEquals("catalog/series/hi_series/skip=40.json", HindiCatalog.path("shows", 3));
  }

  @Test
  public void preservesLiveIdentifiersAndPosters() throws Exception {
    JSONObject movie =
        new JSONObject()
            .put("id", "tt12345")
            .put("name", "New Hindi title")
            .put("poster", "https://example.com/poster.jpg")
            .put("releaseInfo", "2026");
    java.util.List<Catalog.Title> titles =
        HindiCatalog.parse(new JSONObject().put("metas", new JSONArray().put(movie)), "movies");
    assertEquals(1, titles.size());
    assertEquals("tt12345", titles.get(0).id);
    assertTrue(titles.get(0).raw.getBoolean("regionalCatalog"));
    assertEquals("https://example.com/poster.jpg", titles.get(0).poster);
  }

  @Test
  public void keepsTmdbOnlyTitlesWithoutInventingImdbIds() throws Exception {
    JSONObject movie = new JSONObject().put("id", "tmdb:123").put("name", "New series");
    assertEquals(
        "tmdb:123",
        HindiCatalog.parse(new JSONObject().put("metas", new JSONArray().put(movie)), "shows")
            .get(0)
            .id);
  }

  @Test
  public void confirmedDiscoveryRejectsCameraLowQualityAndEnglishRecords() throws Exception {
    JSONObject record =
        new JSONObject()
            .put("language", "hin")
            .put("imdb_code", "tt1")
            .put(
                "torrents",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("quality", "1080p")
                            .put("type", "web")
                            .put("hash", "abc")));
    assertTrue(MovieIndex.confirmedHindi(FreshMovies.normalize(record)));
    record.put("language", "en");
    assertFalse(MovieIndex.confirmedHindi(FreshMovies.normalize(record)));
    record.put("language", "hi");
    record.getJSONArray("torrents").getJSONObject(0).put("quality", "720p");
    assertFalse(MovieIndex.confirmedHindi(FreshMovies.normalize(record)));
    record
        .getJSONArray("torrents")
        .getJSONObject(0)
        .put("quality", "1080p")
        .put("name", "Movie HDCAM");
    assertFalse(MovieIndex.confirmedHindi(FreshMovies.normalize(record)));
  }

  @Test
  public void mixedPagesKeepEveryEnglishAndHindiCardWithoutSkipping() throws Exception {
    java.util.List<JSONObject> old = MovieIndex.snapshot;
    long age = MovieIndex.refreshed;
    PagedMovies.Fetcher fetcher = PagedMovies.fetcher;
    try {
      MovieIndex.snapshot = new java.util.ArrayList<>();
      MovieIndex.refreshed = System.currentTimeMillis();
      for (int i = 0; i < 40; i++)
        MovieIndex.snapshot.add(
            new JSONObject()
                .put("imdb_code", "tt" + (9000 + i))
                .put("title", "Hindi " + i)
                .put("language", "hi")
                .put(
                    "torrents",
                    new JSONArray()
                        .put(
                            new JSONObject()
                                .put("quality", "1080p")
                                .put("type", "web")
                                .put("hash", "h" + i)
                                .put("seeds", 100 - i))));
      PagedMovies.feeds.clear();
      PagedMovies.fetcher =
          path -> {
            int page = Integer.parseInt(path.substring(path.lastIndexOf("&page=") + 6));
            JSONArray rows = new JSONArray();
            for (int i = 0; i < 50; i++) {
              int id = (page - 1) * 50 + i;
              rows.put(
                  new JSONObject()
                      .put("imdb_code", "tt" + (1000 + id))
                      .put("title", "English " + id)
                      .put("language", "en")
                      .put(
                          "torrents",
                          new JSONArray()
                              .put(
                                  new JSONObject()
                                      .put("quality", "1080p")
                                      .put("type", "web")
                                      .put("hash", "e" + id)
                                      .put("seeds", 1000 - id))));
            }
            return new JSONObject().put("movie_count", 1000).put("movies", rows);
          };
      java.util.Set<String> ids = new java.util.HashSet<>();
      for (int page = 1; page <= 5; page++) {
        java.util.List<Catalog.Title> rows =
            HindiCatalog.mixedMovies(page, "trending", "All", "All", -1);
        assertEquals(24, rows.size());
        assertEquals("hi", rows.get(3).raw.getString("language"));
        for (Catalog.Title row : rows) assertTrue(ids.add(row.id));
      }
      assertEquals(120, ids.size());
      assertTrue(ids.contains("tt1089"));
      assertTrue(ids.contains("tt9029"));
    } finally {
      MovieIndex.sessions.clear();
      MovieIndex.snapshot = old;
      MovieIndex.refreshed = age;
      PagedMovies.fetcher = fetcher;
      PagedMovies.feeds.clear();
    }
  }
}
