package app.streamnest;

import android.util.JsonReader;
import android.util.JsonToken;
import java.io.*;
import java.nio.charset.StandardCharsets;
import org.json.*;

/** Streaming schema checks keep snapshot refreshes small on 1 GB devices. */
final class CatalogJson {
  static JsonReader reader(InputStream in) {
    return new JsonReader(new InputStreamReader(in, StandardCharsets.UTF_8));
  }

  static void inspect(String kind, JSONObject metadata, InputStream in) throws Exception {
    String field = kind.equals("movies") ? "movies" : kind.equals("latest") ? "releases" : "series";
    int count = -1;
    long saved = 0;
    try (JsonReader reader = reader(in)) {
      reader.beginObject();
      while (reader.hasNext()) {
        String key = reader.nextName();
        if (key.equals("saved")) saved = reader.nextLong();
        else if (key.equals(field)) {
          if (count != -1) throw new IOException("Duplicate catalog array");
          count = 0;
          reader.beginArray();
          while (reader.hasNext()) {
            if (reader.peek() != JsonToken.BEGIN_OBJECT)
              throw new IOException("Invalid catalog row");
            reader.skipValue();
            if (++count > 30000) throw new IOException("Catalog record limit exceeded");
          }
          reader.endArray();
        } else reader.skipValue();
      }
      reader.endObject();
      if (reader.peek() != JsonToken.END_DOCUMENT) throw new IOException("Trailing catalog data");
    }
    if (count != metadata.getInt("count")
        || saved != metadata.getLong("saved")
        || (!kind.equals("latest") && count <= 0))
      throw new IOException("Invalid catalog count or version");
  }

  static Object value(JsonReader r, int depth) throws Exception {
    if (depth > 32) throw new IOException("Catalog nesting limit");
    switch (r.peek()) {
      case BEGIN_OBJECT:
        JSONObject object = new JSONObject();
        r.beginObject();
        while (r.hasNext()) object.put(r.nextName(), value(r, depth + 1));
        r.endObject();
        return object;
      case BEGIN_ARRAY:
        JSONArray array = new JSONArray();
        r.beginArray();
        while (r.hasNext()) array.put(value(r, depth + 1));
        r.endArray();
        return array;
      case STRING:
        return r.nextString();
      case NUMBER:
        String number = r.nextString();
        try {
          return Long.parseLong(number);
        } catch (NumberFormatException e) {
          return Double.parseDouble(number);
        }
      case BOOLEAN:
        return r.nextBoolean();
      case NULL:
        r.nextNull();
        return JSONObject.NULL;
      default:
        throw new IOException("Invalid JSON value");
    }
  }
}
