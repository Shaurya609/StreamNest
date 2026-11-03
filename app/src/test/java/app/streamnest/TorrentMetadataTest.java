package app.streamnest;

import static org.junit.Assert.*;

import org.junit.Test;

public class TorrentMetadataTest {
  @Test
  public void acceptsOnlyExactV1Hash() {
    String h = "abcdef0123456789abcdef0123456789abcdef01";
    assertEquals(
        h, TorrentMetadata.hash("magnet:?xt=urn%3Abtih%3A" + h.toUpperCase() + "&dn=Name"));
    assertEquals("", TorrentMetadata.hash("magnet:?xt=urn:btih:../../file"));
    assertEquals("", TorrentMetadata.hash("https://example.com/"));
    assertEquals(
        "https://itorrents.net/torrent/" + h.toUpperCase() + ".torrent",
        TorrentMetadata.endpoint(h));
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsUntrustedPathInMetadataUrl() {
    TorrentMetadata.endpoint("../bad");
  }
}
