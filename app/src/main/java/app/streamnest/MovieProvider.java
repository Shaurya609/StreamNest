package app.streamnest;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/**
 * Compatible movie API endpoints; malformed or unavailable endpoints cannot become empty catalogs.
 */
final class MovieProvider {
  interface Fetcher {
    String get(String url) throws Exception;
  }

  static final List<String> BASES =
      Arrays.asList(
          "https://yts.gg/api/v2/",
          "https://movies-api.accel.li/api/v2/",
          "https://yts.bz/api/v2/");

  interface Store {
    void remember(String path, JSONObject data) throws Exception;

    JSONObject saved(String path) throws Exception;

    default JSONObject continuation(String path) throws Exception {
      return null;
    }

    default boolean suspiciousEmpty(String path, JSONObject data) throws Exception {
      return false;
    }
  }

  static volatile Store store;
  static volatile List<String> bases = BASES;
  static final EndpointHealth health = new EndpointHealth();
  static final ThreadLocal<Long> deadline = new ThreadLocal<>();
  static volatile String preferred = BASES.get(0);
  static Fetcher fetcher =
      url -> new String(Net.catalogBytes(url, 16000000, deadline.get()), StandardCharsets.UTF_8);

  static final ThreadLocal<String> feedToken = new ThreadLocal<>();
  static final ThreadLocal<Boolean> forceLive = ThreadLocal.withInitial(() -> false);
  static final java.util.concurrent.ThreadPoolExecutor refresh =
      new java.util.concurrent.ThreadPoolExecutor(
          1,
          1,
          30,
          java.util.concurrent.TimeUnit.SECONDS,
          new java.util.concurrent.ArrayBlockingQueue<>(3),
          r -> {
            Thread t = new Thread(r, "movie-refresh");
            t.setDaemon(true);
            return t;
          },
          new java.util.concurrent.ThreadPoolExecutor.DiscardPolicy());
  static final Map<String, JSONObject> recent = new java.util.concurrent.ConcurrentHashMap<>();
  static final Map<String, Long> recentAt = new java.util.concurrent.ConcurrentHashMap<>();
  static final Map<String, Long> attempts = new java.util.concurrent.ConcurrentHashMap<>();

  static JSONObject browse(String path) throws Exception {
    if (store != null && !Boolean.TRUE.equals(forceLive.get()))
      try {
        JSONObject saved = store.saved(path);
        if (saved != null
            && saved.optJSONArray("movies") != null
            && saved.optJSONArray("movies").length() > 0) {
          CatalogStatus.cached();
          queueRefresh(path);
          return saved.put("catalog_saved", true);
        }
      } catch (Exception ignored) {
      }
    return data(path);
  }

  private static void queueRefresh(String path) {
    long now = System.currentTimeMillis();
    Long last = attempts.get(path);
    if (last != null && now - last < 300000 || health.candidates(bases, preferred).isEmpty())
      return;
    if (attempts.size() > 96) attempts.clear();
    attempts.put(path, now);
    refresh.execute(
        () -> {
          try {
            JSONObject data = network(path, false);
            if (data.optJSONArray("movies") != null && data.optJSONArray("movies").length() > 0) {
              if (data.toString().length() < 1000000) {
                if (recent.size() > 2) {
                  recent.clear();
                  recentAt.clear();
                }
                recent.put(path, data);
                recentAt.put(path, System.currentTimeMillis());
              }
              CatalogUpdates.notify(path);
            }
          } catch (Exception ignored) {
          }
        });
  }

  static JSONObject data(String path) throws Exception {
    if (store != null)
      try {
        JSONObject saved = store.continuation(path);
        if (saved != null) {
          CatalogStatus.cached();
          return saved.put("catalog_saved", true);
        }
      } catch (Exception ignored) {
      }
    if (Boolean.TRUE.equals(forceLive.get())) {
      Long saved = recentAt.remove(path);
      JSONObject fresh = recent.remove(path);
      if (saved != null && fresh != null && System.currentTimeMillis() - saved < 15000)
        return new JSONObject(fresh.toString());
    }
    return network(path, true);
  }

  private static JSONObject network(String path, boolean useSaved) throws Exception {
    List<String> candidates = health.candidates(bases, preferred);
    deadline.set(System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(14));
    Exception failure = null;
    for (String base : candidates) {
      if (System.nanoTime() >= deadline.get()) break;
      try {
        JSONObject envelope = new JSONObject(fetcher.get(base + path));
        JSONObject data = envelope.optJSONObject("data");
        if (!"ok".equals(envelope.optString("status")) || data == null)
          throw new java.io.IOException("Invalid movie catalog response");
        if (data.has("movies") && !(data.opt("movies") instanceof JSONArray))
          throw new java.io.IOException("Invalid movie list");
        JSONArray rows = data.optJSONArray("movies");
        if (data.optInt("movie_count") > 0
            && (rows == null || rows.length() == 0)
            && !path.matches(".*[?&]page=[2-9][0-9]*.*"))
          throw new java.io.IOException("Movie catalog response is incomplete");
        boolean suspicious = false;
        if (store != null)
          try {
            suspicious = store.suspiciousEmpty(path, data);
          } catch (Exception ignored) {
          }
        if (suspicious) throw new java.io.IOException("Empty full-catalog refresh rejected");
        preferred = base;
        health.succeeded(base);
        if (store != null)
          try {
            store.remember(path, data);
          } catch (Exception ignored) {
          }
        return data;
      } catch (Exception error) {
        if (Thread.currentThread().isInterrupted())
          throw new InterruptedException("Catalog request replaced");
        failure = error;
        health.failed(base);
      }
    }
    if (useSaved && store != null)
      try {
        JSONObject saved = store.saved(path);
        if (saved != null) {
          CatalogStatus.cached();
          return saved.put("catalog_saved", true);
        }
      } catch (Exception ignored) {
      }
    throw new java.io.IOException(
        "Movie catalog servers are temporarily unavailable. Tap Retry.", failure);
  }

  static synchronized void configureBases(List<String> configured) {
    List<String> valid = new ArrayList<>();
    for (String base : configured)
      try {
        java.net.URI uri = java.net.URI.create(base);
        if ("https".equals(uri.getScheme())
            && uri.getHost() != null
            && uri.getUserInfo() == null
            && uri.getQuery() == null
            && uri.getFragment() == null
            && uri.getPort() == -1
            && uri.getPath().endsWith("/api/v2/")
            && !valid.contains(base)
            && valid.size() < 6) valid.add(base);
      } catch (IllegalArgumentException ignored) {
      }
    if (!valid.isEmpty() && !valid.equals(bases)) {
      bases = Collections.unmodifiableList(valid);
      if (!valid.contains(preferred)) preferred = valid.get(0);
      health.reset();
    }
  }
}
