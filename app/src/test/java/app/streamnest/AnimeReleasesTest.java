package app.streamnest;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import org.junit.Test;

public class AnimeReleasesTest {
  @Test
  public void acceptsTrusted720pIndividualEpisode() throws Exception {
    var rows =
        AnimeReleases.parse(
            feed("[Group] One Piece - 1150 [720p]", "Yes", "https://nyaa.si/download/123.torrent"));
    assertEquals(1, rows.size());
    assertEquals("720p", rows.get(0).getString("releaseQuality"));
  }

  byte[] feed(String title, String trusted, String url) {
    return ("<rss xmlns:nyaa='https://nyaa.si/xmlns/nyaa'><channel><item><title>"
            + title
            + "</title><link>"
            + url
            + "</link><nyaa:seeders>50</nyaa:seeders><nyaa:trusted>"
            + trusted
            + "</nyaa:trusted><pubDate>Tue, 03 Nov 2026 12:00:00"
            + " +0000</pubDate></item></channel></rss>")
        .getBytes(StandardCharsets.UTF_8);
  }

  @Test
  public void acceptsIndividualTrustedHdReleaseWithoutInventedImdbMapping() throws Exception {
    var a =
        AnimeReleases.parse(
            feed(
                "[Group] One Piece - 1150 [1080p]", "Yes", "https://nyaa.si/download/123.torrent"));
    assertEquals(1, a.size());
    assertEquals("nyaa:123", a.get(0).getString("imdb"));
    assertEquals(50, a.get(0).getInt("releaseSeeds"));
  }

  @Test
  public void excludesPacksAndCameraFiles() throws Exception {
    assertTrue(
        AnimeReleases.parse(
                feed(
                    "[Group] One Piece (01-12) [Batch] [1080p]",
                    "Yes",
                    "https://nyaa.si/download/123.torrent"))
            .isEmpty());
    assertTrue(
        AnimeReleases.parse(
                feed(
                    "[Group] One Piece - 1150 [1080p HDCAM]",
                    "Yes",
                    "https://nyaa.si/download/123.torrent"))
            .isEmpty());
  }

  @Test
  public void rejectsUntrustedAndUnrelatedDownloadHosts() throws Exception {
    assertTrue(
        AnimeReleases.parse(
                feed(
                    "[Group] One Piece - 1150 [1080p]",
                    "No",
                    "https://nyaa.si/download/123.torrent"))
            .isEmpty());
    assertTrue(
        AnimeReleases.parse(
                feed(
                    "[Group] One Piece - 1150 [1080p]",
                    "Yes",
                    "https://example.com/download/123.torrent"))
            .isEmpty());
  }

  @Test(expected = Exception.class)
  public void rejectsExternalEntityDocuments() throws Exception {
    AnimeReleases.parse(
        "<!DOCTYPE rss [<!ENTITY x SYSTEM 'file:///etc/passwd'>]><rss>&x;</rss>"
            .getBytes(StandardCharsets.UTF_8));
  }
}
