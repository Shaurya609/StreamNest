package app.streamnest;
import android.app.*;import android.content.*;import java.io.*;import org.json.*;
/** Loaded only on API30+: keep new OS exit-report types out of the API28 diagnostics path. */
@android.annotation.TargetApi(30)
final class ExitReports {
 static void collect(Context c,java.util.function.Consumer<JSONObject> emit){try{
  SharedPreferences prefs=c.getSharedPreferences("diagnostics",0);long seen=prefs.getLong("exitAt",0),now=System.currentTimeMillis();
  ActivityManager manager=(ActivityManager)c.getSystemService(Context.ACTIVITY_SERVICE);
  for(ApplicationExitInfo info:manager.getHistoricalProcessExitReasons(c.getPackageName(),0,8)){
   if(!c.getPackageName().equals(info.getProcessName()))continue; // Recoverable torrent-worker exits are not player crashes.
   boolean unseen=info.getTimestamp()>seen;
   boolean trace=info.getReason()==ApplicationExitInfo.REASON_ANR&&info.getTimestamp()>prefs.getLong("traceAt",0)&&now-info.getTimestamp()<604800000L;
   if(!unseen&&!trace)continue;
   if(unseen)prefs.edit().putLong("exitAt",Math.max(info.getTimestamp(),prefs.getLong("exitAt",0))).apply();
   if(info.getReason()==ApplicationExitInfo.REASON_EXIT_SELF||info.getReason()==ApplicationExitInfo.REASON_USER_REQUESTED)continue;
   JSONObject data=new JSONObject().put("exitReason",info.getReason()).put("elapsedMs",Math.max(0,now-info.getTimestamp()));
   if(info.getReason()==ApplicationExitInfo.REASON_ANR){data.put("reason","anr");if(trace){prefs.edit().putLong("traceAt",info.getTimestamp()).apply();try(InputStream in=info.getTraceInputStream()){if(in!=null)data.put("frames",AnrTrace.read(in));}catch(Exception ignored){}}}
   emit.accept(data);
  }
 }catch(Exception ignored){}}
}
