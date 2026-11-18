package app.streamnest;
import java.io.*;import java.nio.charset.StandardCharsets;import java.util.regex.*;import org.json.*;
/** Only allowlisted Java locations from the main thread. Never retain raw OS traces. */
final class AnrTrace {
 static JSONArray read(InputStream input)throws IOException {
  ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] chunk=new byte[4096];int remaining=262144,n;
  while(remaining>0&&(n=input.read(chunk,0,Math.min(chunk.length,remaining)))>0){out.write(chunk,0,n);remaining-=n;}
  return parse(new String(out.toByteArray(),StandardCharsets.UTF_8));
 }
 static JSONArray parse(String trace){JSONArray frames=new JSONArray();boolean main=false;
  Pattern java=Pattern.compile("^\\s*at ([A-Za-z0-9_.$]+)\\((?:[^():]*:([0-9]{1,6})|Native method)\\)\\s*$");
  for(String line:trace.split("\\r?\\n")){
   if(line.startsWith("\"")){if(main)break;main=line.startsWith("\"main\"");continue;}
   if(!main||frames.length()>=12)continue;Matcher m=java.matcher(line);if(m.matches())frames.put(m.group(1)+":"+(m.group(2)==null?"0":m.group(2)));
  }
  try{JSONArray safe=DiagnosticData.clean(new JSONObject().put("frames",frames)).optJSONArray("frames");return safe==null?new JSONArray():safe;}catch(Exception ignored){return new JSONArray();}
 }
}
