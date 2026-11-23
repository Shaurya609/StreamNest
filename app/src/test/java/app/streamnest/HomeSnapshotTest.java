package app.streamnest;
import org.junit.Test;
import org.json.*;
import java.util.*;
import static org.junit.Assert.*;

public class HomeSnapshotTest {
 Catalog.Title title(String id,String kind)throws Exception{return Catalog.parse(new JSONObject().put("imdb",id).put("title",id).put("genres",new JSONArray().put("drama")),kind);}
 @Test public void savedTitlesUseCurrentProgressAndNeverRestoreRemovedPositions()throws Exception{
  Catalog.Title t=title("tt123","shows");
  JSONObject old=new JSONObject().put("episode:tt123:1:1",new JSONObject().put("positionMs",10000).put("durationMs",120000).put("updatedAt",100));
  JSONObject snapshot=HomeSnapshot.encode(HomeFeed.build(Arrays.asList(t),old,12));
  assertFalse(snapshot.toString().contains("positionMs"));
  List<Catalog.Title> restored=HomeSnapshot.decode(snapshot.toString());
  JSONObject current=new JSONObject().put("episode:tt123:1:2",new JSONObject().put("positionMs",90000).put("durationMs",120000).put("updatedAt",200));
  HomeFeed.Card card=HomeFeed.build(restored,current,12).get(0).cards.get(0);
  assertEquals("episode:tt123:1:2",card.historyKey);assertEquals(90000,card.progress.getLong("positionMs"));
  assertTrue(HomeFeed.build(restored,new JSONObject(),12).get(0).cards.isEmpty());
 }
 @Test public void watchedAndEpisodeCompletionAreRecomputedFromFreshState()throws Exception{
  Catalog.Title t=title("tt123","shows");t.raw.put("homeEpisodeOrder",new JSONArray().put("1:1").put("1:2"));
  JSONObject done=new JSONObject().put("episode:tt123:1:1",new JSONObject().put("completed",true).put("positionMs",120000).put("durationMs",120000).put("updatedAt",100));
  List<Catalog.Title> restored=HomeSnapshot.decode(HomeSnapshot.encode(HomeFeed.build(Arrays.asList(t),done,12)).toString());
  assertEquals("episode:tt123:1:2",HomeFeed.build(restored,done,12).get(0).cards.get(0).historyKey);
  JSONObject manual=new JSONObject().put("tt123",new JSONObject().put("watched",true).put("whole",true).put("updatedAt",200));
  assertTrue(HomeFeed.build(restored,done,12,manual).get(0).cards.isEmpty());
 }
 @Test public void malformedFutureAndOversizedSnapshotsDoNotBecomeHomeData()throws Exception{
  assertTrue(HomeSnapshot.decode(new JSONObject().put("schema",2).put("titles",new JSONArray()).toString()).isEmpty());
  assertTrue(HomeSnapshot.decode("x".repeat(HomeSnapshot.MAX_BYTES+1)).isEmpty());
  JSONArray titles=new JSONArray();for(int i=0;i<=HomeSnapshot.MAX_TITLES;i++)titles.put(new JSONObject());
  assertTrue(HomeSnapshot.decode(new JSONObject().put("schema",1).put("titles",titles).toString()).isEmpty());
 }
}
