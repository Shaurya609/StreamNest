package app.streamnest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Only unauthenticated catalog metadata uses this persistent transport. */
final class CatalogHttp {
  interface Fetcher {
    byte[] get(String url) throws Exception;
  }

  interface Validator {
    boolean valid(Object value);
  }

  static File directory;
  static Fetcher fetcher =
      url ->
          Net.catalogBytes(
              url, 16000000, System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(8));
  static final EndpointHealth health = new EndpointHealth();

  static void configure(File folder) {
    directory = new File(folder, "http");
    directory.mkdirs();
  }

  static Object get(String url, Validator validator) throws Exception {
    java.net.URI uri = java.net.URI.create(url);
    if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
      throw new IOException("HTTPS catalog address required");
    File file = null;
    Exception failure = null;
    if (directory != null) {
      StringBuilder name = new StringBuilder();
      for (byte b :
          MessageDigest.getInstance("SHA-256").digest(url.getBytes(StandardCharsets.UTF_8)))
        name.append(String.format(Locale.ROOT, "%02x", b & 255));
      file = new File(directory, name + ".json");
    }
    if (!health.candidates(Collections.singletonList(uri.getHost()), uri.getHost()).isEmpty())
      try {
        byte[] bytes = fetcher.get(url);
        if (bytes.length > 16000000) throw new IOException("Catalog response too large");
        Object value = new JSONTokener(new String(bytes, StandardCharsets.UTF_8)).nextValue();
        if (!validator.valid(value)) throw new IOException("Invalid catalog response");
        health.succeeded(uri.getHost());
        if (file != null)
          try {
            write(file, bytes);
          } catch (IOException ignored) {
          }
        return value;
      } catch (Exception e) {
        if (Thread.currentThread().isInterrupted())
          throw new InterruptedException("Catalog request replaced");
        failure = e;
        if (!String.valueOf(e.getMessage()).matches(".*HTTP\\s*(400|404|410|422)\\b.*"))
          health.failed(uri.getHost());
      }
    if (file != null && file.isFile() && file.length() <= 16000000)
      try {
        Object value =
            new JSONTokener(new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8))
                .nextValue();
        if (validator.valid(value)) {
          CatalogStatus.cached();
          return value;
        }
      } catch (Exception ignored) {
      }
    throw new IOException("Catalog provider is temporarily unavailable", failure);
  }

  static boolean validObject(String url, String arrayKey, Object value) {
    if (!(value instanceof JSONObject)) return false;
    JSONObject o = (JSONObject) value;
    if (o.length() == 0 || o.has("error")) return false;
    if (arrayKey != null) return o.optJSONArray(arrayKey) != null;
    java.net.URI uri = java.net.URI.create(url);
    if ("v3-cinemeta.strem.io".equals(uri.getHost()) && uri.getPath().startsWith("/meta/series/")) {
      JSONObject meta = o.optJSONObject("meta");
      String id = uri.getPath().replace("/meta/series/", "").replace(".json", "");
      return meta != null
          && id.equals(meta.optString("imdb_id", meta.optString("id")))
          && meta.optJSONArray("videos") != null;
    }
    if ("api.ani.zip".equals(uri.getHost()))
      return o.optJSONObject("mappings") != null && o.optJSONObject("episodes") != null;
    if ("api.tvmaze.com".equals(uri.getHost()) && "/lookup/shows".equals(uri.getPath())) {
      String query = uri.getQuery(),
          id = query == null ? "" : query.replaceFirst("^.*imdb=", "").split("&")[0];
      JSONObject ids = o.optJSONObject("externals");
      return o.optInt("id") > 0 && ids != null && id.equals(ids.optString("imdb"));
    }
    return true;
  }

  static JSONObject object(String url, String arrayKey) throws Exception {
    return (JSONObject) get(url, value -> validObject(url, arrayKey, value));
  }

  static JSONArray array(String url) throws Exception {
    return (JSONArray) get(url, value -> value instanceof JSONArray);
  }

  private static synchronized void write(File file, byte[] bytes) throws IOException {
    if (!directory.isDirectory() && !directory.mkdirs())
      throw new IOException("Catalog storage unavailable");
    File tmp = new File(directory, file.getName() + ".tmp");
    Files.write(tmp.toPath(), bytes);
    Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
    File[] files =
        directory.listFiles(f -> f.isFile() && f.getName().matches("[0-9a-f]{64}\\.json"));
    if (files == null) return;
    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
    long total = 0;
    for (File f : files) total += f.length();
    for (File f : files) {
      if (total <= 48L * 1024 * 1024) break;
      if (f.equals(file)) continue;
      long length = f.length();
      if (f.delete()) total -= length;
    }
  }
}
