package app.streamnest;

import java.io.*;
import java.util.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;

final class XmlRpc {
  static String escape(String s) {
    return s.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;");
  }

  static String value(Object o) {
    if (o instanceof Map) {
      StringBuilder b = new StringBuilder("<value><struct>");
      for (Object entry : ((Map<?, ?>) o).entrySet()) {
        Map.Entry<?, ?> e = (Map.Entry<?, ?>) entry;
        b.append("<member><name>")
            .append(escape(String.valueOf(e.getKey())))
            .append("</name>")
            .append(value(e.getValue()))
            .append("</member>");
      }
      return b + "</struct></value>";
    }
    if (o instanceof List) {
      StringBuilder b = new StringBuilder("<value><array><data>");
      for (Object item : (List<?>) o) b.append(value(item));
      return b + "</data></array></value>";
    }
    if (o instanceof Integer || o instanceof Long) return "<value><int>" + o + "</int></value>";
    return "<value><string>" + escape(String.valueOf(o)) + "</string></value>";
  }

  static byte[] request(String method, Object... args) {
    StringBuilder b =
        new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?><methodCall><methodName>");
    b.append(escape(method)).append("</methodName><params>");
    for (Object arg : args) b.append("<param>").append(value(arg)).append("</param>");
    b.append("</params></methodCall>");
    return b.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
  }

  static List<Element> elements(Element e) {
    List<Element> out = new ArrayList<>();
    NodeList list = e.getChildNodes();
    for (int i = 0; i < list.getLength(); i++)
      if (list.item(i) instanceof Element) out.add((Element) list.item(i));
    return out;
  }

  static Element child(Element e, String name) {
    for (Element c : elements(e)) if (c.getTagName().equals(name)) return c;
    throw new IllegalArgumentException("Malformed subtitle API response");
  }

  static Object parseValue(Element value) {
    List<Element> typed = elements(value);
    if (typed.isEmpty()) return value.getTextContent();
    Element e = typed.get(0);
    String text = e.getTextContent();
    switch (e.getTagName()) {
      case "struct":
        Map<String, Object> map = new LinkedHashMap<>();
        for (Element member : elements(e))
          map.put(child(member, "name").getTextContent(), parseValue(child(member, "value")));
        return map;
      case "array":
        List<Object> list = new ArrayList<>();
        for (Element v : elements(child(e, "data"))) list.add(parseValue(v));
        return list;
      case "int":
      case "i4":
      case "i8":
        return Long.parseLong(text.trim());
      case "boolean":
        return text.trim().equals("1");
      case "double":
        return Double.parseDouble(text.trim());
      case "base64":
        return java.util.Base64.getMimeDecoder().decode(text);
      default:
        return text;
    }
  }

  static Object response(byte[] bytes) throws Exception {
    if (bytes.length > 8000000) throw new IOException("Subtitle API response too large");
    String xml = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    if (xml.indexOf(0) >= 0 || xml.contains("<!DOCTYPE") || xml.contains("<!ENTITY"))
      throw new IOException("Unsafe subtitle XML response");
    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
    factory.setExpandEntityReferences(false);
    Document doc = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
    Element root = doc.getDocumentElement();
    for (Element e : elements(root))
      if (e.getTagName().equals("fault")) {
        Object fault = parseValue(child(e, "value"));
        throw new IOException(
            "Subtitle API returned a fault"
                + (fault instanceof Map ? ": " + ((Map<?, ?>) fault).get("faultString") : ""));
      }
    return parseValue(child(child(child(root, "params"), "param"), "value"));
  }
}
