package app.streamnest;
import org.junit.Test;import static org.junit.Assert.*;import org.json.*;import java.util.*;
public class HomeSearchTest {
 Catalog.Title t(String id,String kind,String title)throws Exception{return Catalog.parse(new JSONObject().put("imdb",id).put("title",title),kind);}
 @Test public void oneQueryMatchesEveryCategoryAndDeduplicatesAnime()throws Exception{Catalog.Title anime=t("tt3","animes","Tsuki");anime.raw.put("aliases",new JSONArray().put("Moon anime"));List<Catalog.Title> pool=Arrays.asList(t("tt1","movies","Café Moon"),t("tt2","shows","Moon series"),anime,t("tt3","shows","Moon anime"),t("tt4","movies","Moon sequel"));List<HomeFeed.Row> rows=HomeSearch.rows(pool,"moon",1,1);for(HomeFeed.Row row:rows)assertEquals(1,row.cards.size());assertEquals("animes",rows.get(2).cards.get(0).title.kind);assertTrue(HomeSearch.more(pool,"moon",1,1));assertEquals(1,HomeSearch.rows(pool,"moon",2,1).get(0).cards.size());assertEquals("tt1",HomeSearch.matches(pool,"cafe").get(0).id);assertEquals("tt2",HomeSearch.matches(pool,"tt2").get(0).id);assertTrue(HomeSearch.matches(pool,"absent").isEmpty());}
}
