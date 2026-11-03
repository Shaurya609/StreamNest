package app.streamnest;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

final class OpenSubtitles {
  private final SecretStore secrets;
  private final Context context;

  boolean orgMode() {
    return context.getSharedPreferences("subtitles", 0).getString("provider", "org").equals("org");
  }

  OpenSubtitles(Context context) {
    this.context = context;
    secrets = new SecretStore(context);
  }

  boolean configured() {
    if (orgMode()) return new OrgSubtitles(context).signedIn();
    try {
      return !secrets.get("apiKey").isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  boolean signedIn() {
    if (orgMode()) return new OrgSubtitles(context).signedIn();
    try {
      return !secrets.get("token").isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  void saveKey(String key) throws Exception {
    if (!secrets.get("apiKey").equals(key.trim())) logout();
    secrets.put("apiKey", key.trim());
  }

  void logout() throws Exception {
    if (orgMode()) {
      new OrgSubtitles(context).logout();
      return;
    }
    secrets.put("token", "");
    secrets.put("base", "");
  }

  void login(String username, String password) throws Exception {
    if (orgMode()) {
      new OrgSubtitles(context).login(username, password);
      return;
    }
    JSONObject data =
        request(
            "POST",
            "login",
            new JSONObject().put("username", username).put("password", password),
            false);
    String token = data.optString("token", "");
    if (token.isEmpty()) throw new IOException("OpenSubtitles did not return a session token");
    String base = SubtitleQuery.trustedBase(data.optString("base_url", ""));
    secrets.put("base", base);
    secrets.put("token", token);
  }

  JSONObject request(String method, String path, JSONObject body, boolean auth) throws Exception {
    String key = secrets.get("apiKey");
    if (key.isEmpty()) throw new IOException("Add your OpenSubtitles API key in Settings");
    String base = auth ? secrets.get("base") : "";
    String endpoint = SubtitleQuery.trustedBase(base) + path;
    for (int redirect = 0; redirect < 4; redirect++) {
      HttpURLConnection c = (HttpURLConnection) new URL(endpoint).openConnection();
      c.setConnectTimeout(12000);
      c.setReadTimeout(20000);
      c.setInstanceFollowRedirects(false);
      c.setRequestMethod(method);
      c.setRequestProperty("Api-Key", key);
      c.setRequestProperty("User-Agent", "StreamNest v0.2.0");
      c.setRequestProperty("Accept", "application/json");
      String token = auth ? secrets.get("token") : "";
      if (!token.isEmpty()) c.setRequestProperty("Authorization", "Bearer " + token);
      try {
        if (body != null) {
          c.setDoOutput(true);
          c.setRequestProperty("Content-Type", "application/json");
          try (OutputStream out = c.getOutputStream()) {
            out.write(body.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
          }
        }
        int status = c.getResponseCode();
        if (status >= 300 && status < 400 && method.equals("GET")) {
          URI next = URI.create(endpoint).resolve(c.getHeaderField("Location"));
          SubtitleQuery.trustedBase(next.toString());
          endpoint = next.toString();
          continue;
        }
        InputStream input = status >= 400 ? c.getErrorStream() : c.getInputStream();
        String response = "{}";
        if (input != null) {
          try (InputStream in = input;
              ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192];
            int n;
            while ((n = in.read(b)) != -1) {
              out.write(b, 0, n);
              if (out.size() > 4000000) throw new IOException("Subtitle response too large");
            }
            response = out.toString("UTF-8");
          }
        }
        JSONObject json;
        try {
          json = new JSONObject(response);
        } catch (JSONException e) {
          throw new IOException("OpenSubtitles returned HTTP " + status);
        }
        if (status >= 400) {
          if (status == 401 && auth) {
            logout();
            throw new IOException("OpenSubtitles session expired. Sign in again.");
          }
          if (status == 429)
            throw new IOException("OpenSubtitles request limit reached. Try again shortly.");
          throw new IOException("OpenSubtitles: " + json.optString("message", "HTTP " + status));
        }
        return json;
      } finally {
        c.disconnect();
      }
    }
    throw new IOException("Too many subtitle redirects");
  }

  List<SubtitleQuery.Result> search(
      String imdb, int season, int episode, String title, String language, String hash)
      throws Exception {
    if (orgMode())
      return new OrgSubtitles(context).search(imdb, season, episode, title, language, hash);
    return SubtitleQuery.results(
        request(
            "GET", SubtitleQuery.search(imdb, season, episode, title, language, hash), null, true));
  }

  File download(SubtitleQuery.Result result, File directory) throws Exception {
    if (result.provider.equals("org")) return new OrgSubtitles(context).download(result, directory);
    JSONObject json =
        request(
            "POST",
            "download",
            new JSONObject().put("file_id", result.fileId).put("sub_format", "srt"),
            true);
    String link = json.optString("link", "");
    if (!link.startsWith("https://"))
      throw new IOException("Subtitle download did not return a secure link");
    byte[] data = Net.bytes(link);
    if (data.length > 10000000) throw new IOException("Subtitle file too large");
    File file = new File(directory, "opensubtitles-" + result.fileId + ".srt");
    try (FileOutputStream out = new FileOutputStream(file)) {
      out.write(data);
    }
    return file;
  }
}
