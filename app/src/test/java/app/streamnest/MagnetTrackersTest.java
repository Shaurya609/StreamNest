package app.streamnest;
import org.junit.Test;import static org.junit.Assert.*;import java.net.URLEncoder;
public class MagnetTrackersTest {
 @Test public void retainsDistinctSourceTrackersAndIgnoresOtherMagnetHints()throws Exception{String tracker="https://tracker.example/announce?key=a+b";String url="magnet:?xt=urn:btih:abc&tr="+URLEncoder.encode(tracker,"UTF-8")+"&tr="+URLEncoder.encode(tracker,"UTF-8")+"&tr=udp%3A%2F%2Ftracker.example%3A1337%2Fannounce&ws=https://unrelated.example";assertEquals(2,MagnetTrackers.urls(url).size());assertEquals(tracker,MagnetTrackers.urls(url).get(0));}
 @Test public void malformedAndUnsafeHintsAreNotApplied(){assertTrue(MagnetTrackers.urls("https://example/?tr=https://tracker.example").isEmpty());assertTrue(MagnetTrackers.urls("magnet:?tr=file:///private&tr=https://user:password@tracker.example&tr=%XX").isEmpty());}
 @Test public void trackerHintsAreBounded(){StringBuilder source=new StringBuilder("magnet:?xt=urn:btih:abc");for(int i=0;i<100;i++)source.append("&tr=https://t").append(i).append(".example/announce");assertEquals(12,MagnetTrackers.urls(source.toString()).size());}
}
