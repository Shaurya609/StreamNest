package app.streamnest;

import android.content.*;
import android.graphics.*;
import android.os.*;
import android.util.LruCache;
import android.widget.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

final class PosterLoader implements AutoCloseable {
  final LruCache<String, Bitmap> cache;
  final ThreadPoolExecutor worker;
  final Handler ui = new Handler(Looper.getMainLooper());
  volatile boolean closed;
  final File disk;
  final int targetWidth, targetHeight;

  PosterLoader(Context c) {
    boolean lite = DeviceProfile.lite(c);
    targetWidth = lite ? 192 : 320;
    targetHeight = lite ? 288 : 480;
    disk = new File(c.getCacheDir(), "poster-thumbnails-v2-" + (lite ? "lite" : "standard"));
    disk.mkdirs();
    int bytes = (DeviceProfile.lite(c) ? 3 : 8) * 1024 * 1024;
    cache =
        new LruCache<String, Bitmap>(bytes) {
          protected int sizeOf(String k, Bitmap b) {
            return b.getAllocationByteCount();
          }
        };
    int threads = DeviceProfile.lite(c) ? 1 : 2;
    worker =
        new ThreadPoolExecutor(
            threads,
            threads,
            20,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(40),
            new ThreadPoolExecutor.DiscardOldestPolicy());
  }

  void load(String url, ImageView view) {
    view.setTag(url);
    view.setImageDrawable(null);
    Bitmap found = cache.get(url);
    if (found != null) {
      view.setImageBitmap(found);
      return;
    }
    if (url.isBlank() || closed) return;
    worker.execute(
        () -> {
          try {
            String key = orgHash(url);
            File local = new File(disk, key + ".jpg");
            BitmapFactory.Options localOptions = new BitmapFactory.Options();
            localOptions.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap existing =
                local.isFile() ? BitmapFactory.decodeFile(local.getPath(), localOptions) : null;
            if (existing != null) {
              publish(url, view, existing);
              return;
            }
            byte[] data = Net.bytes(url, 3 * 1024 * 1024);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = 1;
            while (bounds.outWidth / (options.inSampleSize * 2) >= targetWidth
                && bounds.outHeight / (options.inSampleSize * 2) >= targetHeight)
              options.inSampleSize *= 2;
            options.inPreferredConfig = Bitmap.Config.RGB_565;
            Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length, options);
            if (bitmap != null
                && (bitmap.getWidth() > targetWidth || bitmap.getHeight() > targetHeight)) {
              float ratio =
                  Math.min(
                      (float) targetWidth / bitmap.getWidth(),
                      (float) targetHeight / bitmap.getHeight());
              Bitmap scaled =
                  Bitmap.createScaledBitmap(
                      bitmap,
                      Math.max(1, Math.round(bitmap.getWidth() * ratio)),
                      Math.max(1, Math.round(bitmap.getHeight() * ratio)),
                      true);
              if (scaled != bitmap) bitmap.recycle();
              bitmap = scaled;
            }
            if (bitmap != null && !closed) {
              try {
                File temp = new File(disk, key + "-" + Thread.currentThread().getId() + ".tmp");
                try (FileOutputStream out = new FileOutputStream(temp)) {
                  bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out);
                }
                if (!temp.renameTo(local)) temp.delete();
                prune();
              } catch (IOException ignored) {
              }
              publish(url, view, bitmap);
            }
          } catch (Exception | OutOfMemoryError ignored) {
          }
        });
  }

  static String orgHash(String value) throws Exception {
    byte[] bytes =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    StringBuilder out = new StringBuilder();
    for (byte b : bytes) out.append(String.format(java.util.Locale.ROOT, "%02x", b & 255));
    return out.toString();
  }

  void publish(String url, ImageView view, Bitmap bitmap) {
    if (closed) return;
    cache.put(url, bitmap);
    ui.post(
        () -> {
          if (!closed && url.equals(view.getTag())) view.setImageBitmap(bitmap);
        });
  }

  synchronized void prune() {
    File[] files = disk.listFiles((folder, name) -> name.endsWith(".jpg"));
    if (files == null || files.length <= 128) return;
    Arrays.sort(files, Comparator.comparingLong(File::lastModified));
    for (int i = 0; i < files.length - 128; i++) files[i].delete();
  }

  void trim() {
    cache.evictAll();
  }

  public void close() {
    closed = true;
    worker.shutdownNow();
    ui.removeCallbacksAndMessages(null);
    cache.evictAll();
  }
}
