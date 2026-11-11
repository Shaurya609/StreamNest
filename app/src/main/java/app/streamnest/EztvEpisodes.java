package app.streamnest;
import org.json.*;
import java.util.*;
import java.util.regex.*;
final class EztvEpisodes {
 static final Map<String,Scan> scans=new LinkedHashMap<>();
 static final class Scan {long time=System.currentTimeMillis();int next=2,lastPage=1;Map<Integer,JSONObject> pages=new java.util.concurrent.ConcurrentHashMap<>();}
 static List<Catalog.Source> parse(JSONObject doc,String imdb,int season,int episode)throws Exception{
  String wanted=imdb.substring(2).replaceFirst("^0+","");
  if(!doc.optString("imdb_id").replaceFirst("^0+","").equals(wanted)||doc.optJSONArray("torrents")==null)throw new java.io.IOException("Independent series identity mismatch");
  List<Catalog.Source> result=new ArrayList<>();Set<String> seen=new HashSet<>();JSONArray rows=doc.getJSONArray("torrents");
  for(int n=0;n<rows.length();n++){JSONObject row=rows.optJSONObject(n);if(row==null||!row.optString("imdb_id").replaceFirst("^0+","").equals(wanted)||row.optInt("season",-1)!=season||row.optInt("episode",-1)!=episode)continue;
   String title=row.optString("title",row.optString("filename")),hash=row.optString("hash").toLowerCase(Locale.ROOT);Matcher m=Pattern.compile("(?i)(?:^|[^a-z0-9])s([0-9]{1,2})e([0-9]{1,4})(?![0-9])").matcher(title);
   String quality=PlaybackPlan.episodeQuality(new Catalog.Source(title,"",0));
   if(!m.find()||Integer.parseInt(m.group(1))!=season||Integer.parseInt(m.group(2))!=episode||Pattern.compile("(?i)s[0-9]{1,2}e[0-9]{1,4}(?:e[0-9]|[-+]e?[0-9])").matcher(title).find()||quality.isEmpty()||!hash.matches("[a-f0-9]{40}")||row.optInt("seeds")<=0||!seen.add(hash))continue;
   String url="magnet:?xt=urn:btih:"+hash;
   try{java.net.URI old=new java.net.URI(row.optString("magnet_url"));String q=old.getRawSchemeSpecificPart();if("magnet".equals(old.getScheme())){int count=0;for(String part:q.replaceFirst("^\\?","").split("&"))if(part.startsWith("tr=")&&count++<12){String tracker=java.net.URLDecoder.decode(part.substring(3),"UTF-8");if(tracker.matches("(?:https?|udp)://[^\\s]+"))url+="&tr="+Net.q(tracker);}}}catch(Exception ignored){}
   Catalog.Source source=new Catalog.Source(quality+" · EZTV"+(title.toLowerCase(Locale.ROOT).matches(".*(hevc|265|av1).*" )?" · HEVC/AV1":""),url,row.optInt("seeds"));result.add(source);
  }return result;
 }
 static void retry(){synchronized(scans){for(Scan scan:scans.values()){scan.pages.remove(1);scan.pages.remove(scan.lastPage);}}}
 static void invalidate(){synchronized(scans){scans.clear();}}
 static List<Catalog.Source> find(String imdb,int season,int episode,long deadline)throws Exception{
  Scan scan; synchronized(scans){scan=scans.get(imdb);if(scan==null||System.currentTimeMillis()-scan.time>300000){scan=new Scan();scans.put(imdb,scan);while(scans.size()>8)scans.remove(scans.keySet().iterator().next());}}
  JSONObject first=page(scan,imdb,1,deadline);int total=Math.max(1,Math.min(100,(first.optInt("torrents_count")+99)/100));scan.lastPage=total;List<Integer> pages=new ArrayList<>();pages.add(1);if(total>1)pages.add(total);
  if(total>2){synchronized(scan){if(scan.next>=total)scan.next=2;pages.add(scan.next++);}}
  for(int p:pages)page(scan,imdb,p,deadline);
  LinkedHashMap<String,Catalog.Source> found=new LinkedHashMap<>();for(JSONObject doc:scan.pages.values())for(Catalog.Source source:parse(doc,imdb,season,episode))found.put(source.url,source);
  if(found.isEmpty()&&scan.pages.size()<total)throw new java.io.IOException("Independent source scan incomplete; retry to search more pages");return new ArrayList<>(found.values());
 }
 static JSONObject page(Scan scan,String imdb,int page,long deadline)throws Exception{
  JSONObject cached=scan.pages.get(page);if(cached!=null)return cached;{JSONObject doc=new JSONObject(new String(Net.catalogBytes("https://eztvx.to/api/get-torrents?imdb_id="+imdb.substring(2)+"&limit=100&page="+page,2000000,deadline),java.nio.charset.StandardCharsets.UTF_8));parse(doc,imdb,0,1);if(doc.optInt("page",-1)!=page||doc.optInt("torrents_count",-1)<0||doc.getJSONArray("torrents").length()>100)throw new java.io.IOException("Invalid independent series page");scan.pages.put(page,doc);while(scan.pages.size()>6){Integer victim=null;for(Integer k:scan.pages.keySet())if(k!=1&&k!=page){victim=k;break;}if(victim==null)break;scan.pages.remove(victim);}return doc;}
 }
}
