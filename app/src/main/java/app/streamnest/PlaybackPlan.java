package app.streamnest;

import java.util.*;

final class PlaybackPlan {
  static String quality(Catalog.Source s) {
    if (ReleaseQuality.camera(s.label)) return "";
    if (s.label.contains("2160p")) return "2160p";
    if (s.label.contains("1080p")) return "1080p";
    return "";
  }

  private static final String[] EPISODE_ORDER = {"1080p", "720p", "576p", "480p", "360p", "SD", "2160p"};
  private static final java.util.regex.Pattern RESOLUTION = java.util.regex.Pattern.compile("\\b(2160p|1080p|720p|576p|480p|360p)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);
  private static final java.util.regex.Pattern SD = java.util.regex.Pattern.compile("\\b(sd|dvd|dvdrip|sdtv)\\b", java.util.regex.Pattern.CASE_INSENSITIVE);

  static String episodeQuality(Catalog.Source s) {
    if (ReleaseQuality.camera(s.label)) return "";
    // Preserve resolution precedence when a release label contains several resolutions.
    java.util.regex.Matcher m = RESOLUTION.matcher(s.label);
    String result = "";
    while (m.find()) {
      String q = m.group(1).toLowerCase(Locale.ROOT);
      if (result.isEmpty() || resolution(q) > resolution(result)) result = q;
    }
    return !result.isEmpty() ? result : SD.matcher(s.label).find() ? "SD" : "";
  }

  private static int resolution(String q) { return Integer.parseInt(q.substring(0, q.length()-1)); }

  static List<String> episodeQualities(List<Catalog.Source> sources) {
    Set<String> available = new HashSet<>();
    for (Catalog.Source s : sources) available.add(episodeQuality(s));
    List<String> out = new ArrayList<>();
    for (String q : new String[] {"1080p", "2160p", "720p", "576p", "480p", "360p", "SD"})
      if (available.contains(q)) out.add(q);
    return out;
  }

  static Catalog.Source episodeBest(List<Catalog.Source> sources, String desired, boolean lite) {
    // Keep one winner per quality. Never run release parsing inside a sort comparator:
    // on low-memory devices hundreds of labels previously froze the UI thread.
    Map<String, Ranked> all = new HashMap<>(), seeded = new HashMap<>();
    for (Catalog.Source s : sources) {
      String q = episodeQuality(s);
      if (q.isEmpty() || (!desired.equals("Auto") && !desired.equals(q))) continue;
      Ranked rank = new Ranked(s, lite);
      keepBest(all, q, rank);
      if (s.seeds > 0) keepBest(seeded, q, rank);
    }
    if (!desired.equals("Auto")) return source(all.get(desired));
    Map<String, Ranked> candidates = seeded.isEmpty() ? all : seeded;
    // Auto chooses a quality containing a seeded source, then ranks every source at
    // that quality, matching the existing language/codec preference behavior.
    for (String q : EPISODE_ORDER) if (candidates.containsKey(q)) return source(all.get(q));
    return null;
  }

  private static Catalog.Source source(Ranked rank) { return rank == null ? null : rank.source; }
  private static void keepBest(Map<String, Ranked> winners, String q, Ranked rank) {
    Ranked previous = winners.get(q);
    if (previous == null || rank.compareTo(previous) < 0) winners.put(q, rank);
  }
  private static final class Ranked implements Comparable<Ranked> {
    final Catalog.Source source;
    final int audio, codec;
    Ranked(Catalog.Source source, boolean lite) {
      this.source = source;
      audio = SourcePolicy.audioRank(source);
      String label = lite ? source.label.toLowerCase(Locale.ROOT) : "";
      codec = label.contains("265") || label.contains("hevc") || label.contains("av1") ? 1 : 0;
    }
    public int compareTo(Ranked other) {
      int result = Integer.compare(audio, other.audio);
      if (result == 0) result = Integer.compare(codec, other.codec);
      if (result == 0) result = Integer.compare(other.source.seeds, source.seeds);
      return result == 0 ? source.url.compareTo(other.source.url) : result;
    }
  }

  static List<String> qualities(List<Catalog.Source> sources) {
    List<String> out = new ArrayList<>();
    for (String q : new String[] {"1080p", "2160p"})
      if (sources.stream().anyMatch(s -> quality(s).equals(q))) out.add(q);
    return out;
  }

  static String preferred(List<Catalog.Source> sources, String desired) {
    List<String> values = qualities(sources);
    return values.contains(desired)
        ? desired
        : values.contains("1080p") ? "1080p" : values.isEmpty() ? "" : values.get(0);
  }

  static Catalog.Source best(List<Catalog.Source> sources, String quality, boolean lite) {
    Ranked winner = null;
    for (Catalog.Source source : sources) {
      if (!quality(source).equals(quality)) continue;
      Ranked rank = new Ranked(source, lite);
      if (winner == null || rank.compareTo(winner) < 0) winner = rank;
    }
    return source(winner);
  }

  static List<Catalog.Source> sources(org.json.JSONObject item) throws Exception {
    List<Catalog.Source> out = new ArrayList<>();
    Catalog.collect(item.opt("torrents"), "", out);
    Catalog.collect(item.opt("items"), "", out);
    Catalog.collect(item.opt("items_lang"), "", out);
    return out;
  }
}
