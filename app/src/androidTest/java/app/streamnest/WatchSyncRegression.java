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
    org.json.JSONObject previousGroup=SyncGroup.current;
    try {
      WatchSyncCrypto.parse(code);
      SyncGroup.update(context,null);
      if(!"Sprint 2 migration".equals(SyncGroup.current.optString("name")))throw new AssertionError("Existing group name was not retained");
      if(SyncGroup.current.getJSONArray("devices").length()!=2)throw new AssertionError("Desktop and Android device roster missing");
      SyncGroup.update(context,"Household");
      if(!code.equals(WatchSync.code(context)))throw new AssertionError("Naming changed pairing identity");
      WatchSync.sync(context, code);
      long received = WatchProgress.read(context, "episode:tt123:1:1", 0);
      if (received != 125000)
        throw new AssertionError("Desktop progress did not resume on Android: " + received);
      org.json.JSONObject imported=WatchProgress.records(context);
      boolean episodeSelection=imported.has("episode:tt123:1:2");
      java.util.List<org.json.JSONObject> episodes=java.util.Arrays.asList(
          new org.json.JSONObject().put("season",1).put("episode",1),
          new org.json.JSONObject().put("season",1).put("episode",2),
          new org.json.JSONObject().put("season",2).put("episode",1));
      if(episodeSelection) {
        if(!imported.getJSONObject("episode:tt123:1:2").optBoolean("completed"))throw new AssertionError("Completion flag lost across devices");
        org.json.JSONObject chosen=ResumeEpisodeSelection.choose("tt123",episodes,imported,System.currentTimeMillis());
        if(chosen==null||chosen.optInt("season")!=2||chosen.optInt("episode")!=1)throw new AssertionError("Synced completion did not select next season");
      }
      WatchProgress.save(context, "episode:tt123:1:1", 45000, 1500000);
      WatchSync.sync(context, code);
      if(episodeSelection) {
        org.json.JSONObject chosen=ResumeEpisodeSelection.choose("tt123",episodes,WatchProgress.records(context),System.currentTimeMillis());
        if(chosen==null||chosen.optInt("season")!=1||chosen.optInt("episode")!=1)throw new AssertionError("Newer rewind did not select replayed episode");
      }
      Bundle result = new Bundle();
      result.putString(
          "result",
          "PASS: desktop progress decrypted and restored; deliberate Android rewind encrypted and"
              + " synced; existing group migrated with two devices and shared name");
      result.putLong("receivedMs", received);
      result.putLong("sentMs", 45000);
      result.putBoolean("syncedEpisodeSelection",episodeSelection);
      return result;
    } finally {
      context.getSharedPreferences(temporary, 0).edit().clear().commit();
      WatchProgress.preferenceName = previous;
      WatchSync.testPairingCode = oldCode;
      WatchSync.pending = wasPending;
      SyncGroup.current=previousGroup;
    }
  }
}
