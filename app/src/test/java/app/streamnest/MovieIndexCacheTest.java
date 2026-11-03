package app.streamnest;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import org.json.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class MovieIndexCacheTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Before
  public void reset() {
    MovieIndex.snapshot = null;
    MovieIndex.disk = null;
    MovieIndex.refreshed = 0;
  }

  @After
  public void cleanup() {
    reset();
  }

  @Test
  public void recentDiskCatalogLoadsWithoutNetwork() throws Exception {
    File folder = temp.newFolder();
    JSONObject doc =
        new JSONObject()
            .put("saved", System.currentTimeMillis())
            .put("movies", new JSONArray().put(new JSONObject().put("title", "Cached title")));
    Files.write(
        new File(folder, "movies-index-v5.json").toPath(),
        doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    MovieIndex.configure(folder);
    assertEquals("Cached title", MovieIndex.pool().get(0).getString("title"));
  }

  @Test
  public void malformedCacheIsIgnored() throws Exception {
    File folder = temp.newFolder();
    Files.write(new File(folder, "movies-index-v5.json").toPath(), new byte[] {1, 2, 3});
    MovieIndex.configure(folder);
    assertNull(MovieIndex.snapshot);
  }

  @Test
  public void oldCacheIsNotPresentedAsCurrent() throws Exception {
    File folder = temp.newFolder();
    JSONObject doc =
        new JSONObject()
            .put("saved", System.currentTimeMillis() - 48 * 60 * 60 * 1000L)
            .put("movies", new JSONArray());
    Files.write(
        new File(folder, "movies-index-v5.json").toPath(),
        doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
    MovieIndex.configure(folder);
    assertNull(MovieIndex.snapshot);
  }
}
