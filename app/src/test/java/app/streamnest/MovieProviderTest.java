package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.*;

public class MovieProviderTest {
  MovieProvider.Fetcher saved;
  String preferred;

  @Before
  public void setup() {
    saved = MovieProvider.fetcher;
    preferred = MovieProvider.preferred;
    MovieProvider.preferred = MovieProvider.BASES.get(0);
  }

  @After
  public void restore() {
    MovieProvider.fetcher = saved;
    MovieProvider.preferred = preferred;
  }

  @Test
  public void serverFailureFallsBackAndRemembersWorkingEndpoint() throws Exception {
    List<String> calls = new ArrayList<>();
    MovieProvider.fetcher =
        url -> {
          calls.add(url);
          if (url.startsWith(MovieProvider.BASES.get(0))) throw new java.io.IOException("HTTP 521");
          return "{\"status\":\"ok\",\"data\":{\"movies\":[]}}";
        };
    MovieProvider.data("list_movies.json?page=1");
    assertEquals(2, calls.size());
    calls.clear();
    MovieProvider.data("list_movies.json?page=2");
    assertEquals(1, calls.size());
    assertTrue(calls.get(0).endsWith("page=2"));
  }

  @Test
  public void invalidSuccessfulHttpResponseDoesNotBecomeEmptyCatalog() throws Exception {
    MovieProvider.fetcher =
        url ->
            url.startsWith(MovieProvider.BASES.get(0))
                ? "{\"error\":\"maintenance\"}"
                : "{\"status\":\"ok\",\"data\":{\"movie_count\":0}}";
    assertEquals(0, MovieProvider.data("list_movies.json").getInt("movie_count"));
  }

  @Test(expected = java.io.IOException.class)
  public void allFailuresRemainRetryableErrors() throws Exception {
    MovieProvider.fetcher =
        url -> {
          throw new java.io.IOException("offline");
        };
    MovieProvider.data("list_movies.json");
  }
}
