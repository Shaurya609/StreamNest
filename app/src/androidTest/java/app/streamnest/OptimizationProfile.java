package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import org.json.*;
final class OptimizationProfile {
 static Bundle run(Instrumentation test)throws Exception{
  Context c=test.getTargetContext();WatchSync.pending=true;WatchSync.testPairingCode="";MainActivity activity=null;
  try{HomeRegression.check(!((KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked(),"Tablet locked; profile invalid");c.getSharedPreferences("performance",0).edit().putString("mode","lite").commit();Bundle out=new Bundle();
   long cpu=android.os.Process.getElapsedCpuTime(),begin=SystemClock.elapsedRealtime();activity=(MainActivity)test.startActivitySync(new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));test.waitForIdleSync();out.putLong("launchToIdleMs",SystemClock.elapsedRealtime()-begin);
   int max=0;for(int n=0;n<15;n++){Thread.sleep(2000);max=Math.max(max,(int)Debug.getPss());}out.putLong("cpuMs",android.os.Process.getElapsedCpuTime()-cpu);out.putInt("peakPssKb",max);out.putBoolean("locked",((KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE)).isKeyguardLocked());out.putString("result","PASS: isolated Low Memory Home startup and30-second process profile; network/cache variability applies");return out;
  }finally{if(activity!=null){MainActivity a=activity;test.runOnMainSync(a::finish);}WatchSync.testPairingCode=null;WatchSync.pending=false;}
 }
}
