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
    if(screen==MainActivity.class){
     verifyTitleScreens(test,(MainActivity)current[0]);
     Thread.sleep(300);android.graphics.Bitmap image=test.getUiAutomation().takeScreenshot();try(FileOutputStream out=new FileOutputStream(new File(c.getCacheDir(),"safe-area.png"))){image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}image.recycle();}
    test.runOnMainSync(()->current[0].finish());current[0]=null;
   }
   Bundle b=new Bundle();b.putString("result","PASS: browsing/settings and movie/series title windows respect actual bars; repeated status/navigation/cutout/keyboard/zero-inset transitions stay bounded");return b;
  }finally{if(current[0]!=null)test.runOnMainSync(()->current[0].finish());WatchSync.pending=false;WatchSync.testPairingCode=null;}
 }
 private static void verifyTitleScreens(Instrumentation test, MainActivity activity) throws Exception {
  for(String kind:new String[]{"movies","shows"}) {
   Catalog.Title title=new Catalog.Title();
   title.id=kind.equals("movies")?"tt1375666":"tt1520211";
   title.title=kind.equals("movies")?"Inception":"The Walking Dead";
   title.kind=kind;
   title.year="";
   title.poster="";
   title.synopsis="";
   title.raw=new org.json.JSONObject();
   TitleScreen[] dialog={null};
   test.runOnMainSync(()->{
    dialog[0]=new TitleScreen(activity,title);
    dialog[0].show();
    dialog[0].health.setText("Auto · No positive seed count reported · 1080p · Alternative · HEVC");
   });
   try {
    Thread.sleep(600);
    test.runOnMainSync(()->{
     View content=dialog[0].getWindow().findViewById(android.R.id.content);
     WindowInsetsCompat actual=ViewCompat.getRootWindowInsets(content);
     HomeRegression.check(actual!=null,"Title window insets unavailable");
     Insets bars=actual.getInsets(WindowInsetsCompat.Type.systemBars()|WindowInsetsCompat.Type.displayCutout());
     int[] closeLocation=new int[2];
     dialog[0].closeDetails.getLocationOnScreen(closeLocation);
     HomeRegression.check(content.getPaddingTop()>=bars.top
         &&content.getPaddingBottom()>=bars.bottom&&closeLocation[1]>=bars.top,
         "System bars overlap "+kind+" title controls");
     View watch=dialog[0].watch;
     android.graphics.Rect visibleWatch=new android.graphics.Rect();
     HomeRegression.check(watch.getGlobalVisibleRect(visibleWatch)
         &&visibleWatch.height()==watch.getHeight()
         &&watch.getHeight()>=activity.dp(36),
         "Watch Now is clipped on "+kind+" title details");
    });
    test.runOnMainSync(()->{
     Window window=dialog[0].getWindow();
     window.setLayout(-1,activity.dp(480));
    });
    Thread.sleep(300);
    test.runOnMainSync(()->{
     View watch=dialog[0].watch;
     android.graphics.Rect visible=new android.graphics.Rect();
     HomeRegression.check(watch.getGlobalVisibleRect(visible)&&visible.height()==watch.getHeight(),
         "Watch Now is clipped in a shorter "+kind+" title window");
     if(dialog[0].series)
      HomeRegression.check(dialog[0].episodesView.getHeight()>0,"No room for the episode list");
    });
   }finally{
    test.runOnMainSync(()->dialog[0].dismiss());
   }
  }
 }
}
