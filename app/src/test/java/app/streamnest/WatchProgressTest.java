package app.streamnest;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class WatchProgressTest {
  @Test
  public void canonicalIdentitySurvivesQualityChangeAndSeparatesEpisodes() {
    assertEquals(
        WatchProgress.identity("tt123", 0, 0, "1080p"),
        WatchProgress.identity("tt123", 0, 0, "4k"));
    assertNotEquals(
        WatchProgress.identity("tt123", 1, 1, "source"),
        WatchProgress.identity("tt123", 1, 2, "source"));
    assertNotEquals(
        WatchProgress.identity("tt123", 1, 1, "source"),
        WatchProgress.identity("tt123", 2, 1, "source"));
  }

  @Test
  public void completedTitlesRestartButLateUnfinishedContentCanResume() {
    assertEquals(900000, WatchProgress.resumable(900000, 1000000));
    assertEquals(0, WatchProgress.resumable(990000, 1000000));
    assertEquals(0, WatchProgress.resumable(2000, 1000000));
  }

  @Test
  public void desktopCryptoVectorDecryptsOnAndroidAndMatchesAuthentication() throws Exception {
    JSONObject v =
        new JSONObject(
            new String(
                getClass().getResourceAsStream("/watch-sync-vector.json").readAllBytes(),
                StandardCharsets.UTF_8));
    assertEquals(v.getString("auth"), WatchSyncCrypto.auth(v.getString("code")));
    JSONObject plain = WatchSyncCrypto.open(v.getString("code"), v.getJSONObject("payload"));
    assertEquals(125000, plain.getJSONObject("episode:tt123:1:1").getLong("positionMs"));
    String bad = v.getString("code");
    try {
      WatchSyncCrypto.parse(bad.substring(0, bad.length() - 1) + (bad.endsWith("0") ? "1" : "0"));
      fail();
    } catch (java.io.IOException expected) {
    }
  }

  @Test
  public void newerRewindAndCompletedStateOverrideOlderProgress() throws Exception {
    JSONObject old =
        new JSONObject()
            .put(
                "movie:tt123",
                new JSONObject()
                    .put("positionMs", 90000)
                    .put("durationMs", 1000000)
                    .put("updatedAt", 1000));
    JSONObject rewind =
        new JSONObject()
            .put(
                "movie:tt123",
                new JSONObject()
                    .put("positionMs", 30000)
                    .put("durationMs", 1000000)
                    .put("updatedAt", 2000));
    assertEquals(
        30000,
        WatchSyncCrypto.merge(old, rewind).getJSONObject("movie:tt123").getLong("positionMs"));
    rewind.getJSONObject("movie:tt123").put("positionMs", 0);
    assertEquals(
        0, WatchSyncCrypto.merge(old, rewind).getJSONObject("movie:tt123").getLong("positionMs"));
  }
  @Test public void titleRemovalSurvivesStaleMergeAndNewPlaybackRestarts()throws Exception{
    JSONObject old=new JSONObject().put("episode:tt123:1:1",new JSONObject().put("positionMs",12000).put("durationMs",120000).put("updatedAt",100)).put("episode:tt123:2:3",new JSONObject().put("positionMs",20000).put("durationMs",120000).put("updatedAt",200));
    JSONObject removed=new JSONObject().put("removed:tt123",new JSONObject().put("positionMs",0).put("durationMs",0).put("updatedAt",300));
    for(JSONObject result:new JSONObject[]{WatchSyncCrypto.merge(old,removed),WatchSyncCrypto.merge(removed,old)}){assertFalse(result.has("episode:tt123:1:1"));assertFalse(result.has("episode:tt123:2:3"));assertTrue(result.has("removed:tt123"));String code=WatchSyncCrypto.create();assertEquals(result.toString(),WatchSyncCrypto.open(code,WatchSyncCrypto.seal(code,result)).toString());}
    assertTrue(HomeSync.latest(removed).isEmpty());
    old.getJSONObject("episode:tt123:1:1").put("updatedAt",301);assertTrue(WatchSyncCrypto.merge(removed,old).has("episode:tt123:1:1"));
  }
}
