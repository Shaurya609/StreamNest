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

  @org.junit.Test
  public void savedMirrorReleasesKeepExactIdentityAndNormalizeTimestamp() throws Exception {
    org.json.JSONObject row =
        new org.json.JSONObject()
            .put("imdb", "nyaa:123")
            .put("title", "[Group] One Piece - 1179 [720p]")
            .put("uploaded", 1790000000000L)
            .put(
                "items",
                new org.json.JSONArray()
                    .put(
                        new org.json.JSONObject()
                            .put("quality", "720p")
                            .put("torrent_url", "https://nyaa.si/download/123.torrent")
                            .put("torrent_seeds", 20)));
    org.json.JSONObject doc =
        new org.json.JSONObject().put("releases", new org.json.JSONArray().put(row));
    java.util.List<org.json.JSONObject> rows = AnimeReleases.savedRows(doc, "One Piece", "720p");
    org.junit.Assert.assertEquals(1, rows.size());
    org.junit.Assert.assertEquals(1790000000L, rows.get(0).getLong("uploaded"));
    org.junit.Assert.assertTrue(AnimeReleases.savedRows(doc, "Another series", "All").isEmpty());
    org.junit.Assert.assertTrue(AnimeReleases.savedRows(doc, "One Piece", "1080p").isEmpty());
    row.put("imdb", "nyaa:999");
    org.junit.Assert.assertTrue(AnimeReleases.savedRows(doc, "", "All").isEmpty());
  }
}
