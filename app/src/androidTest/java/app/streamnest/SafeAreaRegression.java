package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import androidx.core.graphics.Insets;import androidx.core.view.*;import java.io.*;
final class SafeAreaRegression {
 static Bundle run(Instrumentation test)throws Exception {
  Context c=test.getTargetContext();HomeRegression.check(!((android.app.KeyguardManager)c.getSystemService(Context.KEYGUARD_SERVICE)).isDeviceLocked(),"Unlock the device before visual verification");WatchSync.pending=true;WatchSync.testPairingCode="";Activity[] current={null};
  try {
   for(Class<?> screen:new Class<?>[]{MainActivity.class,PlaybackSettingsActivity.class,SubtitleSettingsActivity.class,WatchSyncActivity.class,CacheFolderActivity.class,AppUpdateActivity.class}) {
    current[0]=test.startActivitySync(new Intent(c,screen).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));Thread.sleep(600);test.waitForIdleSync();
    test.runOnMainSync(()->{View content=current[0].findViewById(android.R.id.content);WindowInsetsCompat actual=ViewCompat.getRootWindowInsets(content);HomeRegression.check(actual!=null,"Window insets unavailable");Insets bars=actual.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());HomeRegression.check(content.getPaddingTop()>=bars.top&&content.getPaddingBottom()>=bars.bottom,"Real system bars overlap "+screen.getSimpleName());
     WindowInsetsCompat fake=new WindowInsetsCompat.Builder().setInsets(WindowInsetsCompat.Type.systemBars(),Insets.of(0,72,0,30)).setInsets(WindowInsetsCompat.Type.displayCutout(),Insets.of(48,0,0,0)).build();ViewCompat.dispatchApplyWindowInsets(content,fake);ViewCompat.dispatchApplyWindowInsets(content,fake);HomeRegression.check(content.getPaddingTop()==72&&content.getPaddingLeft()==48&&content.getPaddingBottom()==30,"Insets missing or accumulated");
     WindowInsetsCompat keyboard=new WindowInsetsCompat.Builder(fake).setInsets(WindowInsetsCompat.Type.ime(),Insets.of(0,0,0,300)).build();ViewCompat.dispatchApplyWindowInsets(content,keyboard);HomeRegression.check(content.getPaddingBottom()==300,"Keyboard space missing or double counted");ViewCompat.dispatchApplyWindowInsets(content,new WindowInsetsCompat.Builder().build());HomeRegression.check(content.getPaddingTop()==0&&content.getPaddingLeft()==0&&content.getPaddingBottom()==0,"Hidden bars retained stale padding");ViewCompat.dispatchApplyWindowInsets(content,actual);ViewCompat.requestApplyInsets(content);
    });
    if(screen==MainActivity.class){Thread.sleep(300);android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(c.getCacheDir(),"safe-area.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    test.runOnMainSync(()->current[0].finish());current[0]=null;
   }
   Bundle b=new Bundle();b.putString("result","PASS: six native browsing/settings screens respect actual bars; repeated status/navigation/cutout/keyboard/zero-inset transitions stay bounded");return b;
  }finally{if(current[0]!=null)test.runOnMainSync(()->current[0].finish());WatchSync.pending=false;WatchSync.testPairingCode=null;}
 }
}
