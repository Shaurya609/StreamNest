package app.streamnest;
import org.junit.Test;import static org.junit.Assert.*;
public class DiagnosticScopeTest {
 @Test public void cachedPairingHashPreservesShaAndChangesImmediately()throws Exception{String code="scope-one";byte[] b=java.security.MessageDigest.getInstance("SHA-256").digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));StringBuilder expected=new StringBuilder();for(byte v:b)expected.append(String.format(java.util.Locale.ROOT,"%02x",v));assertEquals(expected.toString(),DiagnosticScope.id(code));assertSame(DiagnosticScope.id(code),DiagnosticScope.id(code));assertNotEquals(DiagnosticScope.id(code),DiagnosticScope.id("scope-two"));assertEquals("",DiagnosticScope.id(""));DiagnosticScope.clear();assertEquals(expected.toString(),DiagnosticScope.id(code));}
}
