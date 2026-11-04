package app.streamnest;

import android.content.Context;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.json.*;

/** Private on-disk session avoids large Android Intent payloads for long anime. */
final class EpisodeQueue {
  interface Finder {
    List<Catalog.Source> find(String imdb, int season, int episode) throws Exception;
  }

  static Finder finder = AlternateEpisodes::find;

  static String create(Context c, Catalog.Title title, List<JSONObject> episodes, String quality)
      throws Exception {
    File folder = new File(c.getFilesDir(), "episode-sessions");
    folder.mkdirs();
    File[] old = folder.listFiles();
    if (old != null)
      for (File f : old)
        if (f.getName().matches("[a-f0-9-]{36}\\.json")
            && System.currentTimeMillis() - f.lastModified() > 7L * 86400000) f.delete();
    JSONArray rows = new JSONArray();
    for (JSONObject raw : episodes) {
      JSONObject row = new JSONObject();
      for (String k :
          new String[] {
            "season",
            "episode",
            "absolute",
            "skipMalId",
            "title",
            "airdate",
            "liveRelease",
            "torrents",
            "items",
            "items_lang"
          }) if (raw.has(k)) row.put(k, raw.get(k));
      rows.put(row);
    }
    JSONObject doc =
        new JSONObject()
            .put("imdb", title.id)
            .put("title", title.title)
            .put("quality", quality)
            .put("malId", title.raw.optInt("malId"))
            .put("episodes", rows);
    byte[] bytes = doc.toString().getBytes(StandardCharsets.UTF_8);
    if (bytes.length > 12000000) throw new IOException("Episode list is too large");
    String token = UUID.randomUUID().toString();
    Files.write(new File(folder, token + ".json").toPath(), bytes);
    return token;
  }

  static JSONObject read(Context c, String token) throws Exception {
    if (token == null || !token.matches("[a-f0-9-]{36}")) return null;
    File file = new File(new File(c.getFilesDir(), "episode-sessions"), token + ".json");
    if (file.length() > 12000000) throw new IOException("Episode list is too large");
    return new JSONObject(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
  }

  static List<JSONObject> rows(JSONObject doc) throws Exception {
    List<JSONObject> rows = new ArrayList<>();
    JSONArray a = doc.getJSONArray("episodes");
    for (int i = 0; i < a.length(); i++) rows.add(a.getJSONObject(i));
    return rows;
  }

  static List<Catalog.Source> resolve(JSONObject doc, JSONObject row, boolean lite)
      throws Exception {
    if (EpisodePlayback.future(row)) throw new IOException("This episode has not aired yet");
    List<Catalog.Source> sources = PlaybackPlan.sources(row);
    try {
      sources.addAll(
          finder.find(doc.getString("imdb"), row.optInt("season"), row.optInt("episode")));
    } catch (Exception ignored) {
    }
    List<Catalog.Source> attempts =
        AlternateEpisodes.attempts(sources, doc.optString("quality", "Auto"), lite);
    if (attempts.isEmpty())
      throw new IOException(
          "No source found for this episode. Try another quality from episode details.");
    return attempts;
  }
}
