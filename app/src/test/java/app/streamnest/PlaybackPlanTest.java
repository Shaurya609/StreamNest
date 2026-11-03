package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class PlaybackPlanTest {
  @Test
  public void autoUsesReportedSeededAlternativeBeforeZeroSeedHd() {
    Catalog.Source stale = new Catalog.Source("1080p", "stale", 0),
        live = new Catalog.Source("720p", "live", 5);
    assertEquals(live, PlaybackPlan.episodeBest(Arrays.asList(stale, live), "Auto", false));
    assertEquals(stale, PlaybackPlan.episodeBest(Arrays.asList(stale, live), "1080p", false));
  }

  @Test
  public void episodesFallBackWithoutRelaxingMovieMinimum() {
    Catalog.Source sd = new Catalog.Source("480p DVDRip", "sd", 50),
        hd = new Catalog.Source("720p HDTV", "hd", 20);
    List<Catalog.Source> rows = Arrays.asList(sd, hd);
    assertEquals(hd, PlaybackPlan.episodeBest(rows, "Auto", false));
    assertEquals(sd, PlaybackPlan.episodeBest(rows, "480p", false));
    assertTrue(PlaybackPlan.qualities(rows).isEmpty());
    assertNull(PlaybackPlan.best(rows, "1080p", false));
    assertNull(PlaybackPlan.episodeBest(rows, "1080p", false));
  }

  @Test
  public void autoPrefers1080AndRejectsCameraSourcesAtEveryResolution() {
    Catalog.Source hd = new Catalog.Source("1080p WEB", "1080", 1),
        low = new Catalog.Source("720p WEB", "720", 100),
        cam = new Catalog.Source("480p HDCAM", "cam", 1000);
    assertEquals(hd, PlaybackPlan.episodeBest(Arrays.asList(hd, low, cam), "Auto", false));
    assertNull(PlaybackPlan.episodeBest(Arrays.asList(cam), "Auto", false));
  }

  @Test
  public void legacyDvdAnd576pArePlayableWithHonestLabels() {
    Catalog.Source dvd = new Catalog.Source("DVDRip", "dvd", 2),
        pal = new Catalog.Source("576p", "pal", 1);
    assertEquals("SD", PlaybackPlan.episodeQuality(dvd));
    assertEquals(pal, PlaybackPlan.episodeBest(Arrays.asList(dvd, pal), "Auto", true));
  }

  @Test
  public void qualityChangesSelectTheMatchingSourceDirectly() {
    Catalog.Source hd = new Catalog.Source("1080p WEB H264", "https://example/hd", 50),
        uhd = new Catalog.Source("2160p WEB HEVC", "https://example/4k", 100);
    List<Catalog.Source> a = Arrays.asList(hd, uhd);
    assertEquals("https://example/hd", PlaybackPlan.best(a, "1080p", false).url);
    assertEquals("https://example/4k", PlaybackPlan.best(a, "2160p", false).url);
    assertEquals("1080p", PlaybackPlan.preferred(a, "All"));
  }

  @Test
  public void unavailableQualityDoesNotPlayAnotherResolution() {
    List<Catalog.Source> a = Arrays.asList(new Catalog.Source("1080p", "https://example/hd", 2));
    assertNull(PlaybackPlan.best(a, "2160p", false));
    assertEquals(Arrays.asList("1080p"), PlaybackPlan.qualities(a));
  }

  @Test
  public void excludesCameraAndChoosesBestCompatibleSource() {
    Catalog.Source camera = new Catalog.Source("1080p HDCAM", "bad", 1000),
        h264 = new Catalog.Source("1080p H264", "h264", 30),
        h265 = new Catalog.Source("1080p HEVC", "h265", 100);
    List<Catalog.Source> a = Arrays.asList(camera, h264, h265);
    assertEquals(h264, PlaybackPlan.best(a, "1080p", true));
    assertEquals(h265, PlaybackPlan.best(a, "1080p", false));
  }
}
