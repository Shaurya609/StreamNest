package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class ReleaseQualityTest {
  JSONObject ep(int s, int e, String quality, String file) throws Exception {
    return new JSONObject()
        .put("season", s)
        .put("episode", e)
        .put("title", "Episode")
        .put(
            "items",
            new JSONArray()
                .put(
                    new JSONObject()
                        .put("quality", quality)
                        .put("file", file)
                        .put("torrent_magnet", "magnet:test")));
  }

  @Test
  public void cameraSourcesAreRejectedEvenWhenClaimingHd() {
    assertFalse(ReleaseQuality.hd("1080p", "Movie.1080p.HDCAM.mkv"));
    assertFalse(ReleaseQuality.hd("2160p", "Movie.Telesync.mkv"));
    assertTrue(ReleaseQuality.hd("1080p", "Movie.1080p.WEB-DL.mkv"));
    assertFalse(ReleaseQuality.hd("720p", "Movie.WEB.mkv"));
  }

  @Test
  public void episodePickerFiltersHdAndSortsActualSeasonNumbers() throws Exception {
    JSONObject d =
        new JSONObject()
            .put(
                "episodes",
                new JSONArray()
                    .put(ep(10, 2, "1080p", "clean.mkv"))
                    .put(ep(2, 5, "720p", "clean.mkv"))
                    .put(ep(2, 4, "2160p", "clean.mkv"))
                    .put(ep(1, 1, "1080p", "file.HDCAM.mkv")));
    List<JSONObject> a = ReleaseQuality.episodes(d, "All");
    assertEquals(2, a.size());
    assertEquals(2, a.get(0).getInt("season"));
    assertEquals(10, a.get(1).getInt("season"));
    assertEquals(1, ReleaseQuality.episodes(d, "2160p").size());
  }

  @Test
  public void duplicateMovieRecordsKeepBothHdQualities() throws Exception {
    JSONObject
        a =
            new JSONObject()
                .put(
                    "torrents",
                    new JSONArray().put(new JSONObject().put("hash", "a").put("quality", "1080p"))),
        b =
            new JSONObject()
                .put(
                    "torrents",
                    new JSONArray().put(new JSONObject().put("hash", "b").put("quality", "2160p")));
    PagedMovies.merge(a, b);
    PagedMovies.merge(a, b);
    assertEquals(2, a.getJSONArray("torrents").length());
  }

  @Test
  public void unknownSeriesRatingsRemainLastInBothDirections() throws Exception {
    List<JSONObject> a =
        Arrays.asList(
            new JSONObject().put("imdb", "tt1").put("title", "Unknown").put("rating", 0),
            new JSONObject().put("imdb", "tt2").put("title", "Known").put("rating", 8));
    for (int order : new int[] {-1, 1})
      assertEquals("tt2", SeriesIndex.select(a, 1, "", "rating", "All", "all", order).get(0).id);
  }
}
