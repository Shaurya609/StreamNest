package app.streamnest;

import java.util.*;

/** XML-RPC criteria are alternatives, never an AND of a file hash and a title lookup. */
final class OrgSearch {
  static List<Map<String, Object>> criteria(
      String imdb, int season, int episode, String title, String language, String hash, long size) {
    String lang = language.equals("hi") ? "hin" : "eng";
    List<Map<String, Object>> out = new ArrayList<>();
    if (hash != null && hash.matches("[0-9a-fA-F]{16}") && size >= 131072) {
      Map<String, Object> q = new LinkedHashMap<>();
      q.put("sublanguageid", lang);
      q.put("moviehash", hash.toLowerCase(Locale.ROOT));
      q.put("moviebytesize", String.valueOf(size));
      out.add(q);
    }
    Map<String, Object> q = new LinkedHashMap<>();
    q.put("sublanguageid", lang);
    if (imdb != null && imdb.matches("tt[0-9]+"))
      q.put("imdbid", imdb.substring(2).replaceFirst("^0+(?!$)", ""));
    else {
      String clean =
          title == null ? "" : title.replaceFirst("\\s*[·|]\\s*S[0-9]+\\s*E[0-9]+.*$", "").trim();
      if (clean.isEmpty()) return out;
      q.put("query", clean);
    }
    if (episode > 0) {
      q.put("season", String.valueOf(season));
      q.put("episode", String.valueOf(episode));
    }
    out.add(q);
    return out;
  }
}
