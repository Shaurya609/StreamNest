package app.streamnest;

import java.util.*;

final class PlaybackPlan {
  static String quality(Catalog.Source s) {
    if (ReleaseQuality.camera(s.label)) return "";
    if (s.label.contains("2160p")) return "2160p";
    if (s.label.contains("1080p")) return "1080p";
    return "";
  }

  static String episodeQuality(Catalog.Source s) {
    if (ReleaseQuality.camera(s.label)) return "";
    String label = s.label.toLowerCase(Locale.ROOT);
    for (String q : new String[] {"2160p", "1080p", "720p", "576p", "480p", "360p"})
      if (label.matches(".*\\b" + q + "\\b.*")) return q;
    if (label.matches(".*\\b(sd|dvd|dvdrip|sdtv)\\b.*")) return "SD";
    return "";
  }

  static List<String> episodeQualities(List<Catalog.Source> sources) {
    List<String> out = new ArrayList<>();
    for (String q : new String[] {"1080p", "2160p", "720p", "576p", "480p", "360p", "SD"})
      if (sources.stream().anyMatch(s -> episodeQuality(s).equals(q))) out.add(q);
    return out;
  }

  static Catalog.Source episodeBest(List<Catalog.Source> sources, String desired, boolean lite) {
    String chosen = desired;
    if (desired.equals("Auto")) {
      boolean seeded = sources.stream().anyMatch(s -> s.seeds > 0 && !episodeQuality(s).isEmpty());
      chosen = "";
      for (String q : new String[] {"1080p", "720p", "576p", "480p", "360p", "SD", "2160p"})
        if (sources.stream()
            .anyMatch(s -> episodeQuality(s).equals(q) && (!seeded || s.seeds > 0))) {
          chosen = q;
          break;
        }
    }
    final String exact = chosen;
    List<Catalog.Source> matches = new ArrayList<>();
    for (Catalog.Source source : sources)
      if (!exact.isEmpty() && episodeQuality(source).equals(exact)) matches.add(source);
    matches.sort(
        Comparator.<Catalog.Source>comparingInt(
                s -> lite && s.label.toLowerCase(Locale.ROOT).matches(".*(265|hevc|av1).*") ? 1 : 0)
            .thenComparing(Comparator.comparingInt((Catalog.Source s) -> s.seeds).reversed())
            .thenComparing(s -> s.url));
    return matches.isEmpty() ? null : matches.get(0);
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
    return sources.stream()
        .filter(s -> quality(s).equals(quality))
        .sorted(
            Comparator.<Catalog.Source>comparingInt(
                    s ->
                        lite
                                && (s.label.toLowerCase(Locale.ROOT).contains("265")
                                    || s.label.toLowerCase(Locale.ROOT).contains("hevc")
                                    || s.label.toLowerCase(Locale.ROOT).contains("av1"))
                            ? 1
                            : 0)
                .thenComparing(Comparator.comparingInt((Catalog.Source s) -> s.seeds).reversed())
                .thenComparing(s -> s.url))
        .findFirst()
        .orElse(null);
  }

  static List<Catalog.Source> sources(org.json.JSONObject item) throws Exception {
    List<Catalog.Source> out = new ArrayList<>();
    Catalog.collect(item.opt("torrents"), "", out);
    Catalog.collect(item.opt("items"), "", out);
    Catalog.collect(item.opt("items_lang"), "", out);
    return out;
  }
}
