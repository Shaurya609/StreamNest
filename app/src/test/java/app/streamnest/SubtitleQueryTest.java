package app.streamnest;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class SubtitleQueryTest {
  @Test
  public void movieUsesCanonicalImdbAndSortedParameters() {
    assertEquals(
        "subtitles?imdb_id=133093&languages=en",
        SubtitleQuery.search("tt0133093", 0, 0, "Ignored", "en", ""));
  }

  @Test
  public void episodeUsesParentNotMovieId() {
    String q = SubtitleQuery.search("tt6077448", 2, 3, "Sacred Games", "hi", "");
    assertEquals(
        "subtitles?episode_number=3&languages=hi&parent_imdb_id=6077448&season_number=2", q);
  }

  @Test
  public void manualStreamFallsBackToTitle() {
    assertEquals(
        "subtitles?languages=en&query=big+buck+bunny",
        SubtitleQuery.search("", 0, 0, "Big Buck Bunny", "en", ""));
  }

  @Test
  public void addsValidFileFingerprint() {
    assertTrue(
        SubtitleQuery.search("tt123", 0, 0, "x", "en", "0123456789abcdef")
            .endsWith("moviehash=0123456789abcdef"));
  }

  @Test
  public void rejectsInvalidHash() {
    assertFalse(
        SubtitleQuery.search("tt123", 0, 0, "x", "en", "unfinished-file").contains("moviehash"));
  }

  @Test
  public void acceptsVipSessionHost() {
    assertEquals(
        "https://vip-api.opensubtitles.com/api/v1/",
        SubtitleQuery.trustedBase("vip-api.opensubtitles.com"));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsForeignSessionHost() {
    SubtitleQuery.trustedBase("https://opensubtitles.com.attacker.example/");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsInsecureSessionHost() {
    SubtitleQuery.trustedBase("http://api.opensubtitles.com/");
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsEmbeddedCredentials() {
    SubtitleQuery.trustedBase("https://user@api.opensubtitles.com/");
  }

  @Test
  public void prefersExactMatchAndSkipsMissingFiles() throws Exception {
    JSONObject fuzzy =
        new JSONObject()
            .put(
                "attributes",
                new JSONObject()
                    .put("release", "name match")
                    .put("files", new JSONArray().put(new JSONObject().put("file_id", 1))));
    JSONObject exact =
        new JSONObject()
            .put(
                "attributes",
                new JSONObject()
                    .put("release", "exact")
                    .put("moviehash_match", true)
                    .put("files", new JSONArray().put(new JSONObject().put("file_id", 2))));
    JSONObject empty = new JSONObject().put("attributes", new JSONObject());
    java.util.List<SubtitleQuery.Result> results =
        SubtitleQuery.results(
            new JSONObject().put("data", new JSONArray().put(fuzzy).put(empty).put(exact)));
    assertEquals(2, results.size());
    assertEquals(2, results.get(0).fileId);
    assertTrue(results.get(0).exact);
  }
}
