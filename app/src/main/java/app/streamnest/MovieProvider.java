package app.streamnest;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/**
 * Compatible movie API endpoints; malformed or unavailable endpoints cannot become empty catalogs.
 */
final class MovieProvider {
  interface Fetcher {
    String get(String url) throws Exception;
  }

  static final List<String> BASES =
      Arrays.asList(
          "https://yts.gg/api/v2/",
          "https://movies-api.accel.li/api/v2/",
          "https://yts.bz/api/v2/");
  static volatile String preferred = BASES.get(0);
  static Fetcher fetcher =
      url -> new String(Net.bytes(url, 16000000, 4000, 10000), StandardCharsets.UTF_8);

  static JSONObject data(String path) throws Exception {
    List<String> bases = new ArrayList<>(BASES);
    bases.remove(preferred);
    bases.add(0, preferred);
    Exception failure = null;
    for (String base : bases) {
      try {
        JSONObject envelope = new JSONObject(fetcher.get(base + path));
        JSONObject data = envelope.optJSONObject("data");
        if (!"ok".equals(envelope.optString("status")) || data == null)
          throw new java.io.IOException("Invalid movie catalog response");
        if (data.has("movies") && !(data.opt("movies") instanceof JSONArray))
          throw new java.io.IOException("Invalid movie list");
        preferred = base;
        return data;
      } catch (Exception error) {
        failure = error;
      }
    }
    throw new java.io.IOException(
        "Movie catalog servers are temporarily unavailable. Tap Retry.", failure);
  }
}
