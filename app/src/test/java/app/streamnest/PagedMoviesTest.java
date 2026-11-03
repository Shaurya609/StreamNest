package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.*;

public class PagedMoviesTest {
  PagedMovies.Fetcher original;

  @Before
  public void reset() {
    original = PagedMovies.fetcher;
    PagedMovies.feeds.clear();
  }

  @After
  public void cleanup() {
    PagedMovies.fetcher = original;
    PagedMovies.feeds.clear();
  }

  JSONObject movie(int id, String lang, String quality) throws Exception {
    return new JSONObject()
        .put("imdb_code", "tt" + id)
        .put("title", "Title " + id)
        .put("language", lang)
        .put("genres", new JSONArray().put("Animation"))
        .put(
            "torrents",
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("quality", quality)
                        .put("type", "web")
                        .put("hash", "ABC")));
  }

  @Test
  public void loadsBeyondOldPoolAndKeepsEarlierPagesStable() throws Exception {
    PagedMovies.fetcher =
        path -> {
          int page = Integer.parseInt(path.substring(path.lastIndexOf("=") + 1));
          JSONArray a = new JSONArray();
          for (int i = 0; i < 50; i++) a.put(movie((page - 1) * 50 + i, "en", "1080p"));
          return new JSONObject().put("movies", a).put("movie_count", 1000);
        };
    Set<String> ids = new HashSet<>();
    for (int p = 1; p <= 12; p++)
      for (Catalog.Title t : PagedMovies.list(p, "", "trending", "All", "All", "all", -1, false))
        assertTrue(ids.add(t.id));
    assertEquals(288, ids.size());
    assertEquals(
        "tt0", PagedMovies.list(1, "", "trending", "All", "All", "all", -1, false).get(0).id);
  }

  @Test
  public void scansPastForeignLanguagePagesWithoutFalseEnd() throws Exception {
    PagedMovies.fetcher =
        path -> {
          int p = Integer.parseInt(path.substring(path.lastIndexOf("=") + 1));
          JSONArray a = new JSONArray();
          for (int i = 0; i < 50; i++) a.put(movie(p * 50 + i, p < 4 ? "fr" : "hi", "1080p"));
          return new JSONObject().put("movies", a).put("movie_count", 250);
        };
    List<Catalog.Title> a = PagedMovies.list(1, "", "title", "All", "All", "hi", 1, false);
    assertEquals(24, a.size());
    assertEquals("tt200", a.get(0).id);
  }

  @Test
  public void sendsSortDirectionSearchAndGenreToProvider() throws Exception {
    PagedMovies.fetcher =
        path -> {
          assertTrue(path.contains("sort_by=rating"));
          assertTrue(path.contains("order_by=asc"));
          assertTrue(path.contains("query_term=hello+world"));
          assertTrue(path.contains("genre=Drama"));
          return new JSONObject();
        };
    assertTrue(
        PagedMovies.list(1, "hello world", "rating", "Drama", "All", "all", 1, false).isEmpty());
  }

  @Test
  public void animeRequiresJapaneseAnimationAndSelectedHdSource() throws Exception {
    PagedMovies.fetcher =
        path -> {
          assertTrue(path.contains("genre=Animation"));
          return new JSONObject()
              .put(
                  "movies",
                  new JSONArray()
                      .put(movie(1, "en", "2160p"))
                      .put(movie(2, "ja", "720p"))
                      .put(movie(3, "ja", "2160p")));
        };
    List<Catalog.Title> a = PagedMovies.list(1, "", "year", "All", "2160p", "all", -1, true);
    assertEquals(1, a.size());
    assertEquals("tt3", a.get(0).id);
    assertEquals("movies", a.get(0).kind);
  }

  @Test
  public void ranksRecentTrendingWindowBySeedsInsteadOfUnsupportedServerSort() throws Exception {
    PagedMovies.fetcher =
        path -> {
          assertTrue(path.contains("sort_by=date_added"));
          JSONObject a = movie(1, "eng", "1080p"), b = movie(2, "en", "1080p");
          a.getJSONArray("torrents").getJSONObject(0).put("seeds", 1);
          b.getJSONArray("torrents").getJSONObject(0).put("seeds", 20);
          return new JSONObject().put("movies", new JSONArray().put(a).put(b));
        };
    List<Catalog.Title> rows = PagedMovies.list(1, "", "trending", "All", "All", "all", -1, false);
    assertEquals(2, rows.size());
    assertEquals("tt2", rows.get(0).id);
  }
}
