package app.streamnest;
/** Group-scoped opaque source correlation. The owner never receives a raw infohash or group key. */
final class DiagnosticSource {
 static String id(String code,String infoHash){try{if(infoHash==null||!infoHash.matches("(?i)[a-f0-9]{40}"))return "";byte[] key=WatchSyncCrypto.derive(WatchSyncCrypto.parse(code),"StreamNest diagnostic media v1");javax.crypto.Mac mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(key,"HmacSHA256"));return WatchSyncCrypto.hex(mac.doFinal(infoHash.toLowerCase(java.util.Locale.ROOT).getBytes(java.nio.charset.StandardCharsets.UTF_8)));}catch(Exception e){return "";}}
}
