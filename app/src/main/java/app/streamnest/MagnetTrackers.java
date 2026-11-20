package app.streamnest;
import java.net.*;import java.util.*;import org.libtorrent4j.*;
/** Retain only bounded tracker hints supplied in the original public magnet. */
final class MagnetTrackers {
 static List<String> urls(String source){LinkedHashSet<String> out=new LinkedHashSet<>();try{URI magnet=URI.create(source);if(!"magnet".equalsIgnoreCase(magnet.getScheme()))return new ArrayList<>();String query=magnet.getRawSchemeSpecificPart().replaceFirst("^\\?","");for(String part:query.split("&")){if(out.size()>=12)break;if(!part.startsWith("tr="))continue;try{String value=URLDecoder.decode(part.substring(3),"UTF-8");URI uri=URI.create(value);if(value.length()<=2048&&Arrays.asList("udp","http","https").contains(uri.getScheme())&&uri.getHost()!=null&&uri.getUserInfo()==null)out.add(value);}catch(Exception ignored){}}}catch(Exception ignored){}return new ArrayList<>(out);}
 static int apply(TorrentHandle handle,String source,boolean privateTorrent){if(privateTorrent)return 0;Set<String> existing=new HashSet<>();for(AnnounceEntry entry:handle.trackers())existing.add(entry.url());int added=0;for(String tracker:urls(source))if(existing.add(tracker)){handle.addTracker(new AnnounceEntry(tracker));added++;}return added;}
}
