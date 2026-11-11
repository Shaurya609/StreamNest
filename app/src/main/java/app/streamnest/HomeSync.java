package app.streamnest;

import android.content.Context;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Group-owned recommendations, separate from the backwards-compatible progress room. */
final class HomeSync {
  static String lastScope="",lastSignature="";static long lastCheck;
  static synchronized boolean shouldPoll(String scope,String signature,long now){if(scope.equals(lastScope)&&signature.equals(lastSignature)&&now-lastCheck<60000)return false;lastScope=scope;lastSignature=signature;lastCheck=now;return true;}
  static final Set<String> NAMES=new HashSet<>(Arrays.asList("Picked for you","Discover something new","Recently added","Movies","Series","Anime","Hindi picks","Korean picks"));
  static Map<String,Long> latest(JSONObject history)throws Exception{
    Map<String,Long> result=new HashMap<>();JSONObject clean=WatchSyncCrypto.clean(history);
    for(Iterator<String> it=clean.keys();it.hasNext();){String key=it.next(),id=key.split(":")[1];result.put(id,Math.max(result.getOrDefault(id,0L),clean.getJSONObject(key).getLong("updatedAt")));}return result;
  }
  static JSONObject union(JSONObject history,JSONObject previous)throws Exception{
    Map<String,Long> times=latest(history);if(previous!=null)for(Iterator<String> it=previous.keys();it.hasNext();){String id=it.next();times.put(id,Math.max(times.getOrDefault(id,0L),previous.optLong(id)));}
    JSONObject result=new JSONObject();List<String> ids=ordered(times);for(String id:ids.subList(0,Math.min(500,ids.size())))result.put(id,times.get(id));return result;
  }
  static JSONObject asHistory(JSONObject times)throws Exception{JSONObject out=new JSONObject();for(Iterator<String> it=times.keys();it.hasNext();){String id=it.next();out.put("movie:"+id,new JSONObject().put("positionMs",10000).put("durationMs",120000).put("updatedAt",times.getLong(id)));}return out;}
  static List<String> ordered(Map<String,Long> latest){List<String> ids=new ArrayList<>(latest.keySet());ids.sort((a,b)->{int t=Long.compare(latest.get(b),latest.get(a));return t!=0?t:a.compareTo(b);});return ids;}
  static String signature(JSONObject history)throws Exception{return WatchSyncCrypto.hex(MessageDigest.getInstance("SHA-256").digest(new JSONArray(ordered(latest(history))).toString().getBytes(StandardCharsets.UTF_8)));}
  static JSONObject metadata(JSONObject raw,String kind)throws Exception{
    String id=HomeFeed.id(raw),title=raw.optString("title");if(!id.matches("tt[0-9]+")||!Arrays.asList("movies","shows","animes").contains(kind)||title.isBlank())return null;
    Catalog.Title t=Catalog.parse(raw,kind);List<String> genres=new ArrayList<>(HomeFeed.genres(t));genres.removeIf(g->g.length()>40);Collections.sort(genres);
    JSONObject out=new JSONObject().put("imdb",id).put("kind",kind).put("title",title.substring(0,Math.min(150,title.length()))).put("genres",new JSONArray(genres.subList(0,Math.min(8,genres.size()))));
    for(String key:new String[]{"year","language"})if(raw.has(key)){String s=raw.optString(key);out.put(key,s.substring(0,Math.min(12,s.length())));}
    String poster=Artwork.primary(raw);if(!poster.isEmpty()&&poster.length()<=600)out.put("poster_big",poster);if(WatchSyncCrypto.integer(raw,"malId")&&raw.optInt("malId")>0)out.put("malId",raw.getInt("malId"));return out;
  }
  static JSONObject clean(JSONObject value)throws Exception{
    if(value==null||value.optInt("schema")!=1||!value.optString("history").matches("[a-f0-9]{64}")||!WatchSyncCrypto.integer(value,"createdAt")||value.optLong("createdAt")<1||value.optLong("createdAt")>System.currentTimeMillis()+86400000||value.optJSONArray("rows")==null||value.optJSONObject("titles")==null||value.toString().getBytes(StandardCharsets.UTF_8).length>90000)return null;
    JSONObject titles=new JSONObject(),input=value.getJSONObject("titles");int count=0;
    for(Iterator<String> it=input.keys();it.hasNext()&&count++<150;){String id=it.next();JSONObject raw=input.optJSONObject(id);if(raw==null)continue;JSONObject t=metadata(raw,raw.optString("kind"));if(t!=null&&id.equals(t.optString("imdb")))titles.put(id,t);}
    JSONObject times=new JSONObject(),inputTimes=value.optJSONObject("watched");int wc=0;if(inputTimes!=null)for(Iterator<String> it=inputTimes.keys();it.hasNext()&&wc++<500;){String id=it.next();if(id.matches("tt[0-9]+")&&WatchSyncCrypto.integer(inputTimes,id)&&inputTimes.optLong(id)>0&&inputTimes.optLong(id)<=System.currentTimeMillis()+86400000)times.put(id,inputTimes.getLong(id));}
    JSONArray rows=new JSONArray(),source=value.getJSONArray("rows");Set<String> seen=new HashSet<>();
    for(int i=0;i<Math.min(8,source.length());i++){JSONObject row=source.optJSONObject(i);if(row==null||!NAMES.contains(row.optString("name"))||row.optJSONArray("ids")==null||!seen.add(row.optString("name")))continue;
      JSONArray ids=new JSONArray();Set<String> unique=new HashSet<>();JSONArray a=row.getJSONArray("ids");for(int n=0;n<a.length()&&ids.length()<12;n++){Object item=a.opt(n);if(item instanceof String&&titles.has((String)item)&&unique.add((String)item))ids.put(item);}rows.put(new JSONObject().put("name",row.getString("name")).put("ids",ids));}
    return rows.length()==0?null:new JSONObject().put("schema",1).put("history",value.getString("history")).put("createdAt",value.getLong("createdAt")).put("rows",rows).put("titles",titles).put("watched",times).put("manual",value.optString("manual").matches("[a-f0-9]{64}")?value.getString("manual"):"");
  }
  static List<Catalog.Title> combine(List<Catalog.Title> pool,JSONObject home)throws Exception{
    Map<String,Catalog.Title> known=new LinkedHashMap<>();for(Catalog.Title t:pool){Catalog.Title old=known.get(t.id);if(old==null||!old.kind.equals("animes")||t.kind.equals("animes"))known.put(t.id,t);}
    if(home!=null){JSONObject titles=home.getJSONObject("titles");for(Iterator<String> it=titles.keys();it.hasNext();){String id=it.next();JSONObject meta=titles.getJSONObject(id),raw=known.containsKey(id)?new JSONObject(known.get(id).raw.toString()):new JSONObject();for(Iterator<String> keys=meta.keys();keys.hasNext();){String k=keys.next();raw.put(k,meta.get(k));}known.put(id,Catalog.parse(raw,meta.getString("kind")));}}
    return new ArrayList<>(known.values());
  }
  static JSONObject create(List<Catalog.Title> pool,JSONObject history,JSONObject previous,long now)throws Exception{return create(pool,history,previous,now,new JSONObject());}
  static JSONObject create(List<Catalog.Title> pool,JSONObject history,JSONObject previous,long now,JSONObject manual)throws Exception{
    history=asHistory(union(history,previous==null?null:previous.optJSONObject("watched")));
    pool=combine(pool,previous);JSONObject titles=new JSONObject();JSONArray rows=new JSONArray();List<HomeFeed.Row> built=HomeFeed.build(pool,history,12,manual);
    for(HomeFeed.Row row:built.subList(1,built.size())){JSONArray ids=new JSONArray();for(HomeFeed.Card card:row.cards){JSONObject t=metadata(card.title.raw,card.title.kind);if(t!=null){titles.put(card.title.id,t);ids.put(card.title.id);}}rows.put(new JSONObject().put("name",row.name).put("ids",ids));}
    Map<String,Catalog.Title> known=new HashMap<>();for(Catalog.Title t:pool)known.put(t.id,t);List<String> recent=ordered(latest(history));for(String id:recent.subList(0,Math.min(50,recent.size()))){Catalog.Title t=known.get(id);if(t!=null){JSONObject m=metadata(t.raw,t.kind);if(m!=null)titles.put(id,m);}}
    return clean(new JSONObject().put("schema",1).put("history",signature(history)).put("createdAt",now).put("rows",rows).put("titles",titles).put("watched",union(history,null)).put("manual",ManualWatched.signature(manual)));
  }
  static JSONObject cached(Context c)throws Exception{
    try{String code=WatchSync.code(c);if(code.isEmpty())return null;JSONObject doc=new JSONObject(c.getSharedPreferences("group-home",0).getString("snapshot","{}"));return doc.optString("scope").equals(WatchSyncCrypto.auth(WatchSyncCrypto.homeCode(code)))?clean(doc.optJSONObject("home")):null;}catch(Exception ignored){return null;}
  }
  static void save(Context c,String code,JSONObject home)throws Exception{
    String value=new JSONObject().put("scope",WatchSyncCrypto.auth(WatchSyncCrypto.homeCode(code))).put("home",home).toString();android.content.SharedPreferences p=c.getSharedPreferences("group-home",0);if(!value.equals(p.getString("snapshot",""))){p.edit().putString("snapshot",value).apply();HomeLibrary.revision++;}
  }
  static List<HomeFeed.Row> apply(List<HomeFeed.Row> local,List<Catalog.Title> pool,JSONObject history,JSONObject home,int limit)throws Exception{return apply(local,pool,history,home,limit,new JSONObject());}
  static List<HomeFeed.Row> apply(List<HomeFeed.Row> local,List<Catalog.Title> pool,JSONObject history,JSONObject home,int limit,JSONObject manual)throws Exception{
    home=clean(home);if(home==null)return local;Map<String,Catalog.Title> known=new HashMap<>();for(Catalog.Title t:combine(pool,home))known.put(t.id,t);Set<String> watched=latest(history).keySet();List<HomeFeed.Row> rows=new ArrayList<>();rows.add(local.get(0));JSONArray sections=home.getJSONArray("rows");for(int i=0;i<sections.length();i++){JSONObject row=sections.getJSONObject(i);List<HomeFeed.Card> cards=new ArrayList<>();JSONArray ids=row.getJSONArray("ids");for(int n=0;n<ids.length()&&cards.size()<limit;n++){String id=ids.getString(n);if(!watched.contains(id)&&!(manual.optJSONObject(id)!=null&&manual.getJSONObject(id).optBoolean("watched")))cards.add(new HomeFeed.Card(known.get(id),null,""));}rows.add(new HomeFeed.Row(row.getString("name"),cards));}return rows;
  }
  static List<HomeFeed.Row> build(Context c,List<Catalog.Title> pool,JSONObject history,int limit)throws Exception{JSONObject manual=ManualWatched.records(c);return apply(HomeFeed.build(pool,history,limit,manual),pool,history,cached(c),limit,manual);}
  static void sync(Context c,String pairing)throws Exception{
    String code=WatchSyncCrypto.homeCode(pairing),auth=WatchSyncCrypto.auth(code),endpoint=WatchSync.endpoint(c);
    JSONObject manual=ManualWatched.records(c);String manualSignature=ManualWatched.signature(manual);JSONObject existing=cached(c);if(!shouldPoll(auth,signature(asHistory(union(WatchProgress.records(c),existing==null?null:existing.optJSONObject("watched"))))+manualSignature,System.currentTimeMillis()))return;
    for(int n=0;n<3;n++){
      if(!pairing.equals(WatchSync.code(c)))return;JSONObject remote=WatchSync.call(endpoint,"GET",auth,null,0),home=remote.isNull("payload")?null:clean(WatchSyncCrypto.openDocument(code,remote.getJSONObject("payload")));if(!pairing.equals(WatchSync.code(c)))return;
      JSONObject history=WatchProgress.records(c);
      if(home!=null&&home.optString("manual").equals(manualSignature)&&home.getString("history").equals(signature(asHistory(union(history,home.optJSONObject("watched")))))&&System.currentTimeMillis()-home.getLong("createdAt")<86400000){save(c,pairing,home);return;}
      JSONObject next=create(HomeLibrary.load(c),history,home,System.currentTimeMillis(),manual);if(next==null)return;JSONObject response=WatchSync.call(endpoint,"PUT",auth,WatchSyncCrypto.seal(code,next),remote.getLong("revision"));if(!pairing.equals(WatchSync.code(c)))return;if(!response.optBoolean("conflict")){save(c,pairing,next);return;}
    }
    throw new java.io.IOException("Home changed; retry shortly");
  }
}
