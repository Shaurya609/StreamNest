package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class BrowseOrderTest {
  JSONObject movie(
      String id,
      String title,
      String language,
      int year,
      double rating,
      int seeds,
      long uploaded,
      int rank)
      throws Exception {
    return new JSONObject()
        .put("imdb_code", id)
        .put("title", title)
        .put("language", language)
        .put("year", year)
        .put("rating", rating)
        .put("date_uploaded_unix", uploaded)
        .put("popularityRank", rank)
        .put("genres", new JSONArray().put("Drama"))
        .put(
            "torrents",
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("quality", "1080p")
                        .put("type", "web")
                        .put("hash", "0123456789abcdef0123456789abcdef01234567")
                        .put("seeds", seeds)));
  }

  List<JSONObject> pool() throws Exception {
    return Arrays.asList(
        movie("tt1", "Zulu", "en", 2025, 9, 3, 10, 1),
        movie("tt2", "Alpha", "hi", 2026, 6, 50, 20, 3),
        movie("tt3", "Beta", "en", 2024, 8, 10, 30, 2));
  }

  String first(String sort) throws Exception {
    return BrowseOrder.select(pool(), 1, "", sort, "All", "All", "all").get(0).id;
  }

  @Test
  public void allSixSortsUseDistinctMetrics() throws Exception {
    assertEquals("tt2", first("trending"));
    assertEquals("tt1", first("popularity"));
    assertEquals("tt3", first("last added"));
    assertEquals("tt2", first("year"));
    assertEquals("tt2", first("title"));
    assertEquals("tt1", first("rating"));
  }

  @Test
  public void mainTrendingIncludesBothLanguages() throws Exception {
    List<Catalog.Title> t = BrowseOrder.select(pool(), 1, "", "trending", "All", "All", "all");
    assertEquals(3, t.size());
    assertEquals("hi", t.get(0).raw.getString("language"));
  }

  @Test
  public void filtersIntersectAndHindiKeepsSort() throws Exception {
    assertEquals(
        "tt2", BrowseOrder.select(pool(), 1, "Alpha", "rating", "Drama", "1080p", "hi").get(0).id);
    assertTrue(BrowseOrder.select(pool(), 1, "", "rating", "Comedy", "All", "all").isEmpty());
    assertTrue(BrowseOrder.select(pool(), 1, "", "rating", "All", "2160p", "all").isEmpty());
  }

  @Test
  public void sortingHappensBeforePagination() throws Exception {
    List<JSONObject> list = new ArrayList<>();
    for (int i = 0; i < 30; i++)
      list.add(movie("tt" + (100 + i), "Title" + i, "en", 2000 + i, 5, i, i, 30 - i));
    List<Catalog.Title> first = BrowseOrder.select(list, 1, "", "year", "All", "All", "all"),
        second = BrowseOrder.select(list, 2, "", "year", "All", "All", "all");
    assertEquals(24, first.size());
    assertEquals("2029", first.get(0).year);
    assertEquals("2006", first.get(23).year);
    assertEquals(6, second.size());
    assertEquals("2005", second.get(0).year);
  }

  @Test
  public void unsupportedLanguagesAndDuplicatesAreExcluded() throws Exception {
    List<JSONObject> list = new ArrayList<>(pool());
    list.add(list.get(0));
    list.add(movie("tt4", "Other", "fr", 2026, 10, 999, 100, 0));
    assertEquals(3, BrowseOrder.select(list, 1, "", "trending", "All", "All", "all").size());
  }

  @Test
  public void selectingReverseDirectionReversesCatalogBeforePaging() throws Exception {
    List<Catalog.Title> asc = BrowseOrder.select(pool(), 1, "", "trending", "All", "All", "all", 1);
    assertEquals("tt1", asc.get(0).id);
    List<Catalog.Title> desc = BrowseOrder.select(pool(), 1, "", "title", "All", "All", "all", -1);
    assertEquals("Zulu", desc.get(0).title);
  }
}
