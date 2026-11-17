package app.streamnest;
import java.util.*;import java.io.*;import java.nio.charset.StandardCharsets;import org.json.*;
/** Only exact torrent and selected-file matches; never guesses anime episode numbering. */
final class AnimeSubtitles {
 static List<SubtitleQuery.Result> candidates(JSONObject doc,String hash,String filename,String language)throws Exception {
  List<SubtitleQuery.Result> out=new ArrayList<>();if(!hash.matches("[a-f0-9]{40}")||!hash.equalsIgnoreCase(doc.optString("btih"))||!language.equals("en"))return out;
  JSONArray files=doc.optJSONArray("files");if(files==null)return out;List<JSONObject> videos=new ArrayList<>(),matched=new ArrayList<>();
  for(int n=0;n<files.length();n++){JSONObject f=files.getJSONObject(n);if(f.optString("filename").matches("(?i).*\\.(mkv|mp4|webm)$"))videos.add(f);}
  for(JSONObject f:videos)if(filename.isEmpty()?videos.size()==1:base(filename).equals(base(f.optString("filename"))))matched.add(f);if(matched.size()!=1)return out;
  JSONArray attachments=matched.get(0).optJSONArray("attachments");if(attachments==null)return out;
  for(int n=0;n<attachments.length();n++){JSONObject a=attachments.getJSONObject(n),info=a.optJSONObject("info");long id=a.optLong("id");if(a.optInt("type")!=1||info==null||id<=0||id>Integer.MAX_VALUE||!Arrays.asList("en","eng").contains(info.optString("lang").toLowerCase(Locale.ROOT))||!Arrays.asList("ASS","SSA","SRT").contains(info.optString("codec").toUpperCase(Locale.ROOT)))continue;
   SubtitleQuery.Result result=new SubtitleQuery.Result((int)id,"TsukiHime · English · "+info.optString("name","Subtitles"),true);result.provider="tsukihime";result.codec=info.optString("codec").toLowerCase(Locale.ROOT);result.forced=info.optInt("forced")!=0||MediaTracks.partial(info.optString("name"));result.link="https://storage.tsukihime.org/"+(doc.optBoolean("animetosho")?"tosho/":"")+"attach/"+String.format(Locale.ROOT,"%08x",id)+"/"+id+".xz";out.add(result);
  }out.sort(Comparator.comparing(r->r.forced));return out;
 }
 static String base(String s){String[] parts=s.replace('\\','/').split("/");return parts[parts.length-1];}
 static List<SubtitleQuery.Result> search(String hash,String filename,String language)throws Exception {if(!hash.matches("[a-f0-9]{40}")||!language.equals("en"))return new ArrayList<>();try{String text=new String(Net.bytes("https://api.tsukihime.org/v1/torrents/btih/"+hash,2000000,5000,10000),StandardCharsets.UTF_8);return candidates(new JSONObject(text),hash,filename,language);}catch(IOException e){if("Server returned HTTP 404".equals(e.getMessage()))return new ArrayList<>();throw e;}}
 static File download(SubtitleQuery.Result r,File directory)throws Exception {if(!r.link.matches("https://storage\\.tsukihime\\.org/(?:tosho/)?attach/[a-f0-9]{8}/[1-9][0-9]*\\.xz"))throw new IOException("Invalid anime subtitle address");byte[] compressed=Net.bytes(r.link,2000000,5000,10000),data;try(InputStream in=new org.tukaani.xz.XZInputStream(new ByteArrayInputStream(compressed),16384)){data=Net.read(in,8000000);}String text=new String(data,StandardCharsets.UTF_8);if(!text.contains("[Events]")&&!text.matches("(?s).*\\d+:\\d\\d:\\d\\d[,.]\\d{3}\\s*-->.*"))throw new IOException("No usable subtitle cues");File file=new File(directory,"tsukihime-"+r.fileId+"."+(r.codec.equals("srt")?"srt":"ass"));File tmp=new File(directory,file.getName()+".tmp");java.nio.file.Files.write(tmp.toPath(),data);java.nio.file.Files.move(tmp.toPath(),file.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING);return file;}
}
