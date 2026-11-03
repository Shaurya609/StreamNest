package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class EpisodeCachePolicyTest {
  @Test
  public void nextEpisodeDeletesPreviousAcrossAllQualitiesAndFolders() {
    Map<String, String> rows = new HashMap<>();
    rows.put("/internal/ep4-hd", "show:1:4");
    rows.put("/external/ep4-4k", "show:1:4");
    rows.put("/internal/ep5", "show:1:5");
    assertEquals(
        new HashSet<>(Arrays.asList("/internal/ep4-hd", "/external/ep4-4k")),
        new HashSet<>(EpisodeCachePolicy.obsolete(rows, "show:1:5", "/internal/ep5")));
  }

  @Test
  public void sameEpisodeReplayOrQualityChangeKeepsItsCache() {
    assertTrue(
        EpisodeCachePolicy.obsolete(
                Collections.singletonMap("/ep4", "show:1:4"), "show:1:4", "/ep4-4k")
            .isEmpty());
  }

  @Test
  public void movieDoesNotTriggerEpisodeCleanup() {
    assertTrue(
        EpisodeCachePolicy.obsolete(Collections.singletonMap("/ep4", "show:1:4"), "", "/movie")
            .isEmpty());
  }

  @Test
  public void sharedSourcePathIsNeverDeletedUnderCurrentEpisode() {
    assertTrue(
        EpisodeCachePolicy.obsolete(
                Collections.singletonMap("/pack", "show:1:4"), "show:1:5", "/pack")
            .isEmpty());
  }
}
