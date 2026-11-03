package app.streamnest;

import android.app.*;
import android.content.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;

public class StreamService extends Service {
  static volatile TorrentEngine engine;
  static final ExecutorService worker = Executors.newSingleThreadExecutor();

  public IBinder onBind(Intent i) {
    return null;
  }

  public void onCreate() {
    super.onCreate();
    getSystemService(NotificationManager.class)
        .createNotificationChannel(
            new NotificationChannel(
                "playback", "Torrent playback", NotificationManager.IMPORTANCE_LOW));
    promote();
  }

  void promote() {
    Intent stop = new Intent(this, StreamService.class).setAction("stop");
    PendingIntent action =
        PendingIntent.getService(
            this, 1, stop, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    Notification n =
        new Notification.Builder(this, "playback")
            .setContentTitle("StreamNest")
            .setContentText("Torrent playback is active")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .addAction(new Notification.Action.Builder(null, "Stop", action).build())
            .build();
    startForeground(7, n);
  }

  public int onStartCommand(Intent i, int flags, int id) {
    if (i == null) return START_NOT_STICKY;
    if ("stop".equals(i.getAction())) {
      stopSelf();
      return START_NOT_STICKY;
    }
    String source = i.getStringExtra("source");
    if (source == null) {
      stopSelf();
      return START_NOT_STICKY;
    }
    TorrentEngine next = new TorrentEngine();
    next.sourceUrl = source;
    next.fileIndex = i.getIntExtra("fileIndex", -1);
    next.expectedFilename =
        i.getStringExtra("filename") == null ? "" : i.getStringExtra("filename");
    next.episodeRequest =
        i.getStringExtra("episodeIdentity") != null
            && !i.getStringExtra("episodeIdentity").isEmpty();
    next.requestedSeason = i.getIntExtra("season", 0);
    next.requestedEpisode = i.getIntExtra("episode", 0);
    next.absoluteEpisode = i.getIntExtra("absolute", 0);
    next.publicMetadataLookup = i.getBooleanExtra("catalogSource", false);
    TorrentEngine previous = engine;
    if (previous != null) previous.closed = true;
    engine = next;
    worker.execute(
        () -> {
          if (previous != null) previous.close();
          try {
            File dir =
                new File(
                    CacheLocation.current(this),
                    Integer.toHexString(
                        (source
                                + (next.episodeRequest
                                    ? "|" + i.getStringExtra("episodeIdentity")
                                    : ""))
                            .hashCode()));
            next.lite = DeviceProfile.lite(this);
            CacheLocation.writable(CacheLocation.current(this));
            if (next.closed) return;
            PlaybackCache.prepareEpisode(this, i.getStringExtra("episodeIdentity"), dir);
            PlaybackCache.record(this, CacheLocation.current(this), dir);
            if (!next.closed) next.start(source, dir);
          } catch (Throwable e) {
            next.message =
                "Unable to stream: "
                    + (e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            android.util.Log.e("StreamNest", "Playback failed", e);
          }
        });
    return START_NOT_STICKY;
  }

  @Override
  public void onTaskRemoved(Intent rootIntent) {
    PlaybackCache.exit(this);
    super.onTaskRemoved(rootIntent);
  }

  public void onDestroy() {
    TorrentEngine e = engine;
    engine = null;
    if (e != null) e.closed = true;
    worker.execute(
        () -> {
          if (e != null) e.close();
          if (PlaybackCache.exiting && engine == null) PlaybackCache.purge(getApplicationContext());
        });
    super.onDestroy();
  }
}
