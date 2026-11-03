package app.streamnest;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class CatalogTest {
  @Test
  public void parsesApkMovieWithEmptyTorrentUrl() throws Exception {
    JSONObject item =
        new JSONObject()
            .put("quality", "1080p")
            .put("torrent_magnet", "magnet:?xt=urn:btih:0123456789012345678901234567890123456789")
            .put("torrent_url", "")
            .put("torrent_seeds", 12);
    JSONObject movie =
        new JSONObject()
            .put("imdb", "tt123")
            .put("id", "123")
            .put("title", "Fixture movie")
            .put("items", new JSONArray().put(item));
    Catalog.Title t = Catalog.parse(movie, "movies");
    assertEquals("tt123", t.id);
    assertEquals(1, t.sources.size());
    assertEquals(12, t.sources.get(0).seeds);
    assertTrue(t.sources.get(0).url.startsWith("magnet:"));
  }

  @Test
  public void fallsBackFromEmptyMagnetToTorrentFile() throws Exception {
    JSONObject o =
        new JSONObject().put("magnet", "").put("torrent_url", "https://example.org/test.torrent");
    java.util.List<Catalog.Source> out = new java.util.ArrayList<>();
    Catalog.collect(o, "720p", out);
    assertEquals(1, out.size());
    assertEquals("https://example.org/test.torrent", out.get(0).url);
  }

  @Test
  public void parsesNestedLanguageQualities() throws Exception {
    JSONObject torrent = new JSONObject().put("url", "magnet:?xt=urn:btih:test").put("seed", 7);
    JSONObject movie =
        new JSONObject()
            .put("imdb_id", "tt123")
            .put("torrents", new JSONObject().put("en", new JSONObject().put("1080p", torrent)));
    Catalog.Title t = Catalog.parse(movie, "movies");
    assertEquals(1, t.sources.size());
    assertTrue(t.sources.get(0).label.contains("1080p"));
  }

  @Test
  public void flattensSeasonKeysWithoutLosingEpisodes() throws Exception {
    JSONObject a = new JSONObject().put("episode", 1);
    JSONObject b = new JSONObject().put("episode", 2);
    JSONObject response = new JSONObject().put("1", new JSONArray().put(a).put(b));
    JSONArray episodes = Catalog.episodes(response);
    assertEquals(2, episodes.length());
    assertEquals(1, episodes.getJSONObject(0).getInt("season"));
  }

  @Test
  public void leavesStandardEpisodeArraysIntact() throws Exception {
    JSONObject response =
        new JSONObject()
            .put(
                "episodes",
                new JSONArray().put(new JSONObject().put("season", 2).put("episode", 3)));
    assertEquals(3, Catalog.episodes(response).getJSONObject(0).getInt("episode"));
  }
}
