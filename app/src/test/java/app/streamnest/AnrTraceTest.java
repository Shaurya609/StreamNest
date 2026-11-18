package app.streamnest;
import org.junit.Test;import static org.junit.Assert.*;import java.io.*;import org.json.*;
public class AnrTraceTest {
 @Test public void onlyMainThreadSafeLocationsLeaveTheDevice()throws Exception{
 String trace="private account URL https://private.example token=password\n\"main\" prio=5 tid=1 Native\n  at org.libtorrent4j.swig.libtorrent_jni.pause(Native method)\n  at app.streamnest.PlayerActivity.onStop(PlayerActivity.java:1043)\n  at private.account.Credentials.run(Password.java:42)\n\"private-worker-title\" prio=5\n  at app.streamnest.Secret.run(Secret.java:12)\n";
 JSONArray rows=AnrTrace.parse(trace);assertEquals(2,rows.length());assertEquals("org.libtorrent4j.swig.libtorrent_jni.pause:0",rows.getString(0));assertFalse(rows.toString().contains("private"));assertFalse(rows.toString().contains("Password"));assertFalse(rows.toString().contains("Secret"));
 }
 @Test public void missingMainThreadIsNotGuessed(){assertEquals(0,AnrTrace.parse("at app.streamnest.Other.run(File.java:7)").length());}
 @Test public void traceReadIsBounded()throws Exception{final int[] read={0};InputStream large=new InputStream(){public int read(){read[0]++;return 'x';}};assertEquals(0,AnrTrace.read(large).length());assertEquals(262144,read[0]);}
}
