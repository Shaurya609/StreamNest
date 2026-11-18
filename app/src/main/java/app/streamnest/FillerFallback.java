package app.streamnest;
import java.io.*;import java.util.*;import java.util.regex.*;import org.json.*;
/** Independently mapped community filler flags; recap is unknown, never inferred. */
final class FillerFallback {
 static final Map<Integer,JSONArray> cache=new HashMap<>();static final Map<Integer,Long> ages=new HashMap<>();
 static String slug(int mal){return mal==34572?"black-clover":mal==21?"one-piece":"";}
 static JSONArray parse(String html,int mal)throws Exception{
  String slug=slug(mal),title=mal==34572?"Black Clover":"One Piece";if(slug.isEmpty()||html.length()>2000000||html.toUpperCase(Locale.ROOT).contains("<!ENTITY"))throw new IOException("Unsupported filler document");
  String canonical="https://www.animefillerlist.com/shows/"+slug;
  if(!Pattern.compile("<link\\b[^>]*rel=[\"\\']canonical[\"\\'][^>]*href=[\"\\']"+Pattern.quote(canonical)+"[\"']",Pattern.CASE_INSENSITIVE).matcher(html).find())throw new IOException("Filler identity mismatch");
  Matcher heading=Pattern.compile("<h1\\b[^>]*>(.*?)</h1>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);
  if(!heading.find()||!heading.group(1).replaceAll("<[^>]*>","").contains(title))throw new IOException("Filler title mismatch");
  Matcher rows=Pattern.compile("<tr\\b[^>]*>(.*?)</tr>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(html);JSONArray out=new JSONArray();Set<Integer> seen=new HashSet<>();
  while(rows.find()){
   Matcher number=Pattern.compile("<td\\b[^>]*class=[\"\\']Number[\"\\'][^>]*>\\s*(\\d+)\\s*</td>",Pattern.CASE_INSENSITIVE).matcher(rows.group(1)),type=Pattern.compile("<td\\b[^>]*class=[\"\\']Type[\"\\'][^>]*>(.*?)</td>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(rows.group(1));
   if(!number.find()||!type.find())continue;int episode=Integer.parseInt(number.group(1));String label=type.group(1).replaceAll("<[^>]*>","").trim();
   if(episode<1||episode>(mal==34572?170:5000)||!Arrays.asList("Filler","Manga Canon","Mixed Canon/Filler","Anime Canon").contains(label))continue;
   if(!seen.add(episode)||out.length()>=5000)throw new IOException("Invalid filler episode table");out.put(new JSONObject().put("mal_id",episode).put("filler",label.equals("Filler")));
  }
  if(out.length()==0)throw new IOException("No filler episode table");return out;
 }
 static JSONArray get(int mal,int page)throws Exception{
  if(slug(mal).isEmpty())throw new IOException("No independently mapped filler provider");JSONArray all;
  synchronized(cache){all=cache.get(mal);if(System.currentTimeMillis()-ages.getOrDefault(mal,0L)>86400000)all=null;}
  if(all==null){all=parse(new String(Net.catalogBytes("https://www.animefillerlist.com/shows/"+slug(mal),2000000,System.nanoTime()+6000000000L),java.nio.charset.StandardCharsets.UTF_8),mal);synchronized(cache){cache.put(mal,all);ages.put(mal,System.currentTimeMillis());}}
  JSONArray out=new JSONArray();for(int i=0;i<all.length();i++){JSONObject r=all.getJSONObject(i);if(r.getInt("mal_id")>(page-1)*100&&r.getInt("mal_id")<=page*100)out.put(r);}return out;
 }
}
