package app.streamnest;

import static org.junit.Assert.*;

import org.json.*;
import org.junit.Test;

public class SeriesDiscoveryTest {
  @Test
  public void onlyExplicitImdbIdentityIsImportedAndHindiIsPreserved() throws Exception {
    JSONObject show =
        new JSONObject()
            .put("name", "Hindi show")
            .put("language", "Hindi")
            .put("externals", new JSONObject().put("imdb", "tt123"))
            .put("genres", new JSONArray().put("Drama"));
    JSONArray data =
        new JSONArray()
            .put(new JSONObject().put("show", show))
            .put(new JSONObject().put("show", new JSONObject().put("name", "No identity")));
    java.util.List<JSONObject> rows = SeriesDiscovery.parse(data);
    assertEquals(1, rows.size());
    assertEquals("tt123", rows.get(0).getString("imdb"));
    assertEquals("hi", rows.get(0).getString("language"));
    assertTrue(rows.get(0).getBoolean("metadataOnly"));
    assertFalse(rows.get(0).has("torrents"));
  }
}
