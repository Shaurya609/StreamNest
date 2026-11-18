package app.streamnest;
import static org.junit.Assert.*;import org.junit.Test;import org.json.*;
public class DiagnosticSourceTest {
 @Test public void opaqueIdMatchesDesktopVectorAndIsScopedToGroup()throws Exception{byte[] root=new byte[16];for(int i=0;i<16;i++)root[i]=(byte)i;String code=WatchSyncCrypto.format(root);String id=DiagnosticSource.id(code,"a".repeat(40));assertEquals("7a7385b329597cb86fa1e0926ba4d315715fb29aead47670e660a3f4e0aaec9a",id);assertEquals(id,DiagnosticSource.id(code,"A".repeat(40)));root[0]=42;assertNotEquals(id,DiagnosticSource.id(WatchSyncCrypto.format(root),"a".repeat(40)));assertEquals("",DiagnosticSource.id(code,"magnet:?xt=private"));}
 @Test public void diagnosticsKeepOnlyStrictOpaqueIdAndFreshness()throws Exception{JSONObject d=DiagnosticData.clean(new JSONObject().put("mediaId","a".repeat(64)).put("telemetryAgeMs",500).put("infoHash","a".repeat(40)));assertEquals(2,d.length());assertFalse(d.has("infoHash"));assertEquals(0,DiagnosticData.clean(new JSONObject().put("mediaId","https://private")).length());}
}
