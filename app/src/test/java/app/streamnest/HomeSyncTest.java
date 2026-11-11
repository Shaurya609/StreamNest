package app.streamnest;
import org.junit.Test;
import org.json.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import static org.junit.Assert.*;
public class HomeSyncTest {
  JSONObject vector()throws Exception{try(java.io.InputStream in=getClass().getResourceAsStream("/group-home-vector.json")){return new JSONObject(new String(in.readAllBytes(),StandardCharsets.UTF_8));}}
  Catalog.Title title(String id,String kind)throws Exception{return Catalog.parse(new JSONObject().put("imdb",id).put("title",id).put("genres",new JSONArray().put("action")),kind);}
  @Test public void pollingBoundsRoutineRequestsButNotNewChoices(){
    assertTrue(HomeSync.shouldPoll("test-group","first",1000));assertFalse(HomeSync.shouldPoll("test-group","first",2000));assertTrue(HomeSync.shouldPoll("test-group","new",2001));assertFalse(HomeSync.shouldPoll("test-group","new",60000));assertTrue(HomeSync.shouldPoll("test-group","new",62001));assertTrue(HomeSync.shouldPoll("other-group","new",62002));
  }
  @Test public void sharedGeneratorPreservesAnimeIdentity()throws Exception{
    JSONObject home=HomeSync.create(Arrays.asList(title("tt2","animes"),title("tt2","shows")),new JSONObject(),null,1000);assertEquals("animes",home.getJSONObject("titles").getJSONObject("tt2").getString("kind"));
  }
  @Test public void desktopEnvelopeAndNamespaceAreCompatibleButDistinctFromProgress()throws Exception{
    JSONObject v=vector();assertEquals(v.getString("homeCode"),WatchSyncCrypto.homeCode(v.getString("code")));assertNotEquals(WatchSyncCrypto.auth(v.getString("code")),WatchSyncCrypto.auth(v.getString("homeCode")));
    JSONObject snapshot=WatchSyncCrypto.openDocument(v.getString("homeCode"),v.getJSONObject("encrypted"));assertEquals(v.getJSONObject("snapshot").getString("history"),snapshot.getString("history"));assertEquals(snapshot.getString("history"),HomeSync.signature(v.getJSONObject("history")));assertEquals(0,WatchSyncCrypto.open(v.getString("homeCode"),v.getJSONObject("encrypted")).length());
  }
  @Test public void nativeGeneratorMatchesTheDesktopRowVector()throws Exception{
    JSONObject v=vector(),snapshot=v.getJSONObject("snapshot");List<Catalog.Title> pool=new ArrayList<>();JSONObject titles=snapshot.getJSONObject("titles");for(Iterator<String> it=titles.keys();it.hasNext();){JSONObject t=titles.getJSONObject(it.next());pool.add(Catalog.parse(t,t.getString("kind")));}
    JSONObject generated=HomeSync.create(pool,v.getJSONObject("history"),null,1000);assertEquals(snapshot.getJSONArray("rows").toString(),generated.getJSONArray("rows").toString());
  }
  @Test public void devicesWithDifferentCatalogsKeepSameSharedOrderAndLowMemoryPrefix()throws Exception{
    JSONObject v=vector(),history=v.getJSONObject("history"),home=v.getJSONObject("snapshot");List<Catalog.Title> empty=new ArrayList<>();List<HomeFeed.Row> local=HomeFeed.build(empty,history,12),shared=HomeSync.apply(local,empty,history,home,12),lite=HomeSync.apply(local,empty,history,home,1);
    assertEquals("tt2",shared.get(1).cards.get(0).title.id);assertEquals("tt3",shared.get(1).cards.get(1).title.id);assertEquals("Shared movie",shared.get(1).cards.get(0).title.title);assertEquals(1,lite.get(1).cards.size());assertEquals(shared.get(1).cards.get(0).title.id,lite.get(1).cards.get(0).title.id);
  }
  @Test public void olderDeviceCannotReintroduceWatchedPicks()throws Exception{
    JSONObject v=vector(),history=v.getJSONObject("history"),next=new JSONObject(history.toString()).put("episode:tt2:1:1",new JSONObject().put("positionMs",10000).put("durationMs",120000).put("updatedAt",200));
    List<Catalog.Title> pool=Arrays.asList(title("tt1","movies"),title("tt2","shows"),title("tt3","animes"));JSONObject first=HomeSync.create(pool,next,v.getJSONObject("snapshot"),1000),stale=HomeSync.create(pool,history,first,2000);assertEquals(first.getString("history"),stale.getString("history"));JSONArray rows=stale.getJSONArray("rows");for(int i=0;i<rows.length();i++)assertFalse(rows.getJSONObject(i).getJSONArray("ids").toString().contains("tt2"));
  }
  @Test public void snapshotStripsSourceDataAndBoundsPayload()throws Exception{
    JSONObject raw=new JSONObject().put("imdb","tt2").put("title","Movie").put("poster_big","file:///secret").put("torrents",new JSONArray().put("private"));JSONObject meta=HomeSync.metadata(raw,"movies");assertFalse(meta.has("torrents"));assertFalse(meta.has("poster_big"));JSONObject home=vector().getJSONObject("snapshot");assertNull(HomeSync.clean(new JSONObject(home.toString()).put("padding","x".repeat(100000))));home.getJSONArray("rows").put(new JSONObject().put("name","foreign").put("ids",new JSONArray().put("tt2")));assertEquals(4,HomeSync.clean(home).getJSONArray("rows").length());
  }
}
