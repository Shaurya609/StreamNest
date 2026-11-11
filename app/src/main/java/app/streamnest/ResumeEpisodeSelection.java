package app.streamnest;

import java.time.*;
import java.util.*;
import org.json.*;

final class ResumeEpisodeSelection {
  static boolean aired(JSONObject e, long now) {
    if (e.optLong("first_aired") > now / 1000) return false;
    String date = e.optString("airdate", e.optString("released", ""));
    return date.length() < 10 || !date.substring(0, 10).matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")
        || date.substring(0, 10).compareTo(Instant.ofEpochMilli(now).atOffset(ZoneOffset.UTC).toLocalDate().toString()) <= 0;
  }

  static JSONObject choose(String imdb, List<JSONObject> episodes, JSONObject history, long now) {
    if (imdb == null || !imdb.matches("tt[0-9]+")) return null;
    List<JSONObject> ordered = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    for (JSONObject e : episodes) {
      int season = e.optInt("season", -1), number = e.optInt("episode", -1);
      if (season >= 0 && number > 0 && seen.add(season + ":" + number)) ordered.add(e);
    }
    ordered.sort(Comparator.comparingInt((JSONObject e) -> e.optInt("season"))
        .thenComparingInt(e -> e.optInt("episode")));
    JSONObject latest = null, record = null;
    for (JSONObject e : ordered) {
      JSONObject row = history.optJSONObject(WatchProgress.identity(imdb, e.optInt("season"), e.optInt("episode"), ""));
      if (row != null && aired(e, now) && row.optLong("updatedAt") > 0
          && (record == null || row.optLong("updatedAt") > record.optLong("updatedAt"))) {
        latest = e;
        record = row;
      }
    }
    if (latest == null || !record.optBoolean("completed")) return latest;
    for(int pass=0;pass<2;pass++)for(int n=pass==0?ordered.indexOf(latest)+1:0;n<ordered.size();n++){JSONObject e=ordered.get(n),done=history.optJSONObject(WatchProgress.identity(imdb,e.optInt("season"),e.optInt("episode"),""));if((e.optInt("season")==0)==(latest.optInt("season")==0)&&aired(e,now)&&(done==null||!done.optBoolean("completed")))return e;}
    return latest;
  }
}
