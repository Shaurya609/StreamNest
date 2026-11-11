package app.streamnest;
import org.json.*;
import java.util.*;
import org.junit.Test;
import static org.junit.Assert.*;
public class HomeFeedTest {
 Catalog.Title title(String id,String kind,String genre)throws Exception{return Catalog.parse(new JSONObject().put("imdb",id).put("title",id).put("year",2020).put("genres",new JSONArray().put(genre)),kind);}
 JSONObject progress(long time,boolean completed)throws Exception{return new JSONObject().put("positionMs",completed?0:30000).put("durationMs",120000).put("updatedAt",time).put("completed",completed);}
 @Test public void latestCompletedEpisodeHidesEarlierPartialEpisode()throws Exception{
  JSONObject history=new JSONObject().put("episode:tt1:1:1",progress(1,false)).put("episode:tt1:1:2",progress(2,true));
  assertTrue(HomeFeed.build(Arrays.asList(title("tt1","shows","drama")),history,12).get(0).cards.isEmpty());
  history.put("episode:tt1:1:1",progress(3,false));assertEquals("episode:tt1:1:1",HomeFeed.build(Arrays.asList(title("tt1","shows","drama")),history,12).get(0).cards.get(0).historyKey);
 }
 @Test public void diverseRecommendationsExcludeHistoryAndFavorWatchedGenres()throws Exception{
  List<Catalog.Title> pool=Arrays.asList(title("tt1","movies","action"),title("tt2","movies","romance"),title("tt3","movies","action"),title("tt4","shows","drama"),title("tt5","animes","comedy"));
  List<HomeFeed.Row> rows=HomeFeed.build(pool,new JSONObject().put("movie:tt1",progress(1,false)),3);
  assertEquals("Picked for you",rows.get(1).name);assertEquals("tt3",rows.get(1).cards.get(0).title.id);assertEquals(3,rows.get(1).cards.size());assertEquals("shows",rows.get(1).cards.get(1).title.kind);assertEquals("animes",rows.get(1).cards.get(2).title.kind);
 }
 @Test public void independentSeriesRejectsContradictoryEpisode()throws Exception{
  JSONObject row=new JSONObject().put("imdb_id","123").put("season",1).put("episode",1).put("hash","a".repeat(40)).put("title","Show S01E01 720p WEB").put("seeds",2);
  JSONObject doc=new JSONObject().put("imdb_id","123").put("torrents",new JSONArray().put(row));assertEquals(1,EztvEpisodes.parse(doc,"tt123",1,1).size());row.put("title","Show S01E02 720p WEB");assertTrue(EztvEpisodes.parse(doc,"tt123",1,1).isEmpty());
 }
 @Test public void confirmedNoSegmentsDoNotFallBack()throws Exception{
 final int[] calls={0};List<SkipSegments.Segment> rows=SkipSegments.load("tt123",1,1,1,120000,url->{calls[0]++;return new JSONObject().put("imdb_id","tt123").put("season",1).put("episode",1).put("no_intro",true).put("no_outro",true);});assertTrue(rows.isEmpty());assertEquals(1,calls[0]);
 }
 @Test public void fillerMetadataRejectsOtherPageAndNonBooleanFlags()throws Exception{
 JSONArray data=new JSONArray().put(new JSONObject().put("mal_id",1).put("filler",true)).put(new JSONObject().put("mal_id",101).put("filler",true)).put(new JSONObject().put("mal_id",102).put("filler","yes"));
 JSONArray clean=FillerMetadata.clean(data,2);assertEquals(2,clean.length());assertTrue(clean.getJSONObject(0).getBoolean("filler"));assertFalse(clean.getJSONObject(1).has("filler"));
 }
 @Test public void dimensionsAreNotContradictoryEpisodeMarkers()throws Exception{assertEquals(0,EpisodeFiles.select(Arrays.asList("Video.1920x1080.mkv"),Arrays.asList(100L),-1,"",true,1,1,0));}
 @Test public void homeRecordsKeepPlaybackIdentityWithoutRetainingEpisodePayloads()throws Exception{
 JSONObject raw=new JSONObject().put("imdb","tt123").put("title","Show").put("malId",1).put("description","x".repeat(2000)).put("episodes",new JSONArray().put(new JSONObject()));JSONObject lean=HomeLibrary.lean(raw,"animes",true);
 assertFalse(lean.has("episodes"));assertEquals(1,lean.getInt("malId"));assertEquals(400,lean.getString("description").length());
 }
 @Test public void completedEpisodeOffersKnownNextAndFinaleDisappears()throws Exception{
 Catalog.Title t=title("tt1","animes","action");t.raw.put("homeEpisodeOrder",new JSONArray().put("1:1").put("1:2").put("2:1"));
 List<HomeFeed.Card> cards=HomeFeed.build(Arrays.asList(t),new JSONObject().put("episode:tt1:1:2",progress(1,true)),12).get(0).cards;assertEquals("episode:tt1:2:1",cards.get(0).historyKey);assertTrue(cards.get(0).progress.getBoolean("nextEpisode"));assertTrue(HomeFeed.build(Arrays.asList(t),new JSONObject().put("episode:tt1:2:1",progress(2,true)),12).get(0).cards.isEmpty());
 }
}
