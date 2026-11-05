package app.streamnest;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.*;
import org.json.*;

final class PairingTicket {
  static String normalize(String text) throws Exception {
    String value = text.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    if (!value.matches("[A-HJ-NP-Z2-9]{8}")) throw new java.io.IOException("Enter the eight-character code");
    return value;
  }
  static String create() {
    String alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    byte[] bytes = new byte[8];
    new SecureRandom().nextBytes(bytes);
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) out.append(alphabet.charAt(b & 31));
    return out.toString();
  }
  static byte[] digest(String code) throws Exception {
    return MessageDigest.getInstance("SHA-256").digest(
        ("StreamNest pairing v1:" + normalize(code)).getBytes(StandardCharsets.UTF_8));
  }
  static String lookup(String code) throws Exception {
    return WatchSyncCrypto.hex(MessageDigest.getInstance("SHA-256").digest(
        ("StreamNest pairing lookup v1:" + normalize(code)).getBytes(StandardCharsets.UTF_8)));
  }
  static String seal(String code, String recovery) throws Exception {
    byte[] iv = new byte[12];
    new SecureRandom().nextBytes(iv);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(digest(code), "AES"), new GCMParameterSpec(128,iv));
    byte[] encrypted = cipher.doFinal(recovery.getBytes(StandardCharsets.UTF_8));
    byte[] out = new byte[iv.length + encrypted.length];
    System.arraycopy(iv,0,out,0,iv.length);
    System.arraycopy(encrypted,0,out,iv.length,encrypted.length);
    return Base64.getEncoder().encodeToString(out);
  }
  static String open(String code, String payload) throws Exception {
    byte[] bytes = Base64.getDecoder().decode(payload);
    if (bytes.length < 29 || bytes.length > 1000) throw new java.io.IOException("Invalid pairing ticket");
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(digest(code), "AES"),
        new GCMParameterSpec(128, Arrays.copyOf(bytes,12)));
    return new String(cipher.doFinal(bytes,12,bytes.length-12),StandardCharsets.UTF_8);
  }
  static JSONObject call(android.content.Context context, String code, String method, String payload, String auth) throws Exception {
    java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
        new java.net.URL(WatchSync.endpoint(context)+"/v1/pair/"+lookup(code)).openConnection();
    connection.setConnectTimeout(8000);
    connection.setReadTimeout(10000);
    connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod(method);
    if (auth != null) connection.setRequestProperty("Authorization","Bearer "+auth);
    try {
      connection.setDoOutput(true);
      byte[] bytes = payload == null ? new byte[0] : payload.getBytes(StandardCharsets.UTF_8);
      connection.setFixedLengthStreamingMode(bytes.length);
      try (java.io.OutputStream out = connection.getOutputStream()) { out.write(bytes); }
      int status = connection.getResponseCode();
      java.io.InputStream stream = status == 200 ? connection.getInputStream() : connection.getErrorStream();
      if (stream == null) throw new java.io.IOException("Pairing unavailable");
      JSONObject doc;
      try(java.io.InputStream in = stream) {
        doc = new JSONObject(new String(Net.read(in,2000),StandardCharsets.UTF_8));
      }
      if (status != 200) throw new java.io.IOException(doc.optString("error","Pairing unavailable"));
      return doc;
    } finally { connection.disconnect(); }
  }
}
