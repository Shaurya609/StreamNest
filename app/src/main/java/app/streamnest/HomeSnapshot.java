package app.streamnest;

import java.util.*;
import org.json.*;

/** Small local title cache only. Playback progress is always read afresh, never restored here. */
final class HomeSnapshot {
 static final int MAX_TITLES=160,MAX_BYTES=750000;
 static JSONObject encode(List<HomeFeed.Row> rows)throws Exception{
  JSONArray titles=new JSONArray();Set<String> seen=new HashSet<>();
  for(HomeFeed.Row row:rows)for(HomeFeed.Card card:row.cards){
   if(titles.length()>=MAX_TITLES)break;
   Catalog.Title t=card.title;if(!seen.add(t.id))continue;
   JSONObject raw=HomeLibrary.lean(t.raw,t.kind,true);
   if(raw.toString().length()>16000)continue;
   titles.put(new JSONObject().put("kind",t.kind).put("title",raw));
  }
  JSONObject doc=new JSONObject().put("schema",1).put("titles",titles);
  return doc.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8).length<=MAX_BYTES?doc:null;
 }
 static List<Catalog.Title> decode(String value)throws Exception{
  List<Catalog.Title> out=new ArrayList<>();
  if(value==null||value.length()>MAX_BYTES)return out;
  JSONObject doc=new JSONObject(value);if(doc.optInt("schema")!=1)return out;
  JSONArray titles=doc.optJSONArray("titles");if(titles==null||titles.length()>MAX_TITLES)return out;
  for(int i=0;i<titles.length();i++)try{
   JSONObject row=titles.getJSONObject(i);String kind=row.getString("kind");
   if(!Arrays.asList("movies","shows","animes").contains(kind))continue;
   Catalog.Title t=Catalog.parse(row.getJSONObject("title"),kind);
   if(t.id.matches("tt[0-9]+")&&!t.title.isBlank())out.add(t);
  }catch(Exception ignored){}
  return out;
 }
}
