package app.streamnest;
import java.io.*;import java.util.*;import java.util.concurrent.*;import org.json.*;
/** Optional episode labels never block title details or source discovery. */
final class FillerMetadata {
 static final Map<String,JSONObject> cache=new LinkedHashMap<>();static final Map<String,Long> retry=new HashMap<>();static final Set<String> pending=new HashSet<>();
 static final ExecutorService worker=Executors.newSingleThreadExecutor(r->{Thread t=new Thread(r,"episode-labels");t.setDaemon(true);return t;});static File loaded;
 static File file(){return CatalogHttp.directory==null?null:new File(CatalogHttp.directory.getParentFile(),"filler-flags-v1.json");}
 static synchronized void initialize(){File f=file();if(f==null||f.equals(loaded))return;loaded=f;cache.clear();try{if(f.isFile()&&f.length()<2000000){JSONObject doc=new JSONObject(new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8));for(Iterator<String> it=doc.keys();it.hasNext();){String key=it.next();JSONObject value=doc.optJSONObject(key);if(key.matches("[1-9][0-9]*:[1-9][0-9]*")&&value!=null&&value.optJSONArray("data")!=null&&value.getJSONArray("data").length()<=100)cache.put(key,value);if(cache.size()>=64)break;}}}catch(Exception ignored){}}
 static synchronized void apply(List<JSONObject> rows){initialize();Map<String,List<JSONObject>> groups=new HashMap<>();for(JSONObject row:rows)if(row.optInt("skipMalId")>0&&row.optInt("episode")>0)groups.computeIfAbsent(row.optInt("skipMalId")+":"+((row.optInt("episode")-1)/100+1),k->new ArrayList<>()).add(row);for(Map.Entry<String,List<JSONObject>> group:groups.entrySet())try{JSONObject value=cache.get(group.getKey());if(value!=null)EpisodeLibrary.attachFiller(group.getValue(),Integer.parseInt(group.getKey().split(":")[0]),value.getJSONArray("data"));}catch(Exception ignored){}}
 static void request(List<JSONObject> rows,Runnable updated){
  Set<Integer> ids=new LinkedHashSet<>();for(JSONObject row:rows)if(row.optInt("skipMalId")>0)ids.add(row.optInt("skipMalId"));
  for(int mal:ids)for(int page:EpisodeLibrary.fillerPages(rows,mal)){
   String key=mal+":"+page;
   synchronized(FillerMetadata.class){initialize();JSONObject old=cache.get(key);if(old!=null&&System.currentTimeMillis()-old.optLong("saved")<86400000||retry.getOrDefault(key,0L)>System.currentTimeMillis()||pending.contains(key)||pending.size()>=8)continue;pending.add(key);}
   worker.execute(()->{try{
    Thread.sleep(550);JSONObject response=new JSONObject(new String(Net.catalogBytes("https://api.jikan.moe/v4/anime/"+mal+"/episodes?page="+page,2000000,System.nanoTime()+4000000000L),java.nio.charset.StandardCharsets.UTF_8));JSONArray clean=clean(response.getJSONArray("data"),page);
    synchronized(FillerMetadata.class){cache.put(key,new JSONObject().put("mal",mal).put("saved",System.currentTimeMillis()).put("data",clean));while(cache.size()>64)cache.remove(cache.keySet().iterator().next());File f=file();if(f!=null){File temp=new File(f.getPath()+".tmp");java.nio.file.Files.write(temp.toPath(),new JSONObject(cache).toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));java.nio.file.Files.move(temp.toPath(),f.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);}}
   }catch(Exception e){synchronized(FillerMetadata.class){retry.put(key,System.currentTimeMillis()+900000);if(retry.size()>64)retry.clear();}}
   finally{synchronized(FillerMetadata.class){pending.remove(key);}updated.run();}});
  }
 }
 static JSONArray clean(JSONArray data,int page)throws Exception{if(data.length()>100)throw new IOException("Too many episode flags");JSONArray out=new JSONArray();for(int i=0;i<data.length();i++){JSONObject row=data.optJSONObject(i);if(row==null)continue;int ep=row.optInt("mal_id");if(ep<=(page-1)*100||ep>page*100)continue;JSONObject flags=new JSONObject().put("mal_id",ep);for(String key:new String[]{"filler","recap"})if(row.opt(key) instanceof Boolean)flags.put(key,row.getBoolean(key));out.put(flags);}return out;}
}
