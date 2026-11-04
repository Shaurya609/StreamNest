package app.streamnest;

import java.io.*;
import java.net.*;

final class Net {
  private static final java.util.concurrent.ScheduledExecutorService deadlines =
      java.util.concurrent.Executors.newSingleThreadScheduledExecutor(
          r -> {
            Thread t = new Thread(r, "catalog-deadline");
            t.setDaemon(true);
            return t;
          });

  static byte[] catalogBytes(String url, int limit, long deadlineNanos) throws Exception {
    long remaining =
        java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadlineNanos - System.nanoTime());
    if (remaining <= 0) throw new java.net.SocketTimeoutException("Catalog request timed out");
    return bytes(
        url, limit, (int) Math.min(3000, remaining), (int) Math.min(5000, remaining), remaining);
  }

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
    return bytes(url, limit, connectTimeout, readTimeout, 0);
  }

  static byte[] bytes(String url, int limit, int connectTimeout, int readTimeout, long totalTimeout)
      throws Exception {
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
    java.util.concurrent.ScheduledFuture<?> stop =
        totalTimeout > 0
            ? deadlines.schedule(
                c::disconnect, totalTimeout, java.util.concurrent.TimeUnit.MILLISECONDS)
            : null;
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
      if (stop != null) stop.cancel(false);
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
