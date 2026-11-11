package app.streamnest;

import java.util.*;
import java.util.regex.*;
import org.json.*;

/** Stremio stream records bind an IMDb episode request to a torrent and exact member index. */
final class AlternateEpisodes {
  static final Map<String, List<Catalog.Source>> cache = new LinkedHashMap<>();
  static final Map<String, Long> ages = new HashMap<>();

  static List<Catalog.Source> parse(JSONObject document) throws Exception {
    List<Catalog.Source> out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    JSONArray rows = document.optJSONArray("streams");
    if (rows == null) throw new java.io.IOException("Invalid source index response");
    for (int n = 0; n < rows.length() && out.size() < 80; n++) {
      JSONObject row = rows.optJSONObject(n);
      if (row == null) continue;
      String hash = row.optString("infoHash").toLowerCase(Locale.ROOT);
      if (!hash.matches("[0-9a-f]{40}")) continue;
      String title = row.optString("title"), name = row.optString("name");
      String quality =
          PlaybackPlan.episodeQuality(
              new Catalog.Source((name + " " + title).replace('\n', ' '), "", 0));
      if (quality.isEmpty()) continue;
      Object raw=row.opt("fileIdx");
      if(!(raw instanceof Number)||((Number)raw).doubleValue()!=((Number)raw).intValue())continue;
      int index = row.optInt("fileIdx", -1);
      if (index < 0 || index>100000) continue; // Exact member is required for these alternative records.
      if (!seen.add(hash + ":" + index)) continue;
      int seeds = 0;
      Matcher m = Pattern.compile("👤\\s*(\\d+)").matcher(title);
      if (m.find())
        try {
          seeds = Integer.parseInt(m.group(1));
        } catch (NumberFormatException ignored) {
        }
      String magnet = "magnet:?xt=urn:btih:" + hash;
      JSONArray trackers = row.optJSONArray("sources");
      if (trackers != null)
        for (int t = 0; t < Math.min(trackers.length(), 12); t++) {
          String tracker = trackers.optString(t);
          if (tracker.matches("tracker:(?:https?|udp)://[^\\s]+"))
            magnet += "&tr=" + Net.q(tracker.substring(8));
        }
      String codec = (title + " " + name).toLowerCase(Locale.ROOT);
      Catalog.Source source =
          new Catalog.Source(
              quality
                  + " · Alternative"
                  + (codec.contains("av1")
                      ? " · AV1"
                      : codec.contains("265") || codec.contains("hevc") ? " · HEVC" : ""),
              magnet,
              seeds);
      source.fileIndex = index;
      JSONObject hints = row.optJSONObject("behaviorHints");
      if (hints != null) source.filename = new java.io.File(hints.optString("filename").replace((char)92,'/')).getName();
      out.add(source);
    }
    return out;
  }

  static List<Catalog.Source> find(String imdb,int season,int episode) throws Exception {return find(imdb,season,episode,System.nanoTime()+18000000000L);}
  static List<Catalog.Source> find(String imdb, int season, int episode,long deadline) throws Exception {
    if (!imdb.matches("tt[0-9]+") || season < 0 || episode <= 0) return Collections.emptyList();
    String key = imdb + ":" + season + ":" + episode;
    synchronized (cache) {
      if (cache.containsKey(key) && System.currentTimeMillis() - ages.get(key) < (cache.get(key).isEmpty()?30000:600000))
        return new ArrayList<>(cache.get(key));
    }
    List<Catalog.Source> rows =
        parse(
            new JSONObject(
                new String(
                    Net.catalogBytes("https://torrentio.strem.fun/stream/series/"+key+".json",2000000,Math.min(deadline,System.nanoTime()+6000000000L)),
                    java.nio.charset.StandardCharsets.UTF_8)));
    synchronized (cache) {
      cache.put(key, rows);
      ages.put(key, System.currentTimeMillis());
      while (cache.size() > 12) {
        String first = cache.keySet().iterator().next();
        cache.remove(first);
        ages.remove(first);
      }
    }
    return new ArrayList<>(rows);
  }

  static String encode(List<Catalog.Source> rows) {
    JSONArray out = new JSONArray();
    for (Catalog.Source s : rows)
      try {
        out.put(
            new JSONObject()
                .put("label", s.label)
                .put("url", s.url)
                .put("seeds", s.seeds)
                .put("fileIndex", s.fileIndex)
                .put("filename", s.filename));
      } catch (Exception ignored) {
      }
    return out.toString();
  }

  static List<Catalog.Source> decode(String json) {
    List<Catalog.Source> out = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(json);
      for (int n = 0; n < a.length(); n++) {
        JSONObject o = a.getJSONObject(n);
        Catalog.Source s =
            new Catalog.Source(o.getString("label"), o.getString("url"), o.optInt("seeds"));
        s.fileIndex = o.optInt("fileIndex", -1);
        s.filename = o.optString("filename");
        out.add(s);
      }
    } catch (Exception ignored) {
    }
    return out;
  }

  static List<Catalog.Source> attempts(List<Catalog.Source> sources, String quality, boolean lite) {
    List<Catalog.Source> remaining = new ArrayList<>(sources), out = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    while (!remaining.isEmpty() && out.size() < 6) {
      Catalog.Source next = PlaybackPlan.episodeBest(remaining, quality, lite);
      if (next == null) break;
      remaining.remove(next);
      if (seen.add(next.url + ":" + next.fileIndex)) out.add(next);
    }
    if(out.size()==6&&out.stream().noneMatch(s->(s.label.contains(" · Nyaa")||s.label.contains(" · EZTV")))){
      List<Catalog.Source> fallback=new ArrayList<>();for(Catalog.Source s:sources)if((s.label.contains(" · Nyaa")||s.label.contains(" · EZTV")))fallback.add(s);
      Catalog.Source extra=PlaybackPlan.episodeBest(fallback,quality,lite);if(extra!=null)out.set(out.size()-1,extra);
    }
    return out;
  }
}
