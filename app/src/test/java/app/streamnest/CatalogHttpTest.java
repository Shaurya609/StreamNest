package app.streamnest;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class CatalogHttpTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();
  CatalogHttp.Fetcher old;
  File directory;

  @Before
  public void setup() throws Exception {
    old = CatalogHttp.fetcher;
    directory = CatalogHttp.directory;
    CatalogHttp.configure(temporary.newFolder());
    CatalogHttp.health.reset();
    CatalogStatus.begin();
  }

  @After
  public void restore() {
    CatalogHttp.fetcher = old;
    CatalogHttp.directory = directory;
    CatalogHttp.health.reset();
  }

  @Test
  public void malformed200CannotOverwriteLastGoodCatalog() throws Exception {
    String url = "https://fixture.example/shows";
    CatalogHttp.fetcher =
        u -> "{\"MovieList\":[{\"imdb\":\"tt1\"}]}".getBytes(StandardCharsets.UTF_8);
    assertEquals(1, CatalogHttp.object(url, "MovieList").getJSONArray("MovieList").length());
    CatalogHttp.fetcher = u -> "{\"error\":\"maintenance\"}".getBytes(StandardCharsets.UTF_8);
    assertEquals(1, CatalogHttp.object(url, "MovieList").getJSONArray("MovieList").length());
    assertTrue(CatalogStatus.note().contains("Saved"));
  }

  @Test
  public void offlineRequestsUseDiskAnd404DoesNotDisableOtherLookups() throws Exception {
    String one = "https://fixture.example/search?q=one";
    CatalogHttp.fetcher = u -> "[]".getBytes(StandardCharsets.UTF_8);
    CatalogHttp.array(one);
    CatalogHttp.fetcher =
        u -> {
          throw new IOException("Server returned HTTP 404");
        };
    assertEquals(0, CatalogHttp.array(one).length());
    assertFalse(
        CatalogHttp.health
            .candidates(java.util.Collections.singletonList("fixture.example"), "fixture.example")
            .isEmpty());
    CatalogHttp.fetcher =
        u -> {
          throw new IOException("offline");
        };
    assertEquals(0, CatalogHttp.array(one).length());
  }

  @Test
  public void emptySearchIsValidAndNotConfusedWithInvalidPayload() throws Exception {
    CatalogHttp.fetcher = u -> "[]".getBytes(StandardCharsets.UTF_8);
    assertEquals(0, CatalogHttp.array("https://fixture.example/search?q=missing").length());
    assertEquals("", CatalogStatus.note());
  }
}
