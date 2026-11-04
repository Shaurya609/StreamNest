package app.streamnest;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;
import org.json.*;

/** Signed data only; downloaded catalogs never contain executable provider code. */
final class CatalogSignature {
  static JSONObject manifest(byte[] bytes, String publicKey) throws Exception {
    if (bytes.length > 100000) throw new IOException("Catalog manifest too large");
    JSONObject envelope = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    byte[] payload = Base64.getDecoder().decode(envelope.getString("payload"));
    String pem =
        publicKey
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    PublicKey key =
        KeyFactory.getInstance("RSA")
            .generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pem)));
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initVerify(key);
    signature.update(payload);
    if (!signature.verify(Base64.getDecoder().decode(envelope.getString("signature"))))
      throw new IOException("Catalog signature rejected");
    JSONObject doc = new JSONObject(new String(payload, StandardCharsets.UTF_8));
    if (doc.getInt("schema") != 1 || doc.optLong("generatedAt") <= 0)
      throw new IOException("Unsupported catalog manifest");
    return doc;
  }

  static void verifyFile(String kind, JSONObject metadata, byte[] bytes) throws Exception {
    String path = metadata.getString("path");
    if (!path.matches("catalogs/v1/data/" + kind + "-[0-9a-f]{16}\\.json")
        || bytes.length > 16000000
        || bytes.length != metadata.getInt("bytes")) throw new IOException("Invalid catalog file");
    StringBuilder hash = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
      hash.append(String.format(Locale.ROOT, "%02x", b & 255));
    if (!hash.toString().equals(metadata.getString("sha256")))
      throw new IOException("Catalog checksum rejected");
  }

  static JSONObject data(String kind, JSONObject metadata, byte[] bytes) throws Exception {
    verifyFile(kind, metadata, bytes);
    JSONObject doc = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    String field = kind.equals("movies") ? "movies" : kind.equals("latest") ? "releases" : "series";
    JSONArray rows = doc.getJSONArray(field);
    if (rows.length() != metadata.getInt("count")
        || rows.length() > 30000
        || (!kind.equals("latest") && rows.length() == 0))
      throw new IOException("Invalid catalog record count");
    return doc;
  }
}
