package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class EpisodeLibraryTest {
  JSONObject meta(int id, int season, int episode) throws Exception {
    return new JSONObject()
        .put("id", id)
        .put("season", season)
        .put("number", episode)
        .put("name", "Canonical " + episode)
        .put("summary", "<p>Synopsis</p>");
  }

  @Test
  public void missingHdEpisodesRemainInCanonicalOrder() throws Exception {
    JSONArray metadata = new JSONArray().put(meta(3, 1, 3)).put(meta(1, 1, 1)).put(meta(2, 1, 2));
    JSONObject src =
        new JSONObject()
            .put(
                "episodes",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("season", 1)
                            .put("episode", 2)
                            .put("title", "Source title")
                            .put(
                                "items",
                                new JSONArray()
                                    .put(
                                        new JSONObject()
                                            .put("quality", "1080p")
                                            .put("torrent_magnet", "magnet:test")))));
    List<JSONObject> a = EpisodeLibrary.merge(metadata, src);
    assertEquals(3, a.size());
    assertEquals("Canonical 2", a.get(1).getString("title"));
    assertTrue(PlaybackPlan.qualities(PlaybackPlan.sources(a.get(0))).isEmpty());
    assertEquals(Arrays.asList("1080p"), PlaybackPlan.qualities(PlaybackPlan.sources(a.get(1))));
  }

  @Test
  public void unnumberedSpecialsKeepSeparateIdentitiesAndRegularSeasonsComeFirst()
      throws Exception {
    JSONObject first = meta(100, 0, 0), second = meta(101, 0, 0);
    List<JSONObject> a =
        EpisodeLibrary.merge(
            new JSONArray().put(first).put(second).put(meta(1, 1, 1)), new JSONObject());
    assertEquals(3, a.size());
    assertEquals(1, a.get(0).getInt("season"));
    assertNotEquals(EpisodeLibrary.key(a.get(1)), EpisodeLibrary.key(a.get(2)));
  }

  @Test
  public void providerOnlyEpisodesKeepTheirNumbersWithoutRenumbering() throws Exception {
    JSONObject src =
        new JSONObject()
            .put(
                "episodes",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("season", 2)
                            .put("episode", 12)
                            .put("title", "Source")));
    List<JSONObject> a = EpisodeLibrary.merge(new JSONArray().put(meta(1, 1, 1)), src);
    assertEquals(2, a.size());
    assertEquals(12, a.get(1).getInt("episode"));
  }
}
