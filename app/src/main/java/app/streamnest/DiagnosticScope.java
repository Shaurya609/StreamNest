package app.streamnest;
/** Cache just the current pairing identity; never store it in an event or on disk. */
final class DiagnosticScope {
 private static final class Entry{final String code,id;Entry(String c,String i){code=c;id=i;}}
 private static volatile Entry last;
 static String id(String code)throws Exception{
  if(code==null||code.isEmpty()){last=null;return "";}Entry e=last;if(e!=null&&e.code.equals(code))return e.id;
  byte[] digest=java.security.MessageDigest.getInstance("SHA-256").digest(code.getBytes(java.nio.charset.StandardCharsets.UTF_8));char[] hex=new char[digest.length*2];String alphabet="0123456789abcdef";
  for(int n=0;n<digest.length;n++){int v=digest[n]&255;hex[n*2]=alphabet.charAt(v>>>4);hex[n*2+1]=alphabet.charAt(v&15);}String id=new String(hex);last=new Entry(code,id);return id;
 }
 static void clear(){last=null;}
}
