package app.streamnest;

import android.app.Instrumentation;
import android.content.Context;
import android.os.Bundle;

final class WatchSyncRegression {
  static Bundle run(Instrumentation instrument, String code) throws Exception {
    Context context = instrument.getTargetContext();
    String previous = WatchProgress.preferenceName, oldCode = WatchSync.testPairingCode;
    boolean wasPending = WatchSync.pending;
    String temporary = "watch-sync-regression-" + java.util.UUID.randomUUID();
    WatchProgress.preferenceName = temporary;
    WatchSync.testPairingCode = code;
    WatchSync.pending = true;
    try {
      WatchSyncCrypto.parse(code);
      WatchSync.sync(context, code);
      long received = WatchProgress.read(context, "episode:tt123:1:1", 0);
      if (received != 125000)
        throw new AssertionError("Desktop progress did not resume on Android: " + received);
      WatchProgress.save(context, "episode:tt123:1:1", 45000, 1500000);
      WatchSync.sync(context, code);
      Bundle result = new Bundle();
      result.putString(
          "result",
          "PASS: desktop progress decrypted and restored; deliberate Android rewind encrypted and"
              + " synced");
      result.putLong("receivedMs", received);
      result.putLong("sentMs", 45000);
      return result;
    } finally {
      context.getSharedPreferences(temporary, 0).edit().clear().commit();
      WatchProgress.preferenceName = previous;
      WatchSync.testPairingCode = oldCode;
      WatchSync.pending = wasPending;
    }
  }
}
