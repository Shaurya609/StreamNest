package app.streamnest;

import static org.junit.Assert.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class ResumeEpisodeSelectionTest {
  @Test public void canonicalSelectionsMatchSharedDesktopVectors() throws Exception {
    JSONObject vectors = new JSONObject(new String(getClass().getResourceAsStream(
        "/resume-episodes-vector.json").readAllBytes(), StandardCharsets.UTF_8));
    List<JSONObject> episodes = new ArrayList<>();
    JSONArray rows = vectors.getJSONArray("episodes");
    for (int i=0;i<rows.length();i++) episodes.add(rows.getJSONObject(i));
    JSONArray cases = vectors.getJSONArray("cases");
    for(int i=0;i<cases.length();i++) {
      JSONObject test=cases.getJSONObject(i), chosen=ResumeEpisodeSelection.choose(
          vectors.getString("imdb"),episodes,test.getJSONObject("history"),vectors.getLong("now"));
      String actual=chosen==null?null:chosen.optInt("season")+":"+chosen.optInt("episode");
      assertEquals(test.getString("name"),test.isNull("expected")?null:test.getString("expected"),actual);
    }
  }

  @Test public void completedMarkerSurvivesEncryptionAndNewerRewindClearsIt() throws Exception {
    String code=WatchSyncCrypto.create();
    JSONObject completed=new JSONObject().put("episode:tt123:1:1",new JSONObject()
        .put("positionMs",0).put("durationMs",1500000).put("updatedAt",100).put("completed",true));
    JSONObject restored=WatchSyncCrypto.open(code,WatchSyncCrypto.seal(code,completed));
    assertTrue(restored.getJSONObject("episode:tt123:1:1").getBoolean("completed"));
    JSONObject legacy=new JSONObject(completed.toString());legacy.getJSONObject("episode:tt123:1:1").remove("completed");
    assertTrue(WatchSyncCrypto.merge(legacy,restored).getJSONObject("episode:tt123:1:1").getBoolean("completed"));
    JSONObject rewind=new JSONObject().put("episode:tt123:1:1",new JSONObject()
        .put("positionMs",12000).put("durationMs",1500000).put("updatedAt",200));
    assertFalse(WatchSyncCrypto.merge(restored,rewind).getJSONObject("episode:tt123:1:1").optBoolean("completed"));
  }
}
