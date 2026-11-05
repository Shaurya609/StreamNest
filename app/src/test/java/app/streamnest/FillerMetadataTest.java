package app.streamnest;

import static org.junit.Assert.*;
import java.util.*;
import org.json.*;
import org.junit.Test;

public class FillerMetadataTest {
  @Test public void pagesBeyondOneHundredAndUnmappedSeasons() throws Exception {
    JSONObject first = new JSONObject().put("skipMalId",21).put("season",1).put("episode",100);
    JSONObject next = new JSONObject().put("skipMalId",21).put("season",1).put("episode",101);
    JSONObject split = new JSONObject().put("season",2).put("episode",1);
    List<JSONObject> rows = Arrays.asList(first,next,split);
    assertEquals(new TreeSet<>(Arrays.asList(1,2)),EpisodeLibrary.fillerPages(rows,21));
    EpisodeLibrary.attachFiller(rows,21,new JSONArray()
        .put(new JSONObject().put("mal_id",101).put("filler",true).put("recap",false))
        .put(new JSONObject().put("mal_id",1).put("filler",true)));
    assertTrue(next.getBoolean("filler"));
    assertFalse(next.getBoolean("recap"));
    assertFalse(first.has("filler"));
    assertFalse(split.has("filler"));
  }
}
