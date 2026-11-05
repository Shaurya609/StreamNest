package app.streamnest;
import static org.junit.Assert.*;
import org.junit.Test;
public class PairingTicketTest {
  @Test public void shortCodeRoundtripAndWrongKey() throws Exception {
    String code = PairingTicket.create(), recovery = WatchSyncCrypto.create();
    assertEquals(8,code.length());
    assertEquals(code,PairingTicket.normalize(code.substring(0,4)+" "+code.substring(4)));
    assertFalse(PairingTicket.lookup(code).equals(WatchSyncCrypto.hex(PairingTicket.digest(code))));
    String payload = PairingTicket.seal(code,recovery);
    assertEquals(recovery,PairingTicket.open(code,payload));
    try { PairingTicket.open("ABCDEFGH",payload); fail("Wrong code accepted"); }
    catch(javax.crypto.AEADBadTagException expected) {}
  }

  @Test public void opensDesktopTicket() throws Exception {
    try(java.io.InputStream in=getClass().getResourceAsStream("/pairing-ticket-vector.json")) {
      org.json.JSONObject vector = new org.json.JSONObject(new String(in.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
      assertEquals(vector.getString("recovery"),PairingTicket.open(vector.getString("code"),vector.getString("payload")));
    }
  }
}
