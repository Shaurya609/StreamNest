package app.streamnest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

final class WatchSyncCrypto {
  static final byte[] AAD = "StreamNest:watch-sync:v1".getBytes(StandardCharsets.UTF_8);

  static String hex(byte[] bytes) {
    StringBuilder b = new StringBuilder();
    for (byte v : bytes) b.append(String.format(Locale.ROOT, "%02x", v & 255));
    return b.toString();
  }

  static String checksum(byte[] root) throws Exception {
    return hex(MessageDigest.getInstance("SHA-256").digest(root)).substring(0, 4);
  }

  static String format(byte[] root) throws Exception {
    String text = (hex(root) + checksum(root)).toUpperCase(Locale.ROOT);
    return text.replaceAll("(.{4})(?!$)", "$1-");
  }

  static String create() throws Exception {
    byte[] root = new byte[16];
    new SecureRandom().nextBytes(root);
    return format(root);
  }

  static byte[] parse(String code) throws Exception {
    String text = code.replaceAll("[\\s-]", "").toLowerCase(Locale.ROOT);
    if (!text.matches("[a-f0-9]{36}"))
      throw new java.io.IOException("Enter the complete pairing code");
    byte[] root = new byte[16];
    for (int i = 0; i < 16; i++)
      root[i] = (byte) Integer.parseInt(text.substring(i * 2, i * 2 + 2), 16);
    if (!checksum(root).equals(text.substring(32)))
      throw new java.io.IOException("Pairing code has a typing error");
    return root;
  }

  static byte[] derive(byte[] root, String label) throws Exception {
    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec(root, "HmacSHA256"));
    return mac.doFinal(label.getBytes(StandardCharsets.UTF_8));
  }

  static String auth(String code) throws Exception {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(derive(parse(code), "StreamNest watch authentication v1"));
  }

  static JSONObject seal(String code, JSONObject records) throws Exception {
    byte[] iv = new byte[12];
    new SecureRandom().nextBytes(iv);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.ENCRYPT_MODE,
        new SecretKeySpec(derive(parse(code), "StreamNest watch encryption v1"), "AES"),
        new GCMParameterSpec(128, iv));
    cipher.updateAAD(AAD);
    byte[] data =
        cipher.doFinal(
            new JSONObject()
                .put("schema", 1)
                .put("records", records)
                .toString()
                .getBytes(StandardCharsets.UTF_8));
    return new JSONObject()
        .put("v", 1)
        .put("iv", Base64.getEncoder().encodeToString(iv))
        .put("body", Base64.getEncoder().encodeToString(data));
  }

  static JSONObject open(String code, JSONObject doc) throws Exception {
    if (doc.getInt("v") != 1) throw new java.io.IOException("Unsupported sync data");
    byte[] iv = Base64.getDecoder().decode(doc.getString("iv")),
        body = Base64.getDecoder().decode(doc.getString("body"));
    if (iv.length != 12 || body.length < 16 || body.length > 130000)
      throw new java.io.IOException("Invalid encrypted sync data");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        new SecretKeySpec(derive(parse(code), "StreamNest watch encryption v1"), "AES"),
        new GCMParameterSpec(128, iv));
    cipher.updateAAD(AAD);
    JSONObject plain = new JSONObject(new String(cipher.doFinal(body), StandardCharsets.UTF_8));
    if (plain.getInt("schema") != 1) throw new java.io.IOException("Unsupported watch data");
    return clean(plain.optJSONObject("records"));
  }

  static boolean integer(JSONObject row, String key) {
    Object value = row.opt(key);
    return value instanceof Number
        && Double.isFinite(((Number) value).doubleValue())
        && ((Number) value).doubleValue() == ((Number) value).longValue();
  }

  static JSONObject clean(JSONObject records) throws Exception {
    List<String> keys = new ArrayList<>();
    JSONObject out = new JSONObject();
    if (records == null) return out;
    for (Iterator<String> it = records.keys(); it.hasNext(); ) {
      String key = it.next();
      JSONObject row = records.optJSONObject(key);
      if (!key.matches("(movie:tt[0-9]+|episode:tt[0-9]+:[0-9]+:[0-9]+)")
          || row == null
          || !integer(row, "positionMs")
          || !integer(row, "durationMs")
          || !integer(row, "updatedAt")) continue;
      long position = row.getLong("positionMs"),
          duration = row.getLong("durationMs"),
          updated = row.getLong("updatedAt");
      if (position < 0
          || duration <= 0
          || position > duration
          || duration > 172800000
          || updated < 1
          || updated > System.currentTimeMillis() + 86400000) continue;
      out.put(
          key,
          new JSONObject()
              .put("positionMs", position)
              .put("durationMs", duration)
              .put("updatedAt", updated));
      if (Boolean.TRUE.equals(row.opt("completed"))) out.getJSONObject(key).put("completed", true);
      keys.add(key);
    }
    keys.sort(
        (a, b) ->
            Long.compare(
                out.optJSONObject(b).optLong("updatedAt"),
                out.optJSONObject(a).optLong("updatedAt")));
    for (int i = 500; i < keys.size(); i++) out.remove(keys.get(i));
    return out;
  }

  static JSONObject merge(JSONObject a, JSONObject b) throws Exception {
    JSONObject out = clean(a), other = clean(b);
    for (Iterator<String> it = other.keys(); it.hasNext(); ) {
      String key = it.next();
      JSONObject row = other.getJSONObject(key), old = out.optJSONObject(key);
      if (old == null
          || row.getLong("updatedAt") > old.getLong("updatedAt")
          || row.getLong("updatedAt") == old.getLong("updatedAt")
              && (row.getLong("positionMs") > old.getLong("positionMs")
                  || row.getLong("positionMs") == old.getLong("positionMs")
                      && row.getLong("durationMs") > old.getLong("durationMs"))) out.put(key, row);
      else if (row.getLong("updatedAt") == old.getLong("updatedAt")
          && row.getLong("positionMs") == old.getLong("positionMs")
          && row.getLong("durationMs") == old.getLong("durationMs")
          && row.optBoolean("completed")) out.put(key,row);
    }
    return clean(out);
  }
}
