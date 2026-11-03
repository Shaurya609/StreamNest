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

  static synchronized List<Catalog.Title> list(
      int page, String query, String sort, String quality, int order) throws Exception {
    String key = query + "|" + quality;
    List<JSONObject> rows = cache.get(key);
    if (rows == null
        || page == 1 && System.currentTimeMillis() - ages.getOrDefault(key, 0L) > 300000) {
      LinkedHashMap<String, JSONObject> all = new LinkedHashMap<>();
      Exception failure = null;
      String[] qualities =
          quality.equals("All")
              ? new String[] {"1080p", "2160p", "720p", "480p"}
              : new String[] {quality};
      for (String q : qualities)
        try {
          for (JSONObject m :
              parse(
                  Net.bytes(
                      "https://nyaa.si/?page=rss&c=1_2&f=2&q="
                          + Net.q((query.isBlank() ? "" : query + " ") + q),
                      4000000))) all.putIfAbsent(m.optString("imdb"), m);
        } catch (Exception e) {
          failure = e;
        }
      if (all.isEmpty() && failure != null && rows == null) throw failure;
      if (!all.isEmpty() || rows == null) {
        rows = new ArrayList<>(all.values());
        cache.put(key, rows);
        ages.put(key, System.currentTimeMillis());
        while (cache.size() > 3) {
          String first = cache.keySet().iterator().next();
          cache.remove(first);
          ages.remove(first);
        }
      }
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
