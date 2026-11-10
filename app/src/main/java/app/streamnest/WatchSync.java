package app.streamnest;

import android.content.Context;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import org.json.*;

final class WatchSync {
  static final ExecutorService worker =
      new ThreadPoolExecutor(0, 1, 30, TimeUnit.SECONDS, new LinkedBlockingQueue<>());
  static volatile boolean pending;
  static boolean followup;
  static volatile String testPairingCode;
  static long last;
  static volatile String status = "Not connected";

  static String endpoint(Context c) throws Exception {
    try (InputStream in = c.getAssets().open("watch-sync.json")) {
      String value =
          new JSONObject(new String(Net.read(in, 10000), StandardCharsets.UTF_8))
              .getString("endpoint");
      if (!value.matches("https://[A-Za-z0-9.-]+"))
        throw new IOException("Sync service is not configured yet");
      return value;
    }
  }

  static String code(Context c) throws Exception {
    return testPairingCode != null ? testPairingCode : new SecretStore(c).get("watchSyncCode");
  }

  static void connect(Context c, String code) throws Exception {
    String normalized = WatchSyncCrypto.format(WatchSyncCrypto.parse(code));
    new SecretStore(c).put("watchSyncCode", normalized);
    status = "Connecting…";
    request(c, true);
  }

  static void disconnect(Context c) throws Exception {
    String previous = code(c);
    Context app = c.getApplicationContext();
    SyncGroup.worker.execute(() -> {try{if(!previous.isEmpty())SyncGroup.call(app,previous,"DELETE",new JSONObject().put("deviceId",SyncGroup.deviceId(app)));}catch(Exception ignored){}});
    SyncGroup.current = null;
    new SecretStore(c).put("watchSyncCode", "");
    status = "Not connected";
  }

  static synchronized void request(Context c, boolean force) {
    if (pending) { if (force) followup = true; return; }
    if (!force && System.currentTimeMillis() - last < 10000) return;
    Context app = c.getApplicationContext();
    pending = true;
    last = System.currentTimeMillis();
    worker.execute(
        () -> {
          try {
            String pairing = code(app);
            if (pairing.isEmpty()) return;
            sync(app, pairing);
            if (pairing.equals(code(app))) {status = "Synced"; SyncGroup.refresh(app);}
          } catch (Exception e) {
            status = "Offline — progress stays on this device";
          } finally {
            synchronized (WatchSync.class) {
              pending = false;
              if (followup) { followup = false; request(app, true); }
            }
          }
        });
  }

  static JSONObject call(String url, String method, String auth, JSONObject data, long revision)
      throws Exception {
    HttpURLConnection c = (HttpURLConnection) new URL(url + "/v1/progress").openConnection();
    c.setConnectTimeout(8000);
    c.setReadTimeout(10000);
    c.setRequestMethod(method);
    c.setInstanceFollowRedirects(false);
    c.setRequestProperty("Authorization", "Bearer " + auth);
    c.setRequestProperty("Accept", "application/json");
    try {
      if (data != null) {
        byte[] bytes = data.toString().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 180000) throw new IOException("Watch history too large");
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("If-Match", String.valueOf(revision));
        c.setFixedLengthStreamingMode(bytes.length);
        c.setDoOutput(true);
        try (OutputStream out = c.getOutputStream()) {
          out.write(bytes);
        }
      }
      int response = c.getResponseCode();
      if (response == 409) return new JSONObject().put("conflict", true);
      if (response != 200) throw new IOException("Sync service HTTP " + response);
      try (InputStream in = c.getInputStream()) {
        return new JSONObject(new String(Net.read(in, 180000), StandardCharsets.UTF_8));
      }
    } finally {
      c.disconnect();
    }
  }

  static void sync(Context c, String pairing) throws Exception {
    String endpoint = endpoint(c), auth = WatchSyncCrypto.auth(pairing);
    for (int attempt = 0; attempt < 3; attempt++) {
      if (!pairing.equals(code(c))) return;
      JSONObject remote = call(endpoint, "GET", auth, null, 0);
      JSONObject incoming =
          remote.isNull("payload")
              ? new JSONObject()
              : WatchSyncCrypto.open(pairing, remote.getJSONObject("payload"));
      if (!pairing.equals(code(c))) return;
      WatchProgress.merge(c, incoming);
      JSONObject merged = WatchProgress.records(c);
      JSONObject response =
          call(
              endpoint,
              "PUT",
              auth,
              WatchSyncCrypto.seal(pairing, merged),
              remote.getLong("revision"));
      if (!response.optBoolean("conflict")) return;
    }
    throw new IOException("Another device is syncing; retry shortly");
  }
}
