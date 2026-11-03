package app.streamnest;

import java.io.*;
import java.net.*;

final class Net {
  static byte[] read(InputStream in, int limit) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    byte[] b = new byte[16384];
    int n;
    while ((n = in.read(b)) != -1) {
      out.write(b, 0, n);
      if (out.size() > limit) throw new IOException("Response too large");
    }
    return out.toByteArray();
  }

  static byte[] bytes(String url) throws Exception {
    return bytes(url, 16 * 1024 * 1024);
  }

  static byte[] bytes(String url, int limit) throws Exception {
    return bytes(url, limit, 12000, 18000);
  }

  static byte[] bytes(String url, int limit, int connectTimeout, int readTimeout) throws Exception {
    URI uri = URI.create(url);
    if (!"http".equals(uri.getScheme()) && !"https".equals(uri.getScheme()))
      throw new IOException("Use an HTTP or HTTPS address");
    HttpURLConnection c = (HttpURLConnection) uri.toURL().openConnection();
    c.setConnectTimeout(connectTimeout);
    c.setReadTimeout(readTimeout);
    c.setRequestProperty(
        "User-Agent",
        (uri.getHost().endsWith("apiabcd.com")
                || uri.getHost().equals("yts.bz")
                || uri.getHost().equals("yts.gg"))
            ? "okhttp/3.14.7"
            : "StreamNest/0.6.0");
    try {
      if (c.getResponseCode() != 200)
        throw new IOException("Server returned HTTP " + c.getResponseCode());
      try (InputStream in = c.getInputStream();
          ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        byte[] b = new byte[16384];
        int n;
        while ((n = in.read(b)) != -1) {
          out.write(b, 0, n);
          if (out.size() > limit) throw new IOException("Response too large");
        }
        return out.toByteArray();
      }
    } finally {
      c.disconnect();
    }
  }

  static String text(String u) throws Exception {
    return new String(bytes(u), java.nio.charset.StandardCharsets.UTF_8);
  }

  static String q(String s) {
    try {
      return java.net.URLEncoder.encode(s, "UTF-8");
    } catch (java.io.UnsupportedEncodingException e) {
      throw new AssertionError(e);
    }
  }
}
