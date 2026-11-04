package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class SeriesIndexTest {
  JSONObject s(
      String id, String title, int year, double rating, int trend, int latest, String popularity)
      throws Exception {
    return new JSONObject()
        .put("imdb", id)
        .put("title", title)
        .put("year", year)
        .put("rating", rating)
        .put("trendRank", trend)
        .put("latestRank", latest)
        .put("popularity", popularity)
        .put("language", "en");
  }

  List<JSONObject> pool() throws Exception {
    return Arrays.asList(
        s("tt1", "Zulu", 2025, 9, 3, 2, "100-0-0"),
        s("tt2", "Alpha", 2026, 6, 1, 3, "50-0-0"),
        s("tt3", "Beta", 2024, 8, 2, 1, "25-0-0"));
  }

  String first(String sort, int order) throws Exception {
    return SeriesIndex.select(pool(), 1, "", sort, "All", "all", order).get(0).id;
  }

  @Test
  public void sixSortsUseSeriesMetadataInsteadOfBrokenServerSorting() throws Exception {
    assertEquals("tt2", first("trending", -1));
    assertEquals("tt1", first("popularity", -1));
    assertEquals("tt3", first("last added", -1));
    assertEquals("tt2", first("year", -1));
    assertEquals("tt2", first("title", 1));
    assertEquals("tt1", first("rating", -1));
  }

  @Test
  public void reverseAndLanguageFiltersWork() throws Exception {
    assertEquals("tt1", first("title", -1));
    assertTrue(SeriesIndex.select(pool(), 1, "", "year", "All", "hi", -1).isEmpty());
  }

  @Test
  public void popularityIsNumericRatherThanLexicographic() throws Exception {
    assertTrue(
        SeriesIndex.popularity(new JSONObject().put("popularity", "100-0-0"))
            > SeriesIndex.popularity(new JSONObject().put("popularity", "99-0-0")));
  }

  @Test
  public void regionalShowsAreVisibleInMixedTrendingAndStayHindi() throws Exception {
    List<JSONObject> rows = new ArrayList<>();
    for (int i = 1; i <= 50; i++) rows.add(s("tt" + i, "English " + i, 2025, 7, i, i, "10-0-0"));
    JSONObject hindi =
        s("tt999", "Hindi series", 2026, 8, 10000, 10000, "0-0-0").put("language", "hi");
    rows.add(hindi);
    assertTrue(
        SeriesIndex.select(rows, 1, "", "trending", "All", "all", -1).stream()
            .anyMatch(t -> t.id.equals("tt999")));
    assertEquals("tt999", SeriesIndex.select(rows, 1, "", "trending", "All", "hi", -1).get(0).id);
    assertEquals(10000, hindi.getInt("trendRank"));
  }
}
