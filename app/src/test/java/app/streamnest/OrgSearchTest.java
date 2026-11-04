package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class OrgSearchTest {
  @Test
  public void unmatchedHashCannotHideImdbEpisodeResults() {
    List<Map<String, Object>> q =
        OrgSearch.criteria(
            "tt12324366", 1, 1, "Percy Jackson", "en", "0123456789abcdef", 500000000);
    assertEquals(2, q.size());
    assertEquals("500000000", q.get(0).get("moviebytesize"));
    assertFalse(q.get(0).containsKey("imdbid"));
    assertFalse(q.get(1).containsKey("moviehash"));
    assertEquals("12324366", q.get(1).get("imdbid"));
    assertEquals("1", q.get(1).get("episode"));
  }

  @Test
  public void hashWithoutSizeIsOmittedAndLeadingZerosAreNormalized() {
    List<Map<String, Object>> q =
        OrgSearch.criteria("tt0816692", 0, 0, "Interstellar", "en", "0123456789abcdef", 0);
    assertEquals(1, q.size());
    assertEquals("816692", q.get(0).get("imdbid"));
    assertFalse(q.get(0).containsKey("moviehash"));
  }

  @Test
  public void titleFallbackRemovesPlayerSuffixAndSupportsSpecials() {
    Map<String, Object> q =
        OrgSearch.criteria("kitsu:1", 0, 2, "Anime · S0 E2", "hi", "", 0).get(0);
    assertEquals("Anime", q.get("query"));
    assertEquals("0", q.get("season"));
    assertEquals("hin", q.get("sublanguageid"));
  }
}
