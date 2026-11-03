package app.streamnest;

import static org.junit.Assert.*;

import java.nio.file.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class SeriesFullIndexTest {
  @Test
  public void bundledCompleteIndexCanBrowsePastHundredPagesWithoutDuplicates() throws Exception {
    Path p = Paths.get("src/main/assets/series-index-v6.json");
    JSONObject doc =
        new JSONObject(new String(Files.readAllBytes(p), java.nio.charset.StandardCharsets.UTF_8));
    JSONArray a = doc.getJSONArray("series");
    List<JSONObject> rows = new ArrayList<>();
    for (int i = 0; i < a.length(); i++) rows.add(a.getJSONObject(i));
    assertTrue(rows.size() > 8000);
    Set<String> seen = new HashSet<>();
    for (int page = 1; page <= 110; page++)
      for (Catalog.Title t : SeriesIndex.select(rows, page, "", "title", "All", "all", 1))
        assertTrue(seen.add(t.id));
    assertEquals(2640, seen.size());
  }

  @Test
  public void animeIdentityUsesCanonicalParentTitleAndRejectsWesternAnimation() throws Exception {
    JSONObject meta = new JSONObject().put("imdb_id", "tt123").put("name", "Future Season 9"),
        source =
            new JSONObject()
                .put(
                    "episodes",
                    new JSONArray()
                        .put(
                            new JSONObject()
                                .put(
                                    "items",
                                    new JSONArray()
                                        .put(
                                            new JSONObject()
                                                .put("quality", "1080p")
                                                .put("torrent_magnet", "magnet:test"))))),
        canonical =
            new JSONObject()
                .put("type", "Animation")
                .put("language", "Japanese")
                .put("name", "Canonical Series")
                .put("premiered", "2020-01-01")
                .put("genres", new JSONArray().put("Anime"));
    assertEquals(
        "Canonical Series", AnimeCatalog.identity(meta, source, canonical).getString("title"));
    canonical.put("language", "English").put("genres", new JSONArray().put("Comedy"));
    assertNull(AnimeCatalog.identity(meta, source, canonical));
  }
}
