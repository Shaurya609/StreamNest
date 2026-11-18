package app.streamnest;
import static org.junit.Assert.*;
import org.json.*;
import org.junit.Test;
public class FillerFallbackTest {
 String fixture(){return "<link rel=\"canonical\" href=\"https://www.animefillerlist.com/shows/black-clover\"/><h1>Black Clover Filler List</h1>"+row(123,"Filler")+row(124,"Mixed Canon/Filler")+row(125,"Anime Canon");}
 String row(int ep,String type){return "<tr><td class=\"Number\">"+ep+"</td><td class=\"Type\"><span>"+type+"</span></td></tr>";}
 @Test public void laterEpisodesPreserveUnknownRecap()throws Exception{JSONArray rows=FillerFallback.parse(fixture(),34572);assertEquals(3,rows.length());assertTrue(rows.getJSONObject(0).getBoolean("filler"));assertFalse(rows.getJSONObject(1).getBoolean("filler"));assertFalse(rows.getJSONObject(2).getBoolean("filler"));assertFalse(rows.getJSONObject(0).has("recap"));}
 @Test public void rejectsMismatchedOrAmbiguousDocuments()throws Exception{for(String html:new String[]{fixture().replace("/black-clover","/one-piece"),fixture().replace("Black Clover","Other show"),fixture()+row(123,"Filler"),"<!ENTITY x>"+fixture()}){try{FillerFallback.parse(html,34572);fail("accepted invalid document");}catch(java.io.IOException expected){}}}
}
