package app.streamnest;

import java.util.*;
import org.json.*;

/** Read-only community timestamps. No fixed-duration guesses or automatic credit skipping. */
final class SkipSegments {
  static final class Segment {
    final String type;
    final long start, end;

    Segment(String t, long s, long e) {
      type = t;
      start = s;
      end = e;
    }
  }

  static Segment range(String type, double start, double end, long length) {
    long s = (long) start, e = (long) end;
    long max = type.equals("intro") ? 300000 : 900000;
    return Double.isFinite(start)
            && Double.isFinite(end)
            && s >= 0
            && e - s >= 5000
            && e - s <= max
            && length > 0
            && e <= length + 1000
            && (type.equals("intro") ? s < length / 2 : s > length / 2)
        ? new Segment(type, s, Math.min(e, length))
        : null;
  }

  static List<Segment> introdb(JSONObject doc, String imdb, int season, int episode, long length) {
    List<Segment> out = new ArrayList<>();
    if (!imdb.equals(doc.optString("imdb_id"))
        || season != doc.optInt("season", -1)
        || episode != doc.optInt("episode", -1)
        || doc.optBoolean("is_movie")) return out;
    for (String type : new String[] {"intro", "outro"}) {
      JSONObject row = doc.optJSONObject(type);
      if (row == null
          || row.optDouble("confidence", 0) < 0.75
          || row.optInt("submission_count") < 1) continue;
      Segment s =
          range(
              type,
              row.optDouble("start_ms", Double.NaN),
              row.optDouble("end_ms", Double.NaN),
              length);
      if (s != null) out.add(s);
    }
    return out;
  }

  static List<Segment> aniskip(JSONObject doc, long length) {
    List<Segment> out = new ArrayList<>();
    JSONArray rows = doc.optJSONArray("results");
    if (!doc.optBoolean("found") || rows == null) return out;
    for (int i = 0; i < rows.length(); i++) {
      JSONObject row = rows.optJSONObject(i);
      if (row == null || Math.abs(row.optDouble("episodeLength", 0) * 1000 - length) > 5000)
        continue;
      String t = row.optString("skipType");
      if (!t.equals("op") && !t.equals("ed")) continue;
      JSONObject interval = row.optJSONObject("interval");
      if (interval == null) continue;
      Segment s =
          range(
              t.equals("op") ? "intro" : "outro",
              interval.optDouble("startTime", Double.NaN) * 1000,
              interval.optDouble("endTime", Double.NaN) * 1000,
              length);
      if (s != null) out.add(s);
    }
    return out;
  }

  static List<Segment> load(String imdb, int season, int episode, int mal, long length) {
    List<Segment> out = new ArrayList<>();
    if (!imdb.matches("tt[0-9]+") || episode <= 0 || length <= 0) return out;
    try {
      out.addAll(
          introdb(
              CatalogHttp.object(
                  "https://api.introdb.app/segments?imdb_id="
                      + imdb
                      + "&season="
                      + season
                      + "&episode="
                      + episode,
                  null),
              imdb,
              season,
              episode,
              length));
    } catch (Exception ignored) {
    }
    // A root MAL ID is valid only for the explicitly mapped first season; never guess later-season
    // offsets.
    if (mal > 0 && season == 1 && out.size() < 2)
      try {
        for (Segment segment :
            aniskip(
                CatalogHttp.object(
                    "https://api.aniskip.com/v2/skip-times/"
                        + mal
                        + "/"
                        + episode
                        + "?types[]=op&types[]=ed&episodeLength="
                        + (length / 1000.0),
                    null),
                length))
          if (out.stream().noneMatch(s -> s.type.equals(segment.type))) out.add(segment);
      } catch (Exception ignored) {
      }
    return out;
  }

  static Segment active(List<Segment> rows, long position) {
    for (Segment s : rows) if (position >= s.start && position < s.end - 1000) return s;
    return null;
  }
}
