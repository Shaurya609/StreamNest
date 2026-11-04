package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class EpisodePlaybackTest {
  JSONObject ep(int s, int e) throws Exception {
    return new JSONObject().put("season", s).put("episode", e);
  }

  @Test
  public void canonicalOrderCrossesSeasonsWithoutSpecialsOrDuplicates() throws Exception {
    List<JSONObject> rows =
        Arrays.asList(ep(0, 1), ep(2, 1), ep(1, 2), ep(1, 1), ep(1, 2), ep(0, 0));
    assertEquals("2:1", EpisodeLibrary.key(EpisodePlayback.neighbour(rows, 1, 2, 1)));
    assertEquals("1:2", EpisodeLibrary.key(EpisodePlayback.neighbour(rows, 2, 1, -1)));
    assertNull(EpisodePlayback.neighbour(rows, 1, 1, -1));
    assertNull(EpisodePlayback.neighbour(rows, 2, 1, 1));
    assertNull(EpisodePlayback.neighbour(rows, 0, 1, 1));
    assertNull(EpisodePlayback.neighbour(rows, 99, 1, 1));
  }

  @Test
  public void completionRequiresActualPlaybackNearEnd() {
    assertTrue(EpisodePlayback.completed(1199000, 1200000, true, false));
    assertFalse(EpisodePlayback.completed(300000, 1200000, true, false));
    assertFalse(EpisodePlayback.completed(1199000, 1200000, true, true));
    assertFalse(EpisodePlayback.completed(1199000, 1200000, false, false));
    assertFalse(EpisodePlayback.completed(0, 0, true, false));
  }

  @Test
  public void futureEpisodesAreNotStarted() throws Exception {
    assertTrue(EpisodePlayback.future(ep(1, 1).put("airdate", "2999-01-01")));
    assertFalse(EpisodePlayback.future(ep(1, 1).put("airdate", "2020-01-01")));
  }

  @Test
  public void missingSourceDoesNotJumpOverEpisode() throws Exception {
    EpisodeQueue.Finder old = EpisodeQueue.finder;
    try {
      EpisodeQueue.finder = (id, s, e) -> Collections.emptyList();
      try {
        EpisodeQueue.resolve(new JSONObject().put("imdb", "tt1"), ep(1, 2), false);
        fail();
      } catch (java.io.IOException expected) {
        assertTrue(expected.getMessage().contains("No source"));
      }
    } finally {
      EpisodeQueue.finder = old;
    }
  }
}
