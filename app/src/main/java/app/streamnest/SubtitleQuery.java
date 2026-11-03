package app.streamnest;

import java.util.*;
import org.json.*;

final class SubtitleQuery {
  static String search(
      String imdb, int season, int episode, String title, String language, String hash) {
    SortedMap<String, String> p = new TreeMap<>();
    p.put("languages", language);
    String numeric = imdb == null ? "" : imdb.replaceFirst("^tt", "");
    if (numeric.matches("[0-9]+")) {
      numeric = numeric.replaceFirst("^0+(?!$)", "");
      p.put(season > 0 ? "parent_imdb_id" : "imdb_id", numeric);
    } else if (title != null && !title.isBlank()) p.put("query", title.toLowerCase(Locale.ROOT));
    if (season > 0) {
      p.put("season_number", String.valueOf(season));
      p.put("episode_number", String.valueOf(episode));
    }
    if (hash != null && hash.matches("[0-9a-f]{16}")) p.put("moviehash", hash);
    StringJoiner q = new StringJoiner("&");
    p.forEach((k, v) -> q.add(k + "=" + Net.q(v)));
    return "subtitles?" + q;
  }

  static class Result {
    int fileId;
    String provider = "com";
    String label;
    boolean exact;

    Result(int id, String l, boolean e) {
      fileId = id;
      label = l;
      exact = e;
    }
  }

  static List<Result> results(JSONObject response) throws Exception {
    List<Result> out = new ArrayList<>();
    JSONArray data = response.optJSONArray("data");
    if (data == null) return out;
    for (int i = 0; i < data.length(); i++) {
      JSONObject a = data.getJSONObject(i).optJSONObject("attributes");
      if (a == null) continue;
      JSONArray files = a.optJSONArray("files");
      if (files == null) continue;
      for (int j = 0; j < files.length(); j++) {
        JSONObject f = files.getJSONObject(j);
        int id = f.optInt("file_id");
        if (id > 0)
          out.add(
              new Result(
                  id,
                  a.optString("language", "")
                      + " · "
                      + a.optString("release", f.optString("file_name", "Subtitle"))
                      + (a.optBoolean("moviehash_match") ? " · exact file match" : ""),
                  a.optBoolean("moviehash_match")));
      }
    }
    out.sort((a, b) -> Boolean.compare(b.exact, a.exact));
    return out;
  }

  static void rejectServiceNotice(byte[] data) throws java.io.IOException {
    String text =
        new String(data, java.nio.charset.StandardCharsets.UTF_8)
            .toLowerCase(java.util.Locale.ROOT);
    if (text.contains("become opensubtitles.org vip member") || text.contains("osdb.link/vip"))
      throw new java.io.IOException(
          "OpenSubtitles.org returned an account/VIP restriction notice instead of subtitles. No"
              + " captions were added.");
  }

  static String trustedBase(String value) {
    if (value == null || value.isBlank()) return "https://api.opensubtitles.com/api/v1/";
    String v = value.startsWith("https://") ? value : "https://" + value;
    java.net.URI uri = java.net.URI.create(v);
    String h = uri.getHost();
    if (!"https".equals(uri.getScheme())
        || h == null
        || (!h.equals("opensubtitles.com") && !h.endsWith(".opensubtitles.com"))
        || uri.getUserInfo() != null
        || uri.getPort() != -1) throw new IllegalArgumentException("Unexpected subtitle API host");
    return "https://" + h + "/api/v1/";
  }
}
