package app.streamnest;
import android.app.*;import android.content.*;import android.os.*;import android.view.*;import org.json.*;import java.io.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Disposable paired group only. Input/output stay in the verification package's cache. */
final class GroupHomeRegression {
 static Bundle run(Instrumentation test)throws Exception{
  Context c=test.getTargetContext();JSONObject input=new JSONObject(new String(java.nio.file.Files.readAllBytes(new File(c.getCacheDir(),"group-home-input.json").toPath()),StandardCharsets.UTF_8));
  String code=input.getString("code");WatchSync.pending=true;WatchSync.testPairingCode=code;WatchProgress.preferenceName="group-home-verification";c.getSharedPreferences(WatchProgress.preferenceName,0).edit().clear().commit();
  try{
   WatchSync.sync(c,code);JSONObject home=HomeSync.cached(c);HomeRegression.check(home!=null,"Shared Home missing");HomeRegression.check(home.getString("history").equals(input.getJSONObject("home").getString("history")),"Desktop history signature differs");
   HomeRegression.check(home.getJSONArray("rows").toString().equals(input.getJSONObject("home").getJSONArray("rows").toString()),"Desktop recommendation rows differ");
   // Test the real Home renderer with only synced recommendation metadata available.
   HomeLibrary.cached=new ArrayList<>();HomeLibrary.cachedAt=System.currentTimeMillis();Intent intent=new Intent(c,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TASK);MainActivity a=(MainActivity)test.startActivitySync(intent);
   String first=home.getJSONArray("rows").getJSONObject(0).getJSONArray("ids").getString(0),name=home.getJSONObject("titles").getJSONObject(first).getString("title");boolean[] visible={false};
   for(int n=0;n<80&&!visible[0];n++){Thread.sleep(250);test.runOnMainSync(()->{for(View v:HomeRegression.collect(a.content))if(v.getContentDescription()!=null&&v.getContentDescription().toString().startsWith(name+" ·"))visible[0]=true;});}HomeRegression.check(visible[0],"Shared recommendation missing in native Home");
   String watched=input.getString("watchNext");WatchProgress.merge(c,new JSONObject().put("movie:"+watched,new JSONObject().put("positionMs",30000).put("durationMs",120000).put("updatedAt",System.currentTimeMillis())));
   HomeLibrary.clear();WatchSync.sync(c,code);JSONObject after=HomeSync.cached(c);for(int i=0;i<after.getJSONArray("rows").length();i++)HomeRegression.check(!after.getJSONArray("rows").getJSONObject(i).getJSONArray("ids").toString().contains("\""+watched+"\""),"Watched title remains recommended");
   boolean[] refreshed={false};for(int n=0;n<80&&!refreshed[0];n++){Thread.sleep(250);test.runOnMainSync(()->{refreshed[0]=true;for(View v:HomeRegression.collect(a.content))if(v.getContentDescription()!=null&&v.getContentDescription().toString().equals(name+" · Movie"))refreshed[0]=false;});}HomeRegression.check(refreshed[0],"Visible Home did not react to changed recommendations");
   try(FileOutputStream out=new FileOutputStream(new File(c.getCacheDir(),"group-home-output.json"))){out.write(after.toString().getBytes(StandardCharsets.UTF_8));}
   WatchSync.testPairingCode=WatchSyncCrypto.create();HomeRegression.check(HomeSync.cached(c)==null,"Recommendations leaked into another group");
   test.runOnMainSync(a::finish);Bundle result=new Bundle();result.putString("result","PASS: desktop-to-native Home rows/UI; native watch updates shared recommendations; group isolation");return result;
  }finally{WatchSync.pending=false;WatchSync.testPairingCode=null;HomeLibrary.clear();}
 }
}
