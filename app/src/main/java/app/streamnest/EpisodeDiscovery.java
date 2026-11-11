package app.streamnest;
import java.util.*;
import org.json.*;

final class EpisodeDiscovery {
  interface Index {List<Catalog.Source> get(String id,int season,int episode,long deadline)throws Exception;}
  interface Anime {List<Catalog.Source> get(String title,JSONObject row,long deadline)throws Exception;}
  static Index index=AlternateEpisodes::find;
  static Index series=EztvEpisodes::find;
  static Anime anime=NyaaEpisodeSources::find;
  static String key(Catalog.Source s){String hash=TorrentMetadata.hash(s.url);return (hash.isEmpty()?s.url:hash)+":"+s.fileIndex;}
  static List<Catalog.Source> find(String id,JSONObject row,String title)throws Exception {
    if(!id.matches("tt[0-9]+")||row.optInt("season")<0||row.optInt("episode")<=0)return Collections.emptyList();
    long deadline=System.nanoTime()+18000000000L;
    List<Catalog.Source> all=new ArrayList<>();Exception failure=null;
    try{all.addAll(index.get(id,row.optInt("season"),row.optInt("episode"),deadline));}catch(Exception e){failure=e;}
    if(Thread.currentThread().isInterrupted())throw new InterruptedException();
    if(all.isEmpty()&&row.optInt("kitsuId")>0&&row.optInt("kitsuEpisode")>0)try{
      all.addAll(AlternateEpisodes.parse(new JSONObject(new String(Net.catalogBytes("https://torrentio.strem.fun/stream/series/kitsu:"+row.getInt("kitsuId")+":"+row.getInt("kitsuEpisode")+".json",2000000,Math.min(deadline,System.nanoTime()+4000000000L)),java.nio.charset.StandardCharsets.UTF_8))));
    }catch(Exception e){failure=e;}
    if(row.optBoolean("animeMapped")&&row.optInt("season")>0&&row.optInt("absolute")>0&&!title.isBlank())try{all.addAll(anime.get(row.optString("animeTitle").isBlank()?title:row.optString("animeTitle"),row,deadline));}catch(Exception e){failure=e;}
    if(!row.optBoolean("animeMapped"))try{all.addAll(series.get(id,row.optInt("season"),row.optInt("episode"),Math.min(deadline,System.nanoTime()+6000000000L)));}catch(Exception e){failure=e;}
    LinkedHashMap<String,Catalog.Source> unique=new LinkedHashMap<>();for(Catalog.Source s:all)unique.putIfAbsent(key(s),s);
    if(unique.isEmpty()&&failure!=null)throw new java.io.IOException("Source lookup incomplete. A provider could not be reached; tap to retry.",failure);
    return new ArrayList<>(unique.values());
  }
  static void invalidate(String id,int season,int episode){synchronized(AlternateEpisodes.cache){String key=id+":"+season+":"+episode;AlternateEpisodes.cache.remove(key);AlternateEpisodes.ages.remove(key);}NyaaEpisodeSources.invalidate();EztvEpisodes.retry();}
}
