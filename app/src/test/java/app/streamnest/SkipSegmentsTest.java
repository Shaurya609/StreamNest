package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class SkipSegmentsTest {
  @Test
  public void identityConfidenceAndBoundsAreRequired() throws Exception {
    JSONObject intro =
        new JSONObject()
            .put("start_ms", 30000)
            .put("end_ms", 120000)
            .put("confidence", 0.9)
            .put("submission_count", 4);
    JSONObject doc =
        new JSONObject()
            .put("imdb_id", "tt123")
            .put("season", 1)
            .put("episode", 2)
            .put("intro", intro);
    assertEquals(1, SkipSegments.introdb(doc, "tt123", 1, 2, 1440000).size());
    assertTrue(SkipSegments.introdb(doc, "tt999", 1, 2, 1440000).isEmpty());
    assertTrue(SkipSegments.introdb(doc, "tt123", 2, 2, 1440000).isEmpty());
    intro.put("confidence", 0.3);
    assertTrue(SkipSegments.introdb(doc, "tt123", 1, 2, 1440000).isEmpty());
    assertNull(SkipSegments.range("intro", 0, 900000, 1440000));
    assertNull(SkipSegments.range("outro", 1300000, 1600000, 1440000));
    assertNull(SkipSegments.range("intro", Double.NaN, 90000, 1440000));
  }

  @Test
  public void creditsJumpPreservesLaterSceneAndButtonOnlyAppearsWithinSegment() {
    SkipSegments.Segment s = SkipSegments.range("outro", 1200000, 1300000, 1440000);
    assertNotNull(s);
    assertEquals(1300000, s.end);
    List<SkipSegments.Segment> rows = Arrays.asList(s);
    assertNull(SkipSegments.active(rows, 1199000));
    assertSame(s, SkipSegments.active(rows, 1200000));
    assertNull(SkipSegments.active(rows, 1300000));
  }

  @Test
  public void animeDurationMismatchIsRejected() throws Exception {
    JSONObject doc =
        new JSONObject(
            "{\"found\":true,\"results\":[{\"skipType\":\"op\",\"episodeLength\":1440,\"interval\":{\"startTime\":30,\"endTime\":120}}]}");
    assertEquals(1, SkipSegments.aniskip(doc, 1440000).size());
    assertTrue(SkipSegments.aniskip(doc, 1600000).isEmpty());
  }

  @Test
  public void animeSkipIdentityMustComeFromExplicitCanonicalMapping() throws Exception {
    JSONObject doc =
        new JSONObject(
            "{\"mappings\":{\"imdb_id\":\"tt123\",\"mal_id\":21},\"episodes\":{\"1\":{\"seasonNumber\":1,\"episodeNumber\":1},\"2\":{\"seasonNumber\":2,\"episodeNumber\":1}}}");
    JSONArray rows = CanonicalEpisodes.fromMappings(doc, "tt123");
    assertEquals(21, rows.getJSONObject(0).optInt("skipMalId"));
    assertEquals(0, rows.getJSONObject(1).optInt("skipMalId"));
  }
}
