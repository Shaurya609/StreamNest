package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import org.json.*;
final class CrashCaptureRegression {
 static Bundle run(Instrumentation test)throws Exception{
  Context c=test.getTargetContext();WatchSync.pending=true;WatchSync.testPairingCode="";
  try{Diagnostics.install(c);Thread.sleep(700);WatchSync.testPairingCode=WatchSyncCrypto.create();String scope=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(WatchSync.testPairingCode.getBytes(java.nio.charset.StandardCharsets.UTF_8)));c.getSharedPreferences("diagnostics",0).edit().putString("scope",scope).commit();Diagnostics.attemptId="12345678-1234-1234-1234-123456789abc";
   IOException error=new IOException("private-path secret token");error.setStackTrace(new StackTraceElement[]{new StackTraceElement("app.streamnest.StreamService","release","StreamService.java",131)});
   CrashJournal.capture(c,error);File capsule=new File(c.getFilesDir(),"diagnostic-crash.json");HomeRegression.check(capsule.isFile(),"Crash capsule not saved");String saved=new String(java.nio.file.Files.readAllBytes(capsule.toPath()),java.nio.charset.StandardCharsets.UTF_8);HomeRegression.check(!saved.contains("private-path")&&!saved.contains("secret")&&saved.contains("StreamService.release:131"),"Unsafe or missing crash stack");
   CrashJournal.replay(c);Thread.sleep(500);HomeRegression.check(!capsule.exists(),"Crash capsule not consumed");String journal=new String(java.nio.file.Files.readAllBytes(new File(c.getFilesDir(),"diagnostics.json").toPath()),java.nio.charset.StandardCharsets.UTF_8);HomeRegression.check(journal.contains("app.crash")&&journal.contains(Diagnostics.attemptId),"Recovered crash lost correlation");
   TorrentEngine stuck=new TorrentEngine(){@Override public void close(){throw new IllegalStateException("Unconfirmed worker shutdown");}};HomeRegression.check(!StreamService.release(stuck),"Cleanup failure escaped or was accepted");
   CrashJournal.capture(c,error);Diagnostics.setEnabled(c,false);HomeRegression.check(!capsule.exists(),"Opt-out retained capsule");CrashJournal.capture(c,error);HomeRegression.check(!capsule.exists(),"Opt-out captured crash");
   Bundle b=new Bundle();b.putString("result","PASS: bounded redacted crash persistence/replay and attempt correlation, protected cleanup failure contained, opt-out clears and prevents capture");return b;
  }finally{Diagnostics.setEnabled(c,false);WatchSync.testPairingCode=null;WatchSync.pending=false;}
 }
}
