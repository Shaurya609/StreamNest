package app.streamnest;

import java.net.*;
import java.util.*;
import javax.xml.parsers.*;
import org.json.*;
import org.w3c.dom.*;

/** A rolling feed of individual trusted releases. No guessed IMDb or season mapping. */
final class AnimeReleases {
  static final Map<String, List<JSONObject>> cache = new HashMap<>();
  static final Map<String, Long> ages = new HashMap<>();

  static java.io.File snapshotFile;
  static android.content.res.AssetManager assets;

  static void configure(java.io.File directory, android.content.res.AssetManager a) {
    snapshotFile = new java.io.File(directory, "anime-latest-v1.json");
    assets = a;
  }

  static List<JSONObject> savedRows(JSONObject doc, String query, String quality) throws Exception {
    List<JSONObject> out = new ArrayList<>();
    JSONArray rows = doc.getJSONArray("releases");
    for (int i = 0; i < rows.length(); i++) {
      JSONObject m = new JSONObject(rows.getJSONObject(i).toString());
      String title = m.optString("title");
      if (!m.optString("imdb").matches("nyaa:[0-9]+")
          || !individual(title)
          || !title.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))) continue;
      JSONArray items = m.optJSONArray("torrents");
      if (items == null) {
        items = new JSONArray();
        JSONArray source = m.optJSONArray("items");
        if (source != null)
          for (int n = 0; n < source.length(); n++) {
            JSONObject item = source.getJSONObject(n);
            items.put(
                new JSONObject()
                    .put("quality", item.optString("quality"))
                    .put("file", title)
                    .put("url", item.optString("torrent_url"))
                    .put("seeds", item.optInt("torrent_seeds")));
          }
      }
      JSONArray chosen = new JSONArray();
      String selected = "";
      for (int n = 0; n < items.length(); n++) {
        JSONObject item = items.getJSONObject(n);
        String url = item.optString("url"), q = item.optString("quality");
        if (!m.optString("imdb").equals("nyaa:" + url.replaceAll("[^0-9]", ""))
            || !url.matches("https://nyaa\\.si/download/[0-9]+\\.torrent")
            || PlaybackPlan.episodeQuality(new Catalog.Source(q + " " + title, url, 0)).isEmpty()
            || !quality.equals("All") && !quality.equals(q)) continue;
        chosen.put(item);
        selected = q;
      }
      if (chosen.length() == 0) continue;
      m.remove("items");
      m.put("torrents", chosen).put("releaseQuality", selected).put("animeRelease", true);
      if (m.optLong("uploaded") > 100000000000L) m.put("uploaded", m.getLong("uploaded") / 1000);
      out.add(m);
    }
    return out;
  }

  static List<JSONObject> saved(String query, String quality) {
    try {
      if (snapshotFile != null && snapshotFile.isFile())
        try (java.io.InputStream in = new java.io.FileInputStream(snapshotFile)) {
          return savedRows(
              new JSONObject(
                  new String(Net.read(in, 4000000), java.nio.charset.StandardCharsets.UTF_8)),
              query,
              quality);
        }
      if (assets != null)
        try (java.io.InputStream in = assets.open("catalog-latest-v1.json")) {
          return savedRows(
              new JSONObject(
                  new String(Net.read(in, 4000000), java.nio.charset.StandardCharsets.UTF_8)),
              query,
              quality);
        }
    } catch (Exception ignored) {
    }
    return new ArrayList<>();
  }

  static void saveRows(Collection<JSONObject> incoming) {
    if (snapshotFile == null || incoming.isEmpty()) return;
    try {
      Map<String, JSONObject> all = new LinkedHashMap<>();
      for (JSONObject row : saved("", "All")) all.put(row.optString("imdb"), row);
      for (JSONObject row : incoming) all.put(row.optString("imdb"), row);
      List<JSONObject> ordered = new ArrayList<>(all.values());
      ordered.sort(Comparator.<JSONObject>comparingLong(m -> m.optLong("uploaded")).reversed());
      JSONObject doc =
          new JSONObject()
              .put("saved", System.currentTimeMillis())
              .put("releases", new JSONArray(ordered.subList(0, Math.min(300, ordered.size()))));
      java.io.File tmp =
          new java.io.File(snapshotFile.getParentFile(), snapshotFile.getName() + ".tmp");
      java.nio.file.Files.write(
          tmp.toPath(), doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      java.nio.file.Files.move(
          tmp.toPath(), snapshotFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    } catch (Exception ignored) {
    }
  }

  static String child(Element item, String tag) {
    NodeList a = item.getElementsByTagName(tag);
    return a.getLength() == 0 ? "" : a.item(0).getTextContent();
  }

  static boolean individual(String title) {
    return title.matches(
            "(?i).*(?:\\bS[0-9]{1,2}E[0-9]{1,4}\\b|\\s-\\s[0-9]{1,4}(?:v[0-9]+)?(?=\\s|\\[|\\(|$)).*")
        && !title.matches("(?i).*(?:\\bbatch\\b|\\bcomplete\\b|[0-9]{1,3}[-~][0-9]{1,3}).*");
  }

  static List<JSONObject> parse(byte[] bytes) throws Exception {
    String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    String upper = xml.toUpperCase(Locale.ROOT);
    if (xml.indexOf(0) >= 0 || upper.contains("<!DOCTYPE") || upper.contains("<!ENTITY"))
      throw new java.io.IOException("External XML declarations are not supported");
    DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
    f.setExpandEntityReferences(false);
    javax.xml.parsers.DocumentBuilder builder = f.newDocumentBuilder();
    builder.setEntityResolver(
        (publicId, systemId) -> new org.xml.sax.InputSource(new java.io.StringReader("")));
    Document doc = builder.parse(new java.io.ByteArrayInputStream(bytes));
    if (!doc.getDocumentElement().getTagName().equalsIgnoreCase("rss"))
      throw new java.io.IOException("Invalid anime RSS feed");
    NodeList items = doc.getElementsByTagName("item");
    List<JSONObject> out = new ArrayList<>();
    for (int i = 0; i < items.getLength(); i++) {
      Element item = (Element) items.item(i);
      if (!child(item, "nyaa:trusted").equalsIgnoreCase("Yes")) continue;
      String title = child(item, "title"),
          link = child(item, "link"),
          quality = PlaybackPlan.episodeQuality(new Catalog.Source(title, link, 0));
      if (quality.isEmpty() || !individual(title)) continue;
      URI uri = URI.create(link);
      if (!uri.getScheme().equals("https")
          || !uri.getHost().equals("nyaa.si")
          || !uri.getPath().matches("/download/[0-9]+\\.torrent")) continue;
      int seeds;
      try {
        seeds = Integer.parseInt(child(item, "nyaa:seeders"));
      } catch (Exception e) {
        seeds = 0;
      }
      if (seeds <= 0) continue;
      long uploaded = 0;
      try {
        uploaded =
            java.time.ZonedDateTime.parse(
                    child(item, "pubDate"), java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME)
                .toEpochSecond();
      } catch (Exception ignored) {
      }
      String id = "nyaa:" + uri.getPath().replaceAll("[^0-9]", "");
      JSONObject source =
          new JSONObject()
              .put("quality", quality)
              .put("file", title)
              .put("url", link)
              .put("seeds", seeds);
      JSONObject m =
          new JSONObject()
              .put("imdb", id)
              .put("title", title)
              .put(
                  "year",
                  uploaded > 0
                      ? java.time.Instant.ofEpochSecond(uploaded)
                          .atZone(java.time.ZoneOffset.UTC)
                          .getYear()
                      : "")
              .put(
                  "description",
                  "Single episode release. The filename identifies the release group and episode."
                      + " Embedded subtitles and audio tracks depend on the release. Reported"
                      + " seeds: "
                      + seeds)
              .put("animeRelease", true)
              .put("releaseQuality", quality)
              .put("releaseSeeds", seeds)
              .put("uploaded", uploaded)
              .put("torrents", new JSONArray().put(source));
      out.add(m);
    }
    return out;
  }

  static final EndpointHealth health = new EndpointHealth();
  static final Map<String, List<JSONObject>> sessions = new LinkedHashMap<>();
  static final Set<String> refreshing = new HashSet<>(), cachedKeys = new HashSet<>();
  static final java.util.concurrent.ThreadPoolExecutor worker =
      new java.util.concurrent.ThreadPoolExecutor(
          1,
          1,
          30,
          java.util.concurrent.TimeUnit.SECONDS,
          new java.util.concurrent.ArrayBlockingQueue<>(2),
          r -> {
            Thread t = new Thread(r, "anime-feed-refresh");
            t.setDaemon(true);
            return t;
          },
          new java.util.concurrent.ThreadPoolExecutor.AbortPolicy());

  interface Fetcher {
    byte[] get(String url, long deadline) throws Exception;
  }

  static Fetcher fetcher = (url, deadline) -> Net.catalogBytes(url, 4000000, deadline);

  static List<JSONObject> fetchRows(String query, String quality) throws Exception {
    String host = "nyaa.si";
    if (health.candidates(Collections.singletonList(host), host).isEmpty())
      throw new java.io.IOException("Anime feed retry is cooling down");
    LinkedHashMap<String, JSONObject> all = new LinkedHashMap<>();
    Exception failure = null;
    long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(14);
    for (String q :
        quality.equals("All")
            ? new String[] {"1080p", "2160p", "720p", "480p"}
            : new String[] {quality})
      try {
        for (JSONObject row :
            parse(
                fetcher.get(
                    "https://nyaa.si/?page=rss&c=1_2&f=2&q="
                        + Net.q((query.isBlank() ? "" : query + " ") + q),
                    deadline))) all.putIfAbsent(row.optString("imdb"), row);
        health.succeeded(host);
      } catch (Exception e) {
        failure = e;
        health.failed(host);
        break;
      }
    saveRows(all.values());
    if (failure != null || all.isEmpty() && query.isBlank())
      for (JSONObject row : saved(query, quality)) {
        all.putIfAbsent(row.optString("imdb"), row);
        CatalogStatus.cached();
      }
    if (all.isEmpty() && failure != null) throw failure;
    return new ArrayList<>(all.values());
  }

  static void put(String key, List<JSONObject> rows, boolean saved) {
    synchronized (AnimeReleases.class) {
      cache.put(key, rows);
      ages.put(key, System.currentTimeMillis());
      if (saved) cachedKeys.add(key);
      else cachedKeys.remove(key);
      while (cache.size() > 3) {
        String first = cache.keySet().iterator().next();
        cache.remove(first);
        ages.remove(first);
        cachedKeys.remove(first);
      }
    }
  }

  static void queue(String key, String query, String quality) {
    synchronized (AnimeReleases.class) {
      if (refreshing.contains(key)
          || System.currentTimeMillis() - ages.getOrDefault(key, 0L) < 300000) return;
      refreshing.add(key);
      ages.put(key, System.currentTimeMillis());
    }
    try {
      worker.execute(
          () -> {
            try {
              CatalogStatus.begin();
              List<JSONObject> rows = fetchRows(query, quality);
              put(key, rows, !CatalogStatus.note().isEmpty());
              if (CatalogStatus.note().isEmpty()) CatalogUpdates.notify("anime-latest:" + key);
            } catch (Exception ignored) {
            } finally {
              synchronized (AnimeReleases.class) {
                refreshing.remove(key);
              }
            }
          });
    } catch (java.util.concurrent.RejectedExecutionException e) {
      synchronized (AnimeReleases.class) {
        refreshing.remove(key);
        ages.remove(key);
      }
    }
  }

  static List<Catalog.Title> list(int page, String query, String sort, String quality, int order)
      throws Exception {
    String key = query + "|" + quality, session = key + "|" + sort + "|" + order;
    List<JSONObject> rows;
    synchronized (AnimeReleases.class) {
      rows = cache.get(key);
    }
    if (rows == null) {
      List<JSONObject> disk = saved(query, quality);
      if (!disk.isEmpty()) {
        rows = disk;
        put(key, rows, true);
        synchronized (AnimeReleases.class) {
          ages.put(key, 0L);
        }
      } else
        try {
          rows = fetchRows(query, quality);
          put(key, rows, !CatalogStatus.note().isEmpty());
        } catch (Exception unavailable) {
          rows = new ArrayList<>();
          put(key, rows, true);
          CatalogStatus.cached();
        }
    }
    queue(key, query, quality);
    synchronized (AnimeReleases.class) {
      if (cachedKeys.contains(key)) CatalogStatus.cached();
      List<JSONObject> pinned = sessions.get(session);
      if (pinned == null || page == 1) {
        pinned = new ArrayList<>(rows);
        sessions.put(session, pinned);
        while (sessions.size() > 4) sessions.remove(sessions.keySet().iterator().next());
      }
      rows = pinned;
    }
    List<JSONObject> ordered = new ArrayList<>(rows);
    Comparator<JSONObject> comparator;
    if (sort.equals("title"))
      comparator = Comparator.comparing(m -> m.optString("title").toLowerCase(Locale.ROOT));
    else if (sort.equals("popularity") || sort.equals("trending"))
      comparator = Comparator.<JSONObject>comparingInt(m -> m.optInt("releaseSeeds")).reversed();
    else comparator = Comparator.<JSONObject>comparingLong(m -> m.optLong("uploaded")).reversed();
    if (order != (sort.equals("title") ? 1 : -1)) comparator = comparator.reversed();
    ordered.sort(comparator.thenComparing(m -> m.optString("imdb")));
    List<Catalog.Title> out = new ArrayList<>();
    int start = Math.min(ordered.size(), Math.max(0, page - 1) * 24);
    for (int i = start; i < Math.min(ordered.size(), start + 24); i++)
      out.add(Catalog.parse(ordered.get(i), "movies"));
    return out;
  }
}
