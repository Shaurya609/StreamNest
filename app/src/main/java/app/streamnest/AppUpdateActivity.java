package app.streamnest;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.widget.*;
import androidx.core.content.FileProvider;
import java.io.*;
import java.net.*;
import java.util.*;
import org.json.*;

public class AppUpdateActivity extends Activity {
  static final Object LOCK = new Object();
  static final String FEED =
      "https://raw.githubusercontent.com/Shaurya609/StreamNest/main/updates/stable.json";
  static volatile String message = "Checking for updates…";
  static volatile File ready;
  static volatile JSONObject metadata;
  static volatile boolean busy;
  static volatile long lastAutomatic;
  final Handler ui = new Handler(Looper.getMainLooper());
  TextView status;
  Button install;

  static void launchCheck(MainActivity activity) {
    long now = SystemClock.elapsedRealtime();
    if (busy || lastAutomatic != 0 && now - lastAutomatic < 300000) return;
    lastAutomatic = now;
    java.lang.ref.WeakReference<MainActivity> owner = new java.lang.ref.WeakReference<>(activity);
    Context context = activity.getApplicationContext();
    new Thread(
            () -> {
              try {
                check(context);
                MainActivity current = owner.get();
                if (current != null && ready != null)
                  current.runOnUiThread(
                      () -> {
                        if (!current.isFinishing() && current.hasWindowFocus())
                          current.startActivity(new Intent(current, AppUpdateActivity.class));
                      });
              } catch (Exception ignored) {
              }
            },
            "app-update-check")
        .start();
  }

  static void check(Context c) throws Exception {
    synchronized (LOCK) {
      busy = true;
      ready = null;
      metadata = null;
      try {
        message = "Checking for updates…";
        String key;
        try (InputStream in = c.getAssets().open("update-public-key.pem")) {
          key = new String(Net.read(in, 10000), java.nio.charset.StandardCharsets.UTF_8);
        }
        JSONObject doc =
            UpdatePolicy.manifest(
                Net.bytes(FEED, 100000, 8000, 10000), key, System.currentTimeMillis());
        android.content.SharedPreferences updatePrefs = c.getSharedPreferences("app-updates", 0);
        if (doc.getLong("generatedAt") < updatePrefs.getLong("lastGenerated", 0))
          throw new IOException("Older update feed rejected");
        updatePrefs.edit().putLong("lastGenerated", doc.getLong("generatedAt")).apply();
        JSONObject android = doc.getJSONObject("android");
        long current =
            c.getPackageManager().getPackageInfo(c.getPackageName(), 0).getLongVersionCode();
        if (android.getLong("versionCode") <= current) {
          new File(c.getFilesDir(), "app-updates/update.apk").delete();
          message = "StreamNest is up to date.";
          return;
        }
        if (android.optInt("minSdk", 28) > Build.VERSION.SDK_INT) {
          message = "This update needs a newer Android version.";
          return;
        }
        JSONObject item = android.getJSONObject("artifacts").getJSONObject("universal");
        UpdatePolicy.artifact(item);
        File folder = new File(c.getFilesDir(), "app-updates");
        if (!folder.exists() && !folder.mkdirs())
          throw new IOException("Cannot create update folder");
        File target = new File(folder, "update.apk"), partial = new File(folder, "update.part");
        if (!target.isFile()
            || target.length() != item.getLong("bytes")
            || !UpdatePolicy.digest(target).equals(item.getString("sha256"))) {
          HttpURLConnection connection =
              (HttpURLConnection) new URL(item.getString("url")).openConnection();
          connection.setConnectTimeout(15000);
          connection.setReadTimeout(20000);
          try {
            if (connection.getResponseCode() != 200)
              throw new IOException("Update download HTTP " + connection.getResponseCode());
            long received = 0;
            long deadline = SystemClock.elapsedRealtime() + 900000;
            try (InputStream in = connection.getInputStream();
                OutputStream out = new FileOutputStream(partial)) {
              byte[] b = new byte[65536];
              int n;
              while ((n = in.read(b)) != -1) {
                if (SystemClock.elapsedRealtime() > deadline)
                  throw new IOException("Update download timed out");
                received += n;
                if (received > item.getLong("bytes")) throw new IOException("Update size mismatch");
                out.write(b, 0, n);
                message = "Downloading update · " + (received * 100 / item.getLong("bytes")) + "%";
              }
            }
            if (received != item.getLong("bytes")
                || !UpdatePolicy.digest(partial).equals(item.getString("sha256")))
              throw new IOException("Update checksum rejected");
            java.nio.file.Files.move(
                partial.toPath(),
                target.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
          } finally {
            connection.disconnect();
            partial.delete();
          }
        }
        verifyApk(c, target, android.getLong("versionCode"));
        metadata = android;
        ready = target;
        message = "StreamNest " + android.getString("version") + " is ready to install.";
      } finally {
        busy = false;
      }
    }
  }

  static void verifyApk(Context c, File file, long expected) throws Exception {
    PackageManager pm = c.getPackageManager();
    PackageInfo
        incoming =
            pm.getPackageArchiveInfo(
                file.getAbsolutePath(), PackageManager.GET_SIGNING_CERTIFICATES),
        installed = pm.getPackageInfo(c.getPackageName(), PackageManager.GET_SIGNING_CERTIFICATES);
    if (incoming == null
        || !c.getPackageName().equals(incoming.packageName)
        || incoming.getLongVersionCode() != expected
        || incoming.getLongVersionCode() <= installed.getLongVersionCode())
      throw new IOException("Wrong app or update version");
    android.content.pm.Signature[] a = incoming.signingInfo.getApkContentsSigners(),
        b = installed.signingInfo.getApkContentsSigners();
    if (a.length != b.length) throw new IOException("Update signing certificate differs");
    Set<String> certs = new HashSet<>();
    for (android.content.pm.Signature s : b)
      certs.add(Base64.getEncoder().encodeToString(s.toByteArray()));
    for (android.content.pm.Signature s : a)
      if (!certs.contains(Base64.getEncoder().encodeToString(s.toByteArray())))
        throw new IOException("Update signing certificate differs");
  }

  public void onCreate(Bundle state) {
    super.onCreate(state);
    LinearLayout root = new LinearLayout(this);
    root.setOrientation(LinearLayout.VERTICAL);
    root.setPadding(32, 32, 32, 32);
    setContentView(root);
    status = new TextView(this);
    root.addView(status);
    install = new Button(this);
    install.setText("Install update");
    root.addView(install);
    install.setOnClickListener(v -> install());
    Button close = new Button(this);
    close.setText("Later");
    root.addView(close);
    close.setOnClickListener(v -> finish());
    if (ready == null && !busy)
      new Thread(
              () -> {
                try {
                  check(getApplicationContext());
                } catch (Exception error) {
                  message = "Update failed: " + error.getMessage();
                }
              },
              "manual-update")
          .start();
    ui.post(refresh);
  }

  final Runnable refresh =
      new Runnable() {
        public void run() {
          if (isDestroyed()) return;
          status.setText(message);
          install.setEnabled(ready != null && !busy);
          ui.postDelayed(this, 500);
        }
      };

  void install() {
    try {
      if (!getPackageManager().canRequestPackageInstalls()) {
        startActivity(
            new Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:" + getPackageName())));
        return;
      }
      File file = ready;
      JSONObject data = metadata;
      if (file == null || data == null) return;
      JSONObject artifact = data.getJSONObject("artifacts").getJSONObject("universal");
      if (file.length() != artifact.getLong("bytes")
          || !UpdatePolicy.digest(file).equals(artifact.getString("sha256")))
        throw new IOException("Update checksum rejected");
      verifyApk(this, file, data.getLong("versionCode"));
      Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".updates", file);
      startActivity(
          new Intent(Intent.ACTION_INSTALL_PACKAGE)
              .setData(uri)
              .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION));
    } catch (Exception error) {
      message = "Cannot install: " + error.getMessage();
    }
  }

  protected void onDestroy() {
    ui.removeCallbacksAndMessages(null);
    super.onDestroy();
  }
}
