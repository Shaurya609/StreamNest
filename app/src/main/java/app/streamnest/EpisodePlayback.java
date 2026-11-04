package app.streamnest;

import java.util.*;
import org.json.*;

/** Canonical neighbours; never jump over missing, future or unnumbered episodes. */
final class EpisodePlayback {
  static JSONObject neighbour(List<JSONObject> rows, int season, int episode, int direction) {
    if (episode <= 0 || Math.abs(direction) != 1) return null;
    List<JSONObject> ordered = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JSONObject row : rows)
      if (row.optInt("episode") > 0
          && (season == 0 ? row.optInt("season") == 0 : row.optInt("season") > 0)
          && seen.add(EpisodeLibrary.key(row))) ordered.add(row);
    ordered.sort(
        Comparator.comparingInt((JSONObject e) -> e.optInt("season"))
            .thenComparingInt(e -> e.optInt("episode")));
    for (int i = 0; i < ordered.size(); i++)
      if (ordered.get(i).optInt("season") == season
          && ordered.get(i).optInt("episode") == episode) {
        int n = i + direction;
        return n >= 0 && n < ordered.size() ? ordered.get(n) : null;
      }
    return null;
  }

  static boolean future(JSONObject row) {
    String date = row.optString("airdate", row.optString("released", ""));
    return date.length() >= 10
        && date.substring(0, 10).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
        && date.substring(0, 10)
                .compareTo(java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString())
            > 0;
  }

  static boolean completed(long position, long length, boolean played, boolean paused) {
    return played && !paused && length > 10000 && position >= length - 5000;
  }
}
