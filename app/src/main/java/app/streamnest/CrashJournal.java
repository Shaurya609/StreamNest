package app.streamnest;

import android.content.Context;
import android.util.AtomicFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import org.json.*;

/** One bounded, scope-bound crash capsule; never network, URLs, messages or raw thread names. */
final class CrashJournal {
 private static final AtomicBoolean handling=new AtomicBoolean();
 private static AtomicFile file(Context c){return new AtomicFile(new File(c.getFilesDir(),"diagnostic-crash.json"));}
 static void clear(Context c){file(c).delete();}
 static void install(Context c){
  Thread.UncaughtExceptionHandler previous=Thread.getDefaultUncaughtExceptionHandler();
  Thread.setDefaultUncaughtExceptionHandler((thread,error)->{
   try{
    if(handling.compareAndSet(false,true)&&Diagnostics.enabled(c)){
     capture(c,error);
    }
   }catch(Throwable ignored){}
   finally{if(previous!=null)previous.uncaughtException(thread,error);else{android.os.Process.killProcess(android.os.Process.myPid());System.exit(10);}}
  });
 }
 static void capture(Context c,Throwable error)throws Exception{
  if(!Diagnostics.enabled(c))return;
     JSONObject data=DiagnosticData.failure("app","playback",error).put("reason",error instanceof OutOfMemoryError?"low_memory":"java_crash");
     if(!Diagnostics.attemptId.isEmpty())data.put("attemptId",Diagnostics.attemptId);
     JSONObject capsule=new JSONObject().put("scope",c.getSharedPreferences("diagnostics",0).getString("scope","")).put("at",System.currentTimeMillis()).put("data",data);
     byte[] bytes=capsule.toString().getBytes(StandardCharsets.UTF_8);
     if(bytes.length<=16000){AtomicFile f=file(c);FileOutputStream out=f.startWrite();try{out.write(bytes);f.finishWrite(out);}catch(Throwable failed){f.failWrite(out);}}
 }
 static void replay(Context c){try{
  AtomicFile f=file(c);if(f.getBaseFile().length()>16000){f.delete();return;}
  try(InputStream in=f.openRead()){
   JSONObject capsule=new JSONObject(new String(Net.read(in,16000),StandardCharsets.UTF_8));
   if(Diagnostics.enabled(c)&&capsule.optString("scope").equals(c.getSharedPreferences("diagnostics",0).getString("scope",""))&&System.currentTimeMillis()-capsule.optLong("at")<604800000L){
    JSONObject data=DiagnosticData.clean(capsule.optJSONObject("data"));data.put("elapsedMs",Math.max(0,System.currentTimeMillis()-capsule.optLong("at")));Diagnostics.event("app.crash",data);
   }
  }f.delete();
 }catch(Exception ignored){clear(c);}}
}
