package app.streamnest;
import static org.junit.Assert.*;
import org.junit.Test;
import org.json.*;
import java.util.*;
public class DiscoveryTest {
 @Test public void titleAndEpisodeMustMatchExactly(){
  assertEquals(2,NyaaEpisodeSources.match("[SubsPlease] Black Clover (01-170) [1080p]","Black Clover",1,1,1));
  assertEquals(2,NyaaEpisodeSources.match("[Erai] Black Clover (TV) - 01 ~ 53 [1080p]","Black Clover",1,1,1));
  for(String name:new String[]{"Black Clover 2nd Season - 01 [1080p]","Black Clover Movie - 01 [1080p]","Mugyutto! Black Clover - 01 [1080p]","Black Clover - 02 [1080p]"})assertEquals(name,0,NyaaEpisodeSources.match(name,"Black Clover",1,1,1));
 }
 @Test public void independentFallbackSurvivesIndexOutage()throws Exception{
  EpisodeDiscovery.Index index=EpisodeDiscovery.index;EpisodeDiscovery.Anime anime=EpisodeDiscovery.anime;
  try{
   EpisodeDiscovery.index=(id,s,e,d)->{throw new java.io.IOException("offline");};
   EpisodeDiscovery.anime=(title,row,d)->Collections.singletonList(new Catalog.Source("1080p · Nyaa","https://nyaa.si/download/1.torrent",10));
   JSONObject row=new JSONObject().put("season",1).put("episode",1).put("absolute",1).put("animeMapped",true);
   assertEquals(1,EpisodeDiscovery.find("tt7441658",row,"Black Clover").size());
   row.put("animeMapped",false);
   try{EpisodeDiscovery.find("tt7441658",row,"Black Clover");fail();}catch(java.io.IOException expected){assertTrue(expected.getMessage().contains("incomplete"));}
  }finally{EpisodeDiscovery.index=index;EpisodeDiscovery.anime=anime;}
 }
 @Test public void malformedIndexIsNotAnEmptyResult()throws Exception{
  try{AlternateEpisodes.parse(new JSONObject());fail();}catch(java.io.IOException expected){}
  assertTrue(AlternateEpisodes.parse(new JSONObject().put("streams",new JSONArray().put("invalid"))).isEmpty());
 }
 @Test public void singleWrongEpisodeIsRejected()throws Exception{
  try{EpisodeFiles.select(Arrays.asList("Show.S01E02.mkv"),Arrays.asList(10L),-1,"",true,1,1,1);fail();}catch(Exception expected){}
 }
}
