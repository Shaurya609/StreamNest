package app.streamnest;
import java.util.*;import java.net.URI;import java.nio.charset.StandardCharsets;import org.json.*;
final class Artwork {
 static String url(String value){try{URI u=new URI(value);if("http".equals(u.getScheme())&&Arrays.asList("images.metahub.space","image.tmdb.org","static.tvmaze.com").contains(u.getHost()))u=new URI("https"+value.substring(4));return "https".equals(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null&&u.toString().length()<=1000?u.toString():"";}catch(Exception e){return "";}}
 static List<String> candidates(JSONObject raw){
  Set<String> out=new LinkedHashSet<>();JSONObject images=raw.optJSONObject("images");for(String s:new String[]{raw.optString("poster_big"),raw.optString("large_cover_image"),raw.optString("poster"),images==null?"":images.optString("poster"),images==null?"":images.optString("medium")}){String image=url(s);if(!image.isEmpty()&&out.size()<2)out.add(image);}
  String id=HomeFeed.id(raw);if(id.matches("tt[0-9]+"))out.add("https://images.metahub.space/poster/medium/"+id+"/img");return new ArrayList<>(out);
 }
 static String primary(JSONObject raw){List<String> urls=candidates(raw);return urls.isEmpty()?"":urls.get(0);}
 static String tvmaze(JSONObject doc,String imdb){JSONObject ids=doc.optJSONObject("externals"),image=doc.optJSONObject("image");return ids!=null&&imdb.equals(ids.optString("imdb"))&&image!=null?url(image.optString("medium",image.optString("original"))):"";}
 static final Map<String,String> alternates=new LinkedHashMap<>();static final Map<String,Long> expires=new HashMap<>();
 static String alternate(Catalog.Title title){
  if(title.kind.equals("movies")||!title.id.matches("tt[0-9]+"))return "";synchronized(alternates){if(expires.getOrDefault(title.id,0L)>System.currentTimeMillis())return alternates.getOrDefault(title.id,"");}
  String image="";try{byte[] bytes=Net.catalogBytes("https://api.tvmaze.com/lookup/shows?imdb="+title.id,256000,System.nanoTime()+4000000000L);image=tvmaze(new JSONObject(new String(bytes,StandardCharsets.UTF_8)),title.id);}catch(Exception ignored){}
  synchronized(alternates){alternates.put(title.id,image);expires.put(title.id,System.currentTimeMillis()+(image.isEmpty()?60000:86400000));while(alternates.size()>128){String id=alternates.keySet().iterator().next();alternates.remove(id);expires.remove(id);}}return image;
 }
}
