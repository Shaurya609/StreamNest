package app.streamnest;
import android.content.*;
import java.util.*;
import java.io.*;
import org.json.*;

final class HomeLibrary {
 static volatile List<Catalog.Title> cached;static volatile long cachedAt,revision;
 static void clear(){cached=null;}

 static void remember(Context c,Catalog.Title title){clear();try{
   android.content.SharedPreferences p=c.getSharedPreferences("home-library",0);JSONObject raw=new JSONObject(title.raw.toString());try{JSONObject old=new JSONObject(p.getString(title.id,"{}")).optJSONObject("title");if(old!=null&&!raw.has("homeEpisodeOrder")&&old.has("homeEpisodeOrder"))raw.put("homeEpisodeOrder",old.get("homeEpisodeOrder")).put("homeEpisodeOrderAt",old.optLong("homeEpisodeOrderAt"));}catch(Exception ignored){}JSONObject doc=new JSONObject().put("kind",title.kind).put("title",raw).put("seen",System.currentTimeMillis());
   android.content.SharedPreferences.Editor edit=p.edit().putString(title.id,doc.toString());
   if(p.getAll().size()>200){List<String> keys=new ArrayList<>(p.getAll().keySet());keys.sort(Comparator.comparingLong(k->{try{return new JSONObject(p.getString(k,"{}")).optLong("seen");}catch(Exception e){return 0L;}}));for(String key:keys.subList(0,keys.size()-200))if(!key.equals(title.id))edit.remove(key);}
   edit.apply();revision++;
 }catch(Exception ignored){}}
 static JSONObject lean(JSONObject row,String kind,boolean lite)throws Exception{
  JSONObject out=new JSONObject();for(String key:new String[]{"imdb","imdb_id","imdb_code","id","title","name","year","language","genres","rating","trendRank","latestRank","date_uploaded_unix","poster_big","poster","poster_med","large_cover_image","images","animeSeries","malId","verifiedQualities","verifiedHdEpisodes","metadataOnly","regionalCatalog","homeEpisodeOrder","homeEpisodeOrderAt"})if(row.has(key))out.put(key,row.get(key));
  String description=row.optString("description",row.optString("description_full",row.optString("synopsis")));out.put("description",description.substring(0,Math.min(description.length(),lite?400:1200)));
  if(kind.equals("movies"))for(String key:new String[]{"torrents","items","items_lang"})if(row.has(key))out.put(key,row.get(key));return out;
 }
 static List<Catalog.Title> read(InputStream in,String kind,boolean lite)throws Exception{
  List<Catalog.Title> rows=new ArrayList<>();String field=kind.equals("movies")?"movies":"series";boolean found=false;
  try(android.util.JsonReader reader=CatalogJson.reader(in)){reader.beginObject();while(reader.hasNext()){String key=reader.nextName();if(!key.equals(field)){reader.skipValue();continue;}found=true;reader.beginArray();while(reader.hasNext()){if(Thread.currentThread().isInterrupted())throw new InterruptedException();JSONObject row=(JSONObject)CatalogJson.value(reader,1);if(rows.size()>=30000)throw new IOException("Home catalog limit");rows.add(Catalog.parse(lean(row,kind,lite),kind));}reader.endArray();}reader.endObject();}if(!found||rows.isEmpty())throw new IOException("Home catalog unavailable");return rows;
 }
 static List<Catalog.Title> load(Context c)throws Exception{
  List<Catalog.Title> savedPool=cached;if(savedPool!=null&&System.currentTimeMillis()-cachedAt<120000)return new ArrayList<>(savedPool);
  List<Catalog.Title> out=new ArrayList<>();File dir=CatalogStorage.folder(c);
  for(String kind:new String[]{"movies","shows","animes"})try{
   String name="catalog-"+kind+"-v1.json";boolean lite=DeviceProfile.lite(c);
   File saved=new File(dir,kind.equals("shows")?"series-index-v6.json":kind.equals("animes")?"anime-series-v8.json":"unused-movies.json");
   long bundled;try(InputStream in=c.getAssets().open(name)){bundled=CatalogSync.version(in);}
   List<Catalog.Title> rows=null;
   if(saved.isFile()&&saved.length()<16000000&&CatalogSync.version(saved)>=bundled)try(InputStream in=new FileInputStream(saved)){rows=read(in,kind,lite);}catch(Exception ignored){}
   if(rows==null)rows=read(c.getAssets().open(name),kind,lite);out.addAll(rows);
  }catch(Exception ignored){}
  try(android.database.Cursor cursor=MovieRepository.shared(c).getReadableDatabase().rawQuery("SELECT payload FROM movies ORDER BY popularity ASC,seeds DESC,uploaded DESC LIMIT 500",null)){while(cursor.moveToNext())out.add(Catalog.parse(lean(new JSONObject(cursor.getString(0)),"movies",DeviceProfile.lite(c)),"movies"));}catch(Exception ignored){}
  for(Object value:c.getSharedPreferences("home-library",0).getAll().values())try{JSONObject doc=new JSONObject((String)value);out.add(Catalog.parse(doc.getJSONObject("title"),doc.getString("kind")));}catch(Exception ignored){}
  cached=new ArrayList<>(out);cachedAt=System.currentTimeMillis();return out;
 }
 static JSONArray episodeOrder(List<JSONObject> episodes){List<JSONObject> ordered=new ArrayList<>(episodes);ordered.sort(Comparator.comparingInt((JSONObject r)->r.optInt("season")).thenComparingInt(r->r.optInt("episode")));JSONArray order=new JSONArray();for(JSONObject row:ordered)if(row.optInt("episode")>0&&ResumeEpisodeSelection.aired(row,System.currentTimeMillis()))order.put(row.optInt("season")+":"+row.optInt("episode"));return order;}
 static void rememberEpisodes(Context c,Catalog.Title title,List<JSONObject> episodes){try{JSONObject raw=new JSONObject(title.raw.toString()).put("homeEpisodeOrder",episodeOrder(episodes)).put("homeEpisodeOrderAt",System.currentTimeMillis());remember(c,Catalog.parse(raw,title.kind));}catch(Exception ignored){}}
 static List<Catalog.Title> enrich(Context c,List<Catalog.Title> pool,JSONObject history)throws Exception{
  Map<String,Catalog.Title> known=new HashMap<>();for(Catalog.Title t:pool){Catalog.Title old=known.get(t.id);if(old==null||!old.kind.equals("animes")||t.kind.equals("animes"))known.put(t.id,t);}
  List<String> keys=new ArrayList<>();for(Iterator<String> it=history.keys();it.hasNext();)keys.add(it.next());keys.sort((a,b)->Long.compare(history.optJSONObject(b).optLong("updatedAt"),history.optJSONObject(a).optLong("updatedAt")));
  Set<String> seen=new HashSet<>();int attempts=0;
  for(String key:keys){String[] parts=key.split(":");if(parts.length<2||!parts[1].matches("tt[0-9]+")||!seen.add(parts[1]))continue;Catalog.Title existing=known.get(parts[1]);boolean next=parts[0].equals("episode")&&history.getJSONObject(key).optBoolean("completed");
   if(existing!=null&&(!next||System.currentTimeMillis()-existing.raw.optLong("homeEpisodeOrderAt")<7200000))continue;if(attempts++>=4)break;
   if(Thread.currentThread().isInterrupted())throw new InterruptedException();
   try{String type=parts[0].equals("movie")?"movie":"series";JSONObject meta=CatalogHttp.object("https://v3-cinemeta.strem.io/meta/"+type+"/"+parts[1]+".json",null).getJSONObject("meta");if(!parts[1].equals(meta.optString("imdb_id",meta.optString("id"))))continue;
    JSONObject raw=existing==null?new JSONObject(meta.toString()).put("imdb",parts[1]).put("title",meta.optString("name")).put("poster_big",meta.optString("poster")).put("description",meta.optString("description")):new JSONObject(existing.raw.toString());String kind=existing==null?(type.equals("movie")?"movies":"shows"):existing.kind;raw=lean(raw,kind,DeviceProfile.lite(c));
    if(type.equals("series")){Catalog.Title candidate=Catalog.parse(raw,kind);List<JSONObject> canonical=EpisodeLibrary.load("https://api.apiabcd.com/",candidate);raw.put("homeEpisodeOrder",episodeOrder(canonical)).put("homeEpisodeOrderAt",System.currentTimeMillis());}
    Catalog.Title title=Catalog.parse(raw,kind);if(existing!=null)pool.remove(existing);pool.add(title);known.put(title.id,title);remember(c,title);
   }catch(Exception ignored){}
  }return pool;
 }
}
