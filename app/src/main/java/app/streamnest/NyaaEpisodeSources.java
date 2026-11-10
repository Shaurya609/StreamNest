package app.streamnest;
import java.util.*;
import java.util.regex.*;
import org.json.*;
import org.w3c.dom.*;
import javax.xml.parsers.*;
import org.libtorrent4j.*;

final class NyaaEpisodeSources {
  static final class Candidate {String title,url,hash,quality;int seeds;boolean pack;}
  static final Map<String,List<Catalog.Source>> cache=new LinkedHashMap<>();
  static final Map<String,Long> ages=new HashMap<>();
  static void invalidate(){synchronized(cache){cache.clear();ages.clear();}}
  static int match(String release,String title,int season,int episode,int absolute){
    String text=release.replaceAll("^(?:\\[[^\\]]+\\]\\s*)+","");
    Matcher prefix=Pattern.compile("(?iu)^"+Pattern.quote(title)+"(?:\\s*\\((?:TV|[12][0-9]{3})\\))?(?=\\s|\\[|\\(|$)").matcher(text);
    if(!prefix.find())return 0;String tail=text.substring(prefix.end());
    Matcher single=Pattern.compile("^\\s*-\\s*([0-9]{1,4})(?:v[0-9]+)?(?=\\s|\\[|\\(|\\.|$)").matcher(tail);
    if(single.find()&&!tail.substring(single.end()).matches("^\\s*[-~].*"))return absolute>0&&Integer.parseInt(single.group(1))==absolute?1:0;
    Matcher ranged=Pattern.compile("^\\s*(?:-\\s*)?[\\[(]?([0-9]{1,4})\\s*[-~]\\s*([0-9]{1,4})(?=\\s|[)\\]]|$)").matcher(tail);
    if(ranged.find())return absolute>0&&Integer.parseInt(ranged.group(1))<=absolute&&absolute<=Integer.parseInt(ranged.group(2))?2:0;
    return 0;
  }
  static List<Candidate> parse(byte[] bytes,String title,JSONObject row)throws Exception {
    String xml=new String(bytes,java.nio.charset.StandardCharsets.UTF_8),upper=xml.toUpperCase(Locale.ROOT);
    if(xml.indexOf(0)>=0||upper.contains("<!DOCTYPE")||upper.contains("<!ENTITY"))throw new java.io.IOException("Unsafe XML");
    DocumentBuilderFactory f=DocumentBuilderFactory.newInstance();f.setExpandEntityReferences(false);DocumentBuilder b=f.newDocumentBuilder();b.setEntityResolver((p,s)->new org.xml.sax.InputSource(new java.io.StringReader("")));
    Document doc=b.parse(new java.io.ByteArrayInputStream(bytes));if(!doc.getDocumentElement().getTagName().equalsIgnoreCase("rss"))throw new java.io.IOException("Invalid RSS");
    List<Candidate> out=new ArrayList<>();NodeList items=doc.getElementsByTagName("item");
    for(int i=0;i<items.getLength();i++){
      Element item=(Element)items.item(i);if(!AnimeReleases.child(item,"nyaa:trusted").equalsIgnoreCase("Yes"))continue;
      Candidate c=new Candidate();c.title=AnimeReleases.child(item,"title");c.url=AnimeReleases.child(item,"link");c.hash=AnimeReleases.child(item,"nyaa:infoHash").toLowerCase(Locale.ROOT);
      c.quality=PlaybackPlan.episodeQuality(new Catalog.Source(c.title,"",0));int match=match(c.title,title,row.optInt("season"),row.optInt("episode"),row.optInt("absolute"));
      try{c.seeds=Integer.parseInt(AnimeReleases.child(item,"nyaa:seeders"));}catch(Exception e){continue;}
      if(match==0||c.quality.isEmpty()||c.seeds<=0||!c.url.matches("https://nyaa\\.si/download/[0-9]+\\.torrent")||!c.hash.matches("[a-f0-9]{40}"))continue;
      c.pack=match==2;out.add(c);
    }
    out.sort(Comparator.comparingInt((Candidate c)->c.seeds).reversed());return out;
  }
  static Catalog.Source indexed(Candidate c,byte[] bytes,JSONObject row)throws Exception {
    TorrentInfo info=new TorrentInfo(bytes);try{if(!info.isValid()||!info.infoHash().toHex().equalsIgnoreCase(c.hash))throw new java.io.IOException("Torrent identity mismatch");
    FileStorage files=info.files();List<String> paths=new ArrayList<>();List<Long> sizes=new ArrayList<>();boolean matched=false;
    for(int n=0;n<files.numFiles();n++){String path=files.filePath(n);paths.add(path);sizes.add(files.fileSize(n));if(EpisodeFiles.video(path)&&EpisodeFiles.matches(path,row.optInt("season"),row.optInt("episode"),row.optInt("absolute")))matched=true;}
    if(c.pack&&!matched)throw new java.io.IOException("Pack has no exact episode member");
    int index=EpisodeFiles.select(paths,sizes,-1,"",true,row.optInt("season"),row.optInt("episode"),row.optInt("absolute"));
    Catalog.Source result=new Catalog.Source(c.quality+" · Nyaa"+(c.title.toLowerCase(Locale.ROOT).matches(".*(hevc|265|av1).*" )?" · HEVC/AV1":""),c.url,c.seeds);
    result.fileIndex=index;result.filename=new java.io.File(paths.get(index).replace('\\','/')).getName();return result;}finally{info.swig().delete();}
  }
  static List<Catalog.Source> find(String title,JSONObject row,long deadline)throws Exception {
    String key=title+":"+row.optInt("season")+":"+row.optInt("episode")+":"+row.optInt("absolute");
    synchronized(cache){if(cache.containsKey(key)&&System.currentTimeMillis()-ages.get(key)<(cache.get(key).isEmpty()?30000:300000))return new ArrayList<>(cache.get(key));}
    String search="\""+title.replace("\"","")+"\" "+String.format(Locale.ROOT,"%02d",row.optInt("absolute"));
    List<Candidate> candidates=parse(Net.catalogBytes("https://nyaa.si/?page=rss&c=1_2&f=2&q="+Net.q(search),2000000,deadline),title,row);
    List<Catalog.Source> results=new ArrayList<>();Exception failure=null;
    for(int i=0;i<Math.min(2,candidates.size());i++){if(Thread.currentThread().isInterrupted())throw new InterruptedException();Candidate c=candidates.get(i);try{results.add(indexed(c,Net.catalogBytes(c.url,8388608,deadline),row));}catch(Exception e){failure=e;}}
    if(results.isEmpty()&&failure!=null)throw new java.io.IOException("Anime torrent metadata could not be verified",failure);
    synchronized(cache){cache.put(key,results);ages.put(key,System.currentTimeMillis());while(cache.size()>12){String first=cache.keySet().iterator().next();cache.remove(first);ages.remove(first);}}
    return new ArrayList<>(results);
  }
}
