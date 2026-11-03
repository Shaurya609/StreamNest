package app.streamnest;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class FreshMoviesTest {
  JSONObject source(String quality, String type) throws Exception {
    return new JSONObject()
        .put("quality", quality)
        .put("type", type)
        .put("hash", "0123456789abcdef0123456789abcdef01234567");
  }

  @Test
  public void excludesCamAndSubHd() throws Exception {
    assertFalse(FreshMovies.hd(source("1080p", "cam")));
    assertFalse(FreshMovies.hd(source("720p", "web")));
    assertTrue(FreshMovies.hd(source("2160p", "web")));
  }

  @Test
  public void normalizesOnlyHdSourcesWithTrackers() throws Exception {
    JSONObject m =
        new JSONObject()
            .put("title", "A & B")
            .put("imdb_code", "tt123")
            .put(
                "torrents",
                new JSONArray().put(source("720p", "web")).put(source("1080p", "bluray")));
    Catalog.Title t = Catalog.parse(FreshMovies.normalize(m), "movies");
    assertEquals(1, t.sources.size());
    assertEquals("tt123", t.id);
    assertTrue(t.sources.get(0).url.contains("&tr="));
    assertTrue(t.raw.optBoolean("freshMovie"));
  }

  @Test
  public void missingSourcesNeverInvented() throws Exception {
    assertEquals(
        0,
        FreshMovies.normalize(new JSONObject().put("title", "Unavailable"))
            .getJSONArray("torrents")
            .length());
  }
}
