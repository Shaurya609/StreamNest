package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.json.*;
import org.junit.Test;

public class CanonicalEpisodesTest {
  JSONObject video(int s, int e) throws Exception {
    return new JSONObject()
        .put("id", "v" + s + "_" + e)
        .put("season", s)
        .put("episode", e)
        .put("name", "Episode");
  }

  @Test
  public void absoluteNumbersComeOnlyFromExplicitVerifiedMappings() throws Exception {
    JSONObject meta =
        new JSONObject()
            .put("imdb_id", "tt1")
            .put(
                "videos",
                new JSONArray()
                    .put(video(0, 1))
                    .put(video(1, 1))
                    .put(video(1, 2))
                    .put(video(2, 1)));
    JSONArray rows = CanonicalEpisodes.fromCinemeta(meta, "tt1");
    for (int i = 0; i < rows.length(); i++) assertFalse(rows.getJSONObject(i).has("absolute"));
    JSONObject doc =
        new JSONObject()
            .put("mappings", new JSONObject().put("imdb_id", "tt1"))
            .put(
                "episodes",
                new JSONObject()
                    .put(
                        "1179",
                        new JSONObject()
                            .put("tvdbId", 100)
                            .put("seasonNumber", 23)
                            .put("episodeNumber", 24)
                            .put("absoluteEpisodeNumber", 1179)
                            .put("title", new JSONObject().put("en", "Correct episode"))));
    JSONObject mapped = CanonicalEpisodes.fromMappings(doc, "tt1").getJSONObject(0);
    assertEquals(23, mapped.getInt("season"));
    assertEquals(24, mapped.getInt("number"));
    assertEquals(1179, mapped.getInt("absolute"));
  }

  @Test
  public void incompleteNumberingDoesNotGuessAnAbsoluteMapping() throws Exception {
    JSONObject meta =
        new JSONObject()
            .put("id", "tt1")
            .put("videos", new JSONArray().put(video(1, 1)).put(video(1, 3)));
    JSONArray a = CanonicalEpisodes.fromCinemeta(meta, "tt1");
    assertFalse(a.getJSONObject(1).has("absolute"));
  }

  @Test
  public void liveReleaseMustMatchExactSeriesNameAndAbsoluteEpisode() throws Exception {
    List<JSONObject> episodes =
        Arrays.asList(new JSONObject().put("absolute", 1179).put("season", 23).put("episode", 24));
    Catalog.Title valid = new Catalog.Title();
    valid.title = "[SubsPlease] One Piece - 1179 (1080p)";
    valid.raw =
        new JSONObject()
            .put(
                "torrents",
                new JSONArray()
                    .put(
                        new JSONObject()
                            .put("url", "https://nyaa.si/download/1.torrent")
                            .put("quality", "1080p")));
    Catalog.Title wrong = new Catalog.Title();
    wrong.title = "[Group] One Piece Special - 1179 (1080p)";
    wrong.raw = valid.raw;
    CanonicalEpisodes.attachLive(episodes, Arrays.asList(valid, wrong), "One Piece");
    assertEquals(1, episodes.get(0).getJSONArray("torrents").length());
    assertTrue(episodes.get(0).getBoolean("liveRelease"));
  }

  @Test(expected = Exception.class)
  public void mismatchedMetadataIdentityIsRejected() throws Exception {
    CanonicalEpisodes.fromCinemeta(new JSONObject().put("id", "tt2"), "tt1");
  }
 @Test public void reverseMappingRejectsRelatedOvaAndWrongTitle()throws Exception{
 JSONObject doc=new JSONObject().put("mappings",new JSONObject().put("imdb_id","tt123").put("type","TV")).put("titles",new JSONObject().put("en","My Hero Academia"));
 assertTrue(CanonicalEpisodes.trustedReverseMapping(doc,"tt123","My Hero Academia (TV Series 2016–)"));
 assertFalse(CanonicalEpisodes.trustedReverseMapping(doc,"tt123","Another anime"));doc.getJSONObject("mappings").put("type","OVA");assertFalse(CanonicalEpisodes.trustedReverseMapping(doc,"tt123","My Hero Academia"));
 }
 @Test public void mappedFirstSeasonDoesNotTruncateCanonicalLaterSeasons()throws Exception{
 JSONObject mapped=new JSONObject().put("season",1).put("number",1).put("name","Origin").put("skipMalId",1),next=new JSONObject().put("season",2).put("number",1).put("name","Next");
 JSONArray rows=CanonicalEpisodes.augmentMappings(new JSONArray().put(mapped),new JSONArray().put(new JSONObject().put("season",1).put("number",1).put("name","Origin")).put(next),true);
 assertEquals(2,rows.length());
 }
}
