package app.streamnest;

import org.json.JSONException;
import org.json.JSONObject;
import org.libtorrent4j.LibTorrent;
import org.libtorrent4j.SessionManager;
import org.libtorrent4j.alerts.SessionStatsAlert;

/** Samples one torrent session without making synchronous disk requests. */
final class TorrentDiskStats {
  static final String[] FIELDS = {
    "torrentPayloadBytes", "diskQueuedWriteBytes", "diskWriteOps", "diskWriteTimeUs",
    "diskHashedBlocks", "diskHashTimeUs", "torrentFailedHashBytes", "diskStatsAgeMs"
  };
  private static final String[] METRICS = {
    "net.recv_payload_bytes", "disk.queued_write_bytes", "disk.num_write_ops",
    "disk.disk_write_time", "disk.num_blocks_hashed", "disk.disk_hash_time",
    "net.recv_failed_bytes"
  };
  private final int[] indices = new int[METRICS.length];
  private long requestedAt;
  private volatile boolean pending;
  private volatile Sample latest;

  private static final class Sample {
    final long at = System.nanoTime();
    final long[] values;
    Sample(long[] values) { this.values = values; }
  }

  TorrentDiskStats() {
    for (int i = 0; i < indices.length; i++) indices[i] = LibTorrent.findMetricIdx(METRICS[i]);
  }

  void request(SessionManager session, long now) {
    if (pending || (requestedAt != 0 && now - requestedAt < 3_000_000_000L)) return;
    requestedAt = now;
    pending = true;
    try {
      session.postSessionStats();
    } catch (RuntimeException ignored) {
      pending = false;
    }
  }

  void accept(SessionStatsAlert alert) {
    if (!pending) return;
    try {
      long[] values = new long[indices.length];
      for (int i = 0; i < indices.length; i++)
        values[i] = indices[i] < 0 ? -1 : alert.value(indices[i]);
      latest = new Sample(values);
    } finally {
      pending = false;
    }
  }

  void append(JSONObject report) throws JSONException {
    Sample sample = latest;
    if (sample == null) return;
    for (int i = 0; i < sample.values.length; i++) {
      if (sample.values[i] >= 0) report.put(FIELDS[i], sample.values[i]);
    }
    report.put("diskStatsAgeMs", Math.max(0, (System.nanoTime() - sample.at) / 1_000_000L));
  }
}
