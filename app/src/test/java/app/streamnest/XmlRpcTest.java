package app.streamnest;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.junit.Test;

public class XmlRpcTest {
  @Test
  public void credentialsCannotChangeXmlStructure() {
    String xml =
        new String(
            XmlRpc.request("LogIn", "user<&", "secret</string><value>evil", "en", "StreamNest"),
            StandardCharsets.UTF_8);
    assertTrue(xml.contains("user&lt;&amp;"));
    assertTrue(xml.contains("secret&lt;/string&gt;&lt;value&gt;evil"));
  }

  @Test
  public void parsesStructWithArray() throws Exception {
    String xml =
        "<methodResponse><params><param><value><struct><member><name>status</name><value><string>200"
            + " OK</string></value></member><member><name>data</name><value><array><data><value><string>one</string></value><value><int>2</int></value></data></array></value></member></struct></value></param></params></methodResponse>";
    Map<?, ?> result = (Map<?, ?>) XmlRpc.response(xml.getBytes(StandardCharsets.UTF_8));
    assertEquals("200 OK", result.get("status"));
    assertEquals(2, ((List<?>) result.get("data")).size());
  }

  @Test(expected = java.io.IOException.class)
  public void rejectsExternalEntities() throws Exception {
    XmlRpc.response(
        "<!DOCTYPE x [<!ENTITY x SYSTEM 'file:///secret'>]><methodResponse/>"
            .getBytes(StandardCharsets.UTF_8));
  }

  @Test(expected = java.io.IOException.class)
  public void rejectsApiFault() throws Exception {
    XmlRpc.response(
        "<methodResponse><fault><value><struct><member><name>faultString</name><value><string>Denied</string></value></member></struct></value></fault></methodResponse>"
            .getBytes(StandardCharsets.UTF_8));
  }

  @Test(expected = java.io.IOException.class)
  public void rejectsVipBannerInsteadOfSubtitle() throws Exception {
    SubtitleQuery.rejectServiceNotice(
        "1\n00:00:00,001 --> 04:00:00,003\nBecome OpenSubtitles.org VIP Member\nto get subtitles -> osdb.link/vip"
            .getBytes(StandardCharsets.UTF_8));
  }

  @Test
  public void acceptsActualSrt() throws Exception {
    SubtitleQuery.rejectServiceNotice(
        "1\n00:00:01,000 --> 00:00:03,000\nHello".getBytes(StandardCharsets.UTF_8));
  }
}
