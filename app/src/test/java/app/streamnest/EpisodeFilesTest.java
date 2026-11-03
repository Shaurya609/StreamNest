package app.streamnest;

import static org.junit.Assert.*;

import java.io.*;
import java.util.*;
import org.junit.Test;

public class EpisodeFilesTest {
  final List<String> pack =
      Arrays.asList("Show/Show.S01E04.mkv", "Show/Show.S01E05.mkv", "Show/sample.mkv");
  final List<Long> sizes = Arrays.asList(900L, 500L, 10L);

  @Test
  public void selectsRequestedEpisodeInsteadOfLargest() throws Exception {
    assertEquals(1, EpisodeFiles.select(pack, sizes, -1, "", true, 1, 5, 0));
  }

  @Test
  public void honorsIndexedMemberAndVerifiesFilename() throws Exception {
    assertEquals(1, EpisodeFiles.select(pack, sizes, 1, "Show.S01E05.mkv", true, 1, 5, 0));
  }

  @Test(expected = IOException.class)
  public void rejectsWrongFilename() throws Exception {
    EpisodeFiles.select(pack, sizes, 1, "Show.S01E04.mkv", true, 1, 4, 0);
  }

  @Test(expected = IOException.class)
  public void rejectsAmbiguousUnnumberedPack() throws Exception {
    EpisodeFiles.select(
        Arrays.asList("a.mkv", "b.mkv"), Arrays.asList(500L, 900L), -1, "", true, 1, 5, 0);
  }

  @Test(expected = IOException.class)
  public void rejectsInvalidIndexWithoutLargestFallback() throws Exception {
    EpisodeFiles.select(pack, sizes, 8, "", true, 1, 5, 0);
  }

  @Test
  public void supportsExplicitAnimeAbsoluteNumber() throws Exception {
    assertEquals(
        1,
        EpisodeFiles.select(
            Arrays.asList("[Group] Anime - 1178 [720p].mkv", "[Group] Anime - 1179 [720p].mkv"),
            Arrays.asList(900L, 500L),
            -1,
            "",
            true,
            23,
            24,
            1179));
  }

  @Test
  public void movieStillSelectsLargestVideo() throws Exception {
    assertEquals(0, EpisodeFiles.select(pack, sizes, -1, "", false, 0, 0, 0));
  }
}
