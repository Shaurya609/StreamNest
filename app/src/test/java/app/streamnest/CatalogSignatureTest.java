package app.streamnest;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import org.json.*;
import org.junit.*;

public class CatalogSignatureTest {
  static KeyPair keys;
  static String pem;

  @BeforeClass
  public static void keys() throws Exception {
    KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
    gen.initialize(2048);
    keys = gen.generateKeyPair();
    pem =
        "-----BEGIN PUBLIC KEY-----\n"
            + Base64.getEncoder().encodeToString(keys.getPublic().getEncoded())
            + "\n-----END PUBLIC KEY-----";
  }

  byte[] signed(JSONObject payload) throws Exception {
    byte[] bytes = payload.toString().getBytes(StandardCharsets.UTF_8);
    Signature signature = Signature.getInstance("SHA256withRSA");
    signature.initSign(keys.getPrivate());
    signature.update(bytes);
    return new JSONObject()
        .put("payload", Base64.getEncoder().encodeToString(bytes))
        .put("signature", Base64.getEncoder().encodeToString(signature.sign()))
        .toString()
        .getBytes(StandardCharsets.UTF_8);
  }

  @Test
  public void signedConfigurationIsAcceptedAndTamperedPayloadIsRejected() throws Exception {
    byte[] data =
        signed(new JSONObject().put("schema", 1).put("generatedAt", 123).put("version", "test"));
    assertEquals("test", CatalogSignature.manifest(data, pem).getString("version"));
    JSONObject changed = new JSONObject(new String(data, StandardCharsets.UTF_8));
    changed.put(
        "payload",
        Base64.getEncoder()
            .encodeToString("{\"schema\":1,\"generatedAt\":124}".getBytes(StandardCharsets.UTF_8)));
    try {
      CatalogSignature.manifest(changed.toString().getBytes(StandardCharsets.UTF_8), pem);
      fail("Signature should fail");
    } catch (java.io.IOException expected) {
    }
  }

  @Test
  public void fileHashLengthAndPathAreChecked() throws Exception {
    byte[] bytes =
        "{\"saved\":1,\"movies\":[{\"imdb_code\":\"tt1\"}]}".getBytes(StandardCharsets.UTF_8);
    StringBuilder hash = new StringBuilder();
    for (byte b : MessageDigest.getInstance("SHA-256").digest(bytes))
      hash.append(String.format("%02x", b & 255));
    JSONObject entry =
        new JSONObject()
            .put("path", "catalogs/v1/data/movies-0123456789abcdef.json")
            .put("bytes", bytes.length)
            .put("sha256", hash.toString())
            .put("count", 1);
    assertEquals(1, CatalogSignature.data("movies", entry, bytes).getJSONArray("movies").length());
    entry.put("path", "../movies.json");
    try {
      CatalogSignature.data("movies", entry, bytes);
      fail("Unsafe path should fail");
    } catch (java.io.IOException expected) {
    }
  }
}
