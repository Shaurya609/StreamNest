package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class AlternateEpisodesTest {
  JSONObject record(String quality, int index) throws Exception {
    return new JSONObject()
        .put("name", "Torrentio\n" + quality)
        .put("title", "Show.S01E05.mkv\n👤 30")
        .put("infoHash", "a".repeat(40))
        .put("fileIdx", index)
        .put("behaviorHints", new JSONObject().put("filename", "Show.S01E05.mkv"));
  }

  @Test
  public void parsesIndexedPackAndPreservesMemberThroughSerialization() throws Exception {
    List<Catalog.Source> rows =
        AlternateEpisodes.parse(
            new JSONObject().put("streams", new JSONArray().put(record("720p", 4))));
    assertEquals(1, rows.size());
    assertEquals(30, rows.get(0).seeds);
    Catalog.Source result = AlternateEpisodes.decode(AlternateEpisodes.encode(rows)).get(0);
    assertEquals(4, result.fileIndex);
    assertEquals("Show.S01E05.mkv", result.filename);
    assertTrue(result.url.startsWith("magnet:?xt=urn:btih:"));
  }

  @Test
  public void ignoresDirectLinksInvalidHashesCameraAndMissingMembers() throws Exception {
    JSONArray rows =
        new JSONArray()
            .put(record("720p", -1))
            .put(record("720p CAM", 3))
            .put(record("720p", 2).put("infoHash", "bad"))
            .put(new JSONObject().put("url", "https://example/video"));
    assertTrue(AlternateEpisodes.parse(new JSONObject().put("streams", rows)).isEmpty());
  }

  @Test
  public void manualQualityNeverFallsBackToDifferentResolution() throws Exception {
    Catalog.Source hd = new Catalog.Source("1080p", "hd", 0),
        low = new Catalog.Source("720p", "low", 50);
    assertEquals(
        Arrays.asList(hd), AlternateEpisodes.attempts(Arrays.asList(hd, low), "1080p", false));
    assertEquals(low, AlternateEpisodes.attempts(Arrays.asList(hd, low), "Auto", false).get(0));
  }
}
