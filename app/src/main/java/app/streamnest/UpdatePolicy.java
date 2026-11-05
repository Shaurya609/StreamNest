package app.streamnest;

import java.io.*;
import java.security.*;
import java.util.*;
import org.json.*;

final class UpdatePolicy {
  static JSONObject manifest(byte[] bytes, String key, long now) throws Exception {
    JSONObject doc = CatalogSignature.manifest(bytes, key);
    if (!doc.optString("product").equals("StreamNest")
        || doc.optLong("expiresAt") < now
        || doc.optLong("generatedAt") > now + 300000)
      throw new IOException("Invalid or expired update manifest");
    return doc;
  }

  static void artifact(JSONObject item) throws Exception {
    if (!item.getString("url")
            .matches(
                "https://github\\.com/Shaurya609/StreamNest/releases/download/[A-Za-z0-9._-]+/[A-Za-z0-9._-]+")
        || !item.getString("sha256").matches("[a-f0-9]{64}")
        || item.getLong("bytes") < 1
        || item.getLong("bytes") > 700000000) throw new IOException("Invalid update download");
  }

  static String digest(File file) throws Exception {
    MessageDigest hash = MessageDigest.getInstance("SHA-256");
    try (InputStream in = new FileInputStream(file)) {
      byte[] b = new byte[65536];
      int n;
      while ((n = in.read(b)) != -1) hash.update(b, 0, n);
    }
    StringBuilder out = new StringBuilder();
    for (byte b : hash.digest()) out.append(String.format(Locale.ROOT, "%02x", b & 255));
    return out.toString();
  }
}
