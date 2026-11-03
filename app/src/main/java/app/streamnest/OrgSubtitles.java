package app.streamnest;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.zip.GZIPInputStream;

final class OrgSubtitles {
  final SecretStore secrets;

  OrgSubtitles(Context c) {
    secrets = new SecretStore(c);
  }

  Map<String, Object> rpc(String method, Object... args) throws Exception {
    HttpURLConnection c =
        (HttpURLConnection) new URL("https://api.opensubtitles.org/xml-rpc").openConnection();
    c.setConnectTimeout(12000);
    c.setReadTimeout(20000);
    c.setRequestMethod("POST");
    c.setInstanceFollowRedirects(false);
    c.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
    c.setRequestProperty("User-Agent", "opensubtitles-api v5.1.2");
    c.setDoOutput(true);
    try {
      try (OutputStream out = c.getOutputStream()) {
        out.write(XmlRpc.request(method, args));
      }
      if (c.getResponseCode() == 401 && method.equals("LogIn"))
        throw new IOException(OrgAuth.loginError("HTTP 401"));
      if (c.getResponseCode() != 200)
        throw new IOException("OpenSubtitles.org returned HTTP " + c.getResponseCode());
      byte[] bytes;
      try (InputStream in = c.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] b = new byte[8192];
        int n;
        while ((n = in.read(b)) != -1) {
          out.write(b, 0, n);
          if (out.size() > 8000000) throw new IOException("Subtitle response too large");
        }
        bytes = out.toByteArray();
      }
      Object result = XmlRpc.response(bytes);
      if (!(result instanceof Map)) throw new IOException("Unexpected subtitle response");
      Map<String, Object> map = (Map<String, Object>) result;
      String status = String.valueOf(map.get("status"));
      if (!status.startsWith("200")) {
        if (status.startsWith("401") || status.startsWith("406")) secrets.put("orgToken", "");
        if (method.equals("LogIn") && status.startsWith("401"))
          throw new IOException(OrgAuth.loginError(status));
        throw new IOException("OpenSubtitles.org: " + status);
      }
      return map;
    } finally {
      c.disconnect();
    }
  }

  boolean signedIn() {
    try {
      return !secrets.get("orgToken").isEmpty();
    } catch (Exception e) {
      return false;
    }
  }

  void login(String user, String password) throws Exception {
    Map<String, Object> result =
        rpc("LogIn", user, OrgAuth.digest(password), "en", "Butter v0.5.1");
    String token = String.valueOf(result.getOrDefault("token", ""));
    if (token.isEmpty()) throw new IOException("No OpenSubtitles.org session was returned");
    secrets.put("orgToken", token);
    secrets.put("orgAnonymous", "false");
  }

  void anonymous() throws Exception {
    Map<String, Object> result = rpc("LogIn", "", "", "en", "Popcorn Time NodeJS");
    String token = String.valueOf(result.getOrDefault("token", ""));
    if (token.isEmpty()) throw new IOException("No anonymous subtitle session returned");
    secrets.put("orgToken", token);
    secrets.put("orgAnonymous", "true");
    secrets.put("orgAnonymousUntil", String.valueOf(System.currentTimeMillis() + 840000));
  }

  boolean anonymousMode() {
    try {
      return secrets.get("orgAnonymous").equals("true");
    } catch (Exception e) {
      return false;
    }
  }

  void logout() throws Exception {
    String token = secrets.get("orgToken");
    secrets.put("orgToken", "");
    secrets.put("orgAnonymous", "false");
    if (!token.isEmpty())
      try {
        rpc("LogOut", token);
      } catch (Exception ignored) {
      }
  }

  List<SubtitleQuery.Result> search(
      String imdb, int season, int episode, String title, String language, String hash)
      throws Exception {
    if (anonymousMode()) {
      long until;
      try {
        until = Long.parseLong(secrets.get("orgAnonymousUntil"));
      } catch (Exception e) {
        until = 0;
      }
      if (System.currentTimeMillis() >= until) anonymous();
    }
    String token = secrets.get("orgToken");
    if (token.isEmpty()) throw new IOException("Connect to OpenSubtitles.org in Settings");
    Map<String, Object> q = new LinkedHashMap<>();
    q.put("sublanguageid", language.equals("hi") ? "hin" : "eng");
    if (imdb != null && imdb.matches("tt[0-9]+")) q.put("imdbid", imdb.substring(2));
    else q.put("query", title == null ? "" : title);
    if (season > 0) {
      q.put("season", String.valueOf(season));
      q.put("episode", String.valueOf(episode));
    }
    if (hash != null && !hash.isEmpty()) q.put("moviehash", hash);
    Map<String, Object> response = rpc("SearchSubtitles", token, Collections.singletonList(q));
    List<SubtitleQuery.Result> results = new ArrayList<>();
    Object data = response.get("data");
    if (data instanceof List)
      for (Object row : (List<?>) data) {
        if (!(row instanceof Map)) continue;
        Map<?, ?> a = (Map<?, ?>) row;
        Object fileId = a.get("IDSubtitleFile");
        if (fileId == null) continue;
        try {
          int id = Integer.parseInt(String.valueOf(fileId));
          String release = String.valueOf(a.get("MovieReleaseName"));
          if (release.equals("null")) release = String.valueOf(a.get("SubFileName"));
          SubtitleQuery.Result result =
              new SubtitleQuery.Result(
                  id, language + " · " + release, "moviehash".equals(a.get("MatchedBy")));
          result.provider = "org";
          results.add(result);
        } catch (NumberFormatException ignored) {
        }
      }
    results.sort((a, b) -> Boolean.compare(b.exact, a.exact));
    return results;
  }

  File download(SubtitleQuery.Result result, File directory) throws Exception {
    Map<String, Object> response =
        rpc(
            "DownloadSubtitles",
            secrets.get("orgToken"),
            Collections.singletonList(String.valueOf(result.fileId)));
    Object data = response.get("data");
    if (!(data instanceof List) || ((List<?>) data).isEmpty())
      throw new IOException("No subtitle data returned");
    Object compressed = ((Map<?, ?>) ((List<?>) data).get(0)).get("data");
    byte[] bytes =
        compressed instanceof byte[]
            ? (byte[]) compressed
            : java.util.Base64.getMimeDecoder().decode(String.valueOf(compressed));
    InputStream input = new ByteArrayInputStream(bytes);
    if (bytes.length >= 2 && (bytes[0] & 255) == 31 && (bytes[1] & 255) == 139)
      input = new GZIPInputStream(input);
    File file = new File(directory, "opensubtitles-org-" + result.fileId + ".srt");
    try (InputStream in = input;
        ByteArrayOutputStream out = new ByteArrayOutputStream()) {
      byte[] b = new byte[8192];
      int n;
      while ((n = in.read(b)) != -1) {
        out.write(b, 0, n);
        if (out.size() > 10000000) throw new IOException("Subtitle file too large");
      }
      SubtitleQuery.rejectServiceNotice(out.toByteArray());
      try (FileOutputStream f = new FileOutputStream(file)) {
        out.writeTo(f);
      }
    }
    return file;
  }
}
