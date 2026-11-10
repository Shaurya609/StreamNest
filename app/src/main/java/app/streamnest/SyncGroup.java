package app.streamnest;

import android.content.*;
import android.os.Build;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.json.*;

final class SyncGroup {
  static volatile JSONObject current;
  static volatile String error = "";
  static long last;
  static final java.util.concurrent.ExecutorService worker=java.util.concurrent.Executors.newSingleThreadExecutor();
  static android.content.SharedPreferences prefs(Context c) {return c.getSharedPreferences("sync-group",0);}
  static String deviceName(Context c) {return prefs(c).getString("deviceName",Build.MODEL);}
  static synchronized String deviceId(Context c) {
    String id=prefs(c).getString("deviceId","");
    if(id.isEmpty()){id=UUID.randomUUID().toString();prefs(c).edit().putString("deviceId",id).commit();}
    return id;
  }
  static JSONObject call(Context c,String code,String method,JSONObject body) throws Exception {
    HttpURLConnection connection=(HttpURLConnection)new URL(WatchSync.endpoint(c)+"/v1/group").openConnection();
    connection.setConnectTimeout(8000);connection.setReadTimeout(10000);connection.setInstanceFollowRedirects(false);
    connection.setRequestMethod(method);connection.setRequestProperty("Authorization","Bearer "+WatchSyncCrypto.auth(code));
    try {
      if(body!=null){byte[] bytes=body.toString().getBytes(StandardCharsets.UTF_8);connection.setRequestProperty("Content-Type","application/json");connection.setFixedLengthStreamingMode(bytes.length);connection.setDoOutput(true);try(OutputStream out=connection.getOutputStream()){out.write(bytes);}}
      if(connection.getResponseCode()!=200)throw new IOException("Group service HTTP "+connection.getResponseCode());
      try(InputStream in=connection.getInputStream()){return new JSONObject(new String(Net.read(in,16000),StandardCharsets.UTF_8));}
    } finally {connection.disconnect();}
  }
  static void update(Context c,String name) throws Exception {
    String code=WatchSync.code(c);if(code.isEmpty())return;
    JSONObject body=new JSONObject().put("deviceId",deviceId(c)).put("deviceName",deviceName(c));
    if(name!=null)body.put("groupName",name);
    try {JSONObject result=call(c,code,"PUT",body);if(code.equals(WatchSync.code(c))){current=result;error="";last=System.currentTimeMillis();}}
    catch(Exception e){error="Group details unavailable — watch sync still works";throw e;}
  }
  static synchronized void refresh(Context c) {if(System.currentTimeMillis()-last<60000)return;last=System.currentTimeMillis();Context app=c.getApplicationContext();worker.execute(()->{try{update(app,null);}catch(Exception ignored){}});}
}
