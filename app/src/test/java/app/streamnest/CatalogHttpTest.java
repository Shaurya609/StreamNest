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

  @Test
  public void wrongEpisodeIdentityCannotReplaceSavedMetadata() throws Exception {
    String url = "https://v3-cinemeta.strem.io/meta/series/tt123.json";
    CatalogHttp.fetcher =
        u -> "{\"meta\":{\"id\":\"tt123\",\"videos\":[]}}".getBytes(StandardCharsets.UTF_8);
    assertEquals("tt123", CatalogHttp.object(url, null).getJSONObject("meta").getString("id"));
    CatalogHttp.fetcher =
        u -> "{\"meta\":{\"id\":\"tt999\",\"videos\":[]}}".getBytes(StandardCharsets.UTF_8);
    assertEquals("tt123", CatalogHttp.object(url, null).getJSONObject("meta").getString("id"));
    assertTrue(CatalogStatus.note().contains("Saved"));
  }

  @Test public void concurrentMetadataSharesTransportButNotMutableResults() throws Exception {
    java.util.concurrent.CountDownLatch started=new java.util.concurrent.CountDownLatch(1),release=new java.util.concurrent.CountDownLatch(1);
    java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
    CatalogHttp.fetcher=u->{calls.incrementAndGet();started.countDown();release.await();return "{\"items\":[]}".getBytes(StandardCharsets.UTF_8);};
    java.util.concurrent.ExecutorService pool=java.util.concurrent.Executors.newFixedThreadPool(2);
    try{
      java.util.concurrent.Future<JSONObject> a=pool.submit(()->CatalogHttp.object("https://fixture.example/shared",null));
      assertTrue(started.await(2,java.util.concurrent.TimeUnit.SECONDS));
      java.util.concurrent.Future<JSONObject> b=pool.submit(()->CatalogHttp.object("https://fixture.example/shared",null));
      Thread.sleep(50);release.countDown();JSONObject first=a.get(),second=b.get();first.put("changed",true);assertFalse(second.has("changed"));assertEquals(1,calls.get());
    }finally{release.countDown();pool.shutdownNow();}
  }
  @Test public void missingLookupIsCachedPerUrlWithoutHidingOtherMetadata() throws Exception {
    java.util.concurrent.atomic.AtomicInteger calls=new java.util.concurrent.atomic.AtomicInteger();
    CatalogHttp.fetcher=u->{calls.incrementAndGet();if(u.endsWith("missing"))throw new IOException("Catalog HTTP 404");return "[]".getBytes(StandardCharsets.UTF_8);};
    for(int i=0;i<2;i++)try{CatalogHttp.array("https://fixture.example/missing");fail();}catch(IOException expected){}
    assertEquals(1,calls.get());assertEquals(0,CatalogHttp.array("https://fixture.example/found").length());assertEquals(2,calls.get());
  }
}
