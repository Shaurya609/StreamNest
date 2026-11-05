package app.streamnest;

import android.content.Context;
import android.security.keystore.*;
import android.util.Base64;
import java.security.KeyStore;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

final class SecretStore implements OrgSubtitles.Store {
  private final android.content.SharedPreferences prefs;
  private static final String ALIAS = "StreamNest.OpenSubtitles";

  SecretStore(Context context) {
    prefs = context.getSharedPreferences("subtitle-secrets", Context.MODE_PRIVATE);
  }

  private javax.crypto.SecretKey key() throws Exception {
    synchronized (SecretStore.class) {
      KeyStore store = KeyStore.getInstance("AndroidKeyStore");
      store.load(null);
      if (!store.containsAlias(ALIAS)) {
        KeyGenerator generator = KeyGenerator.getInstance("AES", "AndroidKeyStore");
        generator.init(
            new KeyGenParameterSpec.Builder(
                    ALIAS, KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        generator.generateKey();
      }
      return (javax.crypto.SecretKey) store.getKey(ALIAS, null);
    }
  }

  public synchronized void put(String name, String value) throws Exception {
    if (value == null || value.isEmpty()) {
      prefs.edit().remove(name).apply();
      return;
    }
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(Cipher.ENCRYPT_MODE, key());
    String data =
        Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP)
            + ":"
            + Base64.encodeToString(
                cipher.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
                Base64.NO_WRAP);
    prefs.edit().putString(name, data).apply();
  }

  public synchronized String get(String name) throws Exception {
    String data = prefs.getString(name, "");
    if (data.isEmpty()) return "";
    String[] parts = data.split(":", 2);
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(
        Cipher.DECRYPT_MODE,
        key(),
        new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
    return new String(
        cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)),
        java.nio.charset.StandardCharsets.UTF_8);
  }
}
