package app.streamnest;

import java.util.*;
import org.json.*;

final class CanonicalEpisodes {
  static JSONArray fromCinemeta(JSONObject meta, String imdb) throws Exception {
    if (!imdb.equals(meta.optString("imdb_id", meta.optString("id"))))
      throw new Exception("Episode metadata identity mismatch");
    JSONArray videos = meta.optJSONArray("videos");
    if (videos == null) return new JSONArray();
    List<JSONObject> rows = new ArrayList<>();
    for (int i = 0; i < videos.length(); i++) {
      JSONObject v = videos.getJSONObject(i);
      String released = v.optString("released", v.optString("firstAired", ""));
      rows.add(
          new JSONObject()
              .put("id", v.optString("id", v.optString("tvdb_id")))
              .put("season", v.optInt("season"))
              .put("number", v.optInt("episode", v.optInt("number")))
              .put("name", v.optString("name", v.optString("title", "Episode")))
              .put("summary", v.optString("overview", v.optString("description", "")))
              .put("airdate", released.length() >= 10 ? released.substring(0, 10) : released));
    }
    rows.sort(
        Comparator.comparingInt((JSONObject e) -> e.optInt("season"))
            .thenComparingInt(e -> e.optInt("number")));
    return new JSONArray(rows);
  }

  static boolean trustedReverseMapping(JSONObject doc,String imdb,String title){
    JSONObject mappings=doc.optJSONObject("mappings"),titles=doc.optJSONObject("titles");
    if(mappings==null||titles==null||!imdb.equals(mappings.optString("imdb_id"))||!Arrays.asList("TV","ONA").contains(mappings.optString("type")))return false;
    String expected=normalized(title.replaceAll("(?i)\\s*\\((?:TV Series|TV Mini Series).*", ""));
    if(expected.isEmpty())return false;
    for(Iterator<String> keys=titles.keys();keys.hasNext();)if(expected.equals(normalized(titles.optString(keys.next()))))return true;return false;
  }

  static JSONArray fromMappings(JSONObject doc, String imdb) throws Exception {
    JSONObject mappings = doc.getJSONObject("mappings");
    if (!imdb.equals(mappings.optString("imdb_id")))
      throw new Exception("Anime episode identity mismatch");
    JSONObject episodes = doc.getJSONObject("episodes");
    List<JSONObject> rows = new ArrayList<>();
    Iterator<String> keys = episodes.keys();
    while (keys.hasNext()) {
      String mappingKey = keys.next();
      JSONObject e = episodes.optJSONObject(mappingKey);
      if (e == null || !e.has("seasonNumber") || !e.has("episodeNumber")) continue;
      JSONObject titles = e.optJSONObject("title");
      String name = titles == null ? "" : titles.optString("en", titles.optString("x-jat", ""));
      JSONObject row =
          new JSONObject()
              .put("id", e.optString("tvdbId"))
              .put("season", e.optInt("seasonNumber"))
              .put("number", e.optInt("episodeNumber"))
              .put("name", name.isEmpty() ? "Episode " + e.optInt("episodeNumber") : name)
              .put("summary", e.optString("overview", ""))
              .put("airdate", e.optString("airDate", e.optString("airdate", "")));
      row.put("animeMapped",mappings.optInt("mal_id")>0);
      JSONObject aliases=doc.optJSONObject("titles");if(aliases!=null)row.put("animeTitle",aliases.optString("en",aliases.optString("x-jat","")));
      if(mappingKey.matches("[1-9][0-9]*")&&mappings.optInt("kitsu_id")>0)
        row.put("kitsuId",mappings.getInt("kitsu_id")).put("kitsuEpisode",Integer.parseInt(mappingKey));
      if (e.optInt("absoluteEpisodeNumber") > 0)
        row.put("absolute", e.optInt("absoluteEpisodeNumber"));
      if (mappings.optInt("mal_id") > 0
          && e.optInt("seasonNumber") == 1
          && mappingKey.equals(String.valueOf(e.optInt("episodeNumber"))))
        row.put("skipMalId", mappings.optInt("mal_id"));
      rows.add(row);
    }
    rows.sort(
        Comparator.comparingInt((JSONObject e) -> e.optInt("season"))
            .thenComparingInt(e -> e.optInt("number")));
    return new JSONArray(rows);
  }

  static boolean sameEpisode(JSONObject a,JSONObject b){
    String ad=a.optString("airdate"),bd=b.optString("airdate"),an=normalized(a.optString("name")),bn=normalized(b.optString("name"));
    return ad.length()>=10&&bd.length()>=10&&ad.substring(0,10).equals(bd.substring(0,10))||!an.isEmpty()&&!an.matches("episode[0-9]*")&&an.equals(bn);
  }
  static JSONArray augmentMappings(JSONArray mapped,JSONArray canonical,boolean preferCanonical)throws Exception{
    Map<String,JSONObject> byKey=new HashMap<>(),base=new HashMap<>();int count=0,full=0;
    for(int i=0;i<canonical.length();i++){JSONObject row=canonical.getJSONObject(i);base.put(row.optInt("season")+":"+row.optInt("number"),row);if(row.optInt("season")>0)full++;}
    boolean aligned=true;for(int i=0;i<mapped.length();i++){JSONObject row=mapped.getJSONObject(i);String key=row.optInt("season")+":"+row.optInt("number");byKey.put(key,row);if(row.optInt("season")>0){count++;JSONObject other=base.get(key);if(other==null||!sameEpisode(row,other))aligned=false;}}
    if(canonical.length()==0||!preferCanonical&&(!aligned||full<=count))return mapped;
    JSONArray out=new JSONArray();for(int i=0;i<canonical.length();i++){JSONObject row=new JSONObject(canonical.getJSONObject(i).toString()),m=byKey.get(row.optInt("season")+":"+row.optInt("number"));if(m!=null&&sameEpisode(m,row))for(String field:new String[]{"absolute","skipMalId","kitsuId","kitsuEpisode","animeMapped","animeTitle"})if(m.has(field))row.put(field,m.get(field));out.put(row);}return out;
  }

  static String normalized(String name) {
    return name.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]", "");
  }

  static int releaseNumber(String release, String title) {
    String clean = release.replaceAll("^(?:\\[[^\\]]+\\]\\s*)+", "");
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile("^(.+?)\\s-\\s([0-9]{1,4})(?:v[0-9]+)?(?:\\s|\\[|\\(|$)")
            .matcher(clean);
    if (!m.find() || !normalized(m.group(1)).equals(normalized(title))) return -1;
    return Integer.parseInt(m.group(2));
  }

  static void attachLive(List<JSONObject> episodes, List<Catalog.Title> releases, String name)
      throws Exception {
    Map<Integer, JSONObject> byAbsolute = new HashMap<>();
    for (JSONObject e : episodes)
      if (e.optInt("absolute") > 0) byAbsolute.put(e.optInt("absolute"), e);
    for (Catalog.Title release : releases) {
      int number = releaseNumber(release.title, name);
      JSONObject ep = byAbsolute.get(number);
      if (ep == null) continue;
      JSONArray sources = ep.optJSONArray("torrents");
      if (sources == null) sources = new JSONArray();
      JSONArray items = release.raw.optJSONArray("torrents");
      if (items == null) continue;
      Set<String> urls = new HashSet<>();
      for (int i = 0; i < sources.length(); i++)
        urls.add(sources.getJSONObject(i).optString("url"));
      for (int i = 0; i < items.length(); i++)
        if (urls.add(items.getJSONObject(i).optString("url"))) sources.put(items.getJSONObject(i));
      ep.put("torrents", sources).put("liveRelease", true);
    }
  }
}
