package app.streamnest;

import java.util.*;
import org.json.*;

/** Offline-first, bounded home rows; history stays on the device / existing encrypted sync. */
final class HomeFeed {
  static final class Card {
    final Catalog.Title title; final JSONObject progress; final String historyKey;
    Card(Catalog.Title t, JSONObject p, String key){title=t;progress=p;historyKey=key;}
  }
  static final class Row {
    final String name; final List<Card> cards;
    Row(String n,List<Card> c){name=n;cards=c;}
  }
  static String id(JSONObject o){return o.optString("imdb",o.optString("imdb_id",o.optString("imdb_code",o.optString("id"))));}
  static Set<String> genres(Catalog.Title t){
    Set<String> result=new HashSet<>();JSONArray a=t.raw.optJSONArray("genres");if(a==null)a=t.raw.optJSONArray("genre");
    if(a!=null)for(int i=0;i<a.length();i++)result.add(a.optString(i).toLowerCase(Locale.ROOT));return result;
  }
  static List<Row> build(List<Catalog.Title> pool,JSONObject history,int limit)throws Exception{return build(pool,history,limit,new JSONObject());}
  static List<Row> build(List<Catalog.Title> pool,JSONObject history,int limit,JSONObject manual)throws Exception{
    Map<String,Catalog.Title> titles=new LinkedHashMap<>();for(Catalog.Title t:pool)if(t.id.matches("tt[0-9]+")&&!t.title.isBlank()){
      Catalog.Title old=titles.get(t.id);if(old==null||!old.kind.equals("animes")||t.kind.equals("animes"))titles.put(t.id,t);
    }
    Map<String,JSONObject> latest=new HashMap<>();Map<String,String> keys=new HashMap<>();
    for(Iterator<String> it=history.keys();it.hasNext();){String key=it.next();String[] parts=key.split(":");if(parts.length<2||!parts[1].matches("tt[0-9]+")||!(parts[0].equals("movie")||parts[0].equals("episode")))continue;
      JSONObject row=history.optJSONObject(key);if(row!=null&&(latest.get(parts[1])==null||row.optLong("updatedAt")>latest.get(parts[1]).optLong("updatedAt"))){latest.put(parts[1],row);keys.put(parts[1],key);}
    }
    for(Iterator<String> it=manual.keys();it.hasNext();){String id=it.next();JSONObject r=manual.getJSONObject(id);if(!r.optBoolean("watched"))continue;JSONObject t=r.optJSONObject("title");if(t!=null&&!titles.containsKey(id))titles.put(id,Catalog.parse(t,t.getString("kind")));latest.put(id,new JSONObject().put("updatedAt",r.getLong("updatedAt")).put("completed",true).put("manualWatched",true));keys.put(id,"manual:"+id);}
    List<String> recent=new ArrayList<>(latest.keySet());recent.sort((a,b)->Long.compare(latest.get(b).optLong("updatedAt"),latest.get(a).optLong("updatedAt")));
    List<Card> resume=new ArrayList<>();Map<String,Integer> affinity=new HashMap<>();
    for(String id:recent){Catalog.Title t=titles.get(id);if(t==null)continue;JSONObject progress=latest.get(id);
      if(!progress.optBoolean("completed")&&WatchProgress.resumable(progress.optLong("positionMs"),progress.optLong("durationMs"))>0&&resume.size()<limit)resume.add(new Card(t,progress,keys.get(id)));
      if(progress.optBoolean("completed")&&!progress.optBoolean("manualWatched")&&!t.kind.equals("movies")&&resume.size()<limit){String key=keys.get(id);String[] parts=key.split(":");JSONArray order=t.raw.optJSONArray("homeEpisodeOrder");if(parts.length==4&&order!=null)for(int n=0;n+1<order.length();n++)if(order.optString(n).equals(parts[2]+":"+parts[3])){String next=order.optString(n+1);if(next.startsWith("0:")==parts[2].equals("0"))resume.add(new Card(t,new JSONObject().put("nextEpisode",true),"episode:"+id+":"+next));break;}}
      if(recent.indexOf(id)<50)for(String g:genres(t))affinity.put(g,affinity.getOrDefault(g,0)+1);
    }
    List<Catalog.Title> candidates=new ArrayList<>(titles.values());
    candidates.removeIf(t->latest.containsKey(t.id)||t.raw.optInt("year")>java.time.Year.now().getValue());
    Map<String,Integer> scores=new HashMap<>();for(Catalog.Title t:candidates){int score=0;for(String g:genres(t))score+=affinity.getOrDefault(g,0);scores.put(t.id,score);}
    candidates.sort(Comparator.<Catalog.Title>comparingInt(t->scores.get(t.id)).reversed().thenComparingInt(t->t.raw.optInt("trendRank",100000)).thenComparing(Comparator.comparingDouble((Catalog.Title t)->t.raw.optDouble("rating",0)).reversed()).thenComparing(t->t.id));
    List<Card> mixed=new ArrayList<>();Map<String,List<Card>> kinds=new LinkedHashMap<>();for(String k:new String[]{"movies","shows","animes"})kinds.put(k,new ArrayList<>());
    for(Catalog.Title t:candidates){List<Card> list=kinds.get(t.kind);if(list!=null&&list.size()<limit)list.add(new Card(t,null,""));}
    for(int n=0;n<limit&&mixed.size()<limit;n++)for(List<Card> list:kinds.values())if(n<list.size()&&mixed.size()<limit)mixed.add(list.get(n));
    List<Row> rows=new ArrayList<>();rows.add(new Row("Continue Watching",resume));rows.add(new Row(affinity.isEmpty()?"Discover something new":"Picked for you",mixed));
    List<Card> added=new ArrayList<>();Map<String,List<Catalog.Title>> recentKinds=new LinkedHashMap<>();
    for(String k:kinds.keySet()){List<Catalog.Title> recentTitles=new ArrayList<>();for(Catalog.Title t:candidates)if(t.kind.equals(k)&&(t.raw.optInt("latestRank",Integer.MAX_VALUE)<Integer.MAX_VALUE||t.raw.optLong("date_uploaded_unix")>0))recentTitles.add(t);
      recentTitles.sort(Comparator.comparingLong(t->t.raw.optLong("date_uploaded_unix")>0?-t.raw.optLong("date_uploaded_unix"):t.raw.optInt("latestRank",Integer.MAX_VALUE)));recentKinds.put(k,recentTitles);}
    for(int n=0;n<limit&&added.size()<limit;n++)for(List<Catalog.Title> list:recentKinds.values())if(n<list.size()&&added.size()<limit)added.add(new Card(list.get(n),null,""));
    if(!added.isEmpty())rows.add(new Row("Recently added",added));
    for(String k:kinds.keySet())rows.add(new Row(k.equals("movies")?"Movies":k.equals("shows")?"Series":"Anime",kinds.get(k)));
    for(String language:new String[]{"hi","ko"}){List<Card> cards=new ArrayList<>();for(Catalog.Title t:candidates)if(t.raw.optString("language").equals(language)&&cards.size()<limit)cards.add(new Card(t,null,""));if(!cards.isEmpty())rows.add(new Row(language.equals("hi")?"Hindi picks":"Korean picks",cards));}
    return rows;
  }
}
