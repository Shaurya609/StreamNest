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
}
