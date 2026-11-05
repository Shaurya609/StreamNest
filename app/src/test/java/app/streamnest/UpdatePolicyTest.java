package app.streamnest;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;
import org.json.*;
import org.junit.Test;

public class UpdatePolicyTest {
  byte[] envelope(JSONObject doc, KeyPair pair) throws Exception {
    byte[] payload = doc.toString().getBytes(StandardCharsets.UTF_8);
    Signature signer = Signature.getInstance("SHA256withRSA");
    signer.initSign(pair.getPrivate());
    signer.update(payload);
    return new JSONObject()
        .put("payload", Base64.getEncoder().encodeToString(payload))
        .put("signature", Base64.getEncoder().encodeToString(signer.sign()))
        .toString()
        .getBytes(StandardCharsets.UTF_8);
  }

  @Test
  public void rejectsTamperingAndExpiry() throws Exception {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(2048);
    KeyPair key = generator.generateKeyPair();
    String pem = Base64.getEncoder().encodeToString(key.getPublic().getEncoded());
    JSONObject doc =
        new JSONObject()
            .put("schema", 1)
            .put("generatedAt", 1000)
            .put("expiresAt", 3000)
            .put("product", "StreamNest");
    byte[] bytes = envelope(doc, key);
    assertEquals("StreamNest", UpdatePolicy.manifest(bytes, pem, 2000).getString("product"));
    try {
      UpdatePolicy.manifest(bytes, pem, 4000);
      fail();
    } catch (java.io.IOException expected) {
    }
    JSONObject altered = new JSONObject(new String(bytes, StandardCharsets.UTF_8));
    altered.put(
        "payload",
        Base64.getEncoder()
            .encodeToString(
                doc.put("expiresAt", 9000).toString().getBytes(StandardCharsets.UTF_8)));
    try {
      UpdatePolicy.manifest(altered.toString().getBytes(StandardCharsets.UTF_8), pem, 2000);
      fail();
    } catch (java.io.IOException expected) {
    }
  }

  @Test
  public void rejectsForeignDownloadsAndUnboundedSizes() throws Exception {
    JSONObject item =
        new JSONObject()
            .put(
                "url",
                "https://github.com/Shaurya609/StreamNest/releases/download/v0.12.0/StreamNest.apk")
            .put("sha256", "a".repeat(64))
            .put("bytes", 100);
    UpdatePolicy.artifact(item);
    item.put("url", "https://example.com/update.apk");
    try {
      UpdatePolicy.artifact(item);
      fail();
    } catch (java.io.IOException expected) {
    }
  }
}
