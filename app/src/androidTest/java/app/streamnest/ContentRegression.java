package app.streamnest;
import android.app.*;import android.os.*;import java.util.*;import org.json.*;
final class ContentRegression {
 static Bundle run(Instrumentation test)throws Exception {
  Bundle result=new Bundle();
  for(String kind:new String[]{"anime","series"}){
   boolean anime=kind.equals("anime");List<SkipSegments.Segment> rows=SkipSegments.load(anime?"tt7441658":"tt11280740",1,anime?108:1,anime?34572:0,anime?1440000:3435000);
   if(rows.isEmpty())throw new AssertionError(kind+" has no validated live segments");
   for(SkipSegments.Segment row:rows)if(SkipSegments.active(rows,row.start+1000)==null)throw new AssertionError("Inactive validated segment");result.putInt(kind+"SkipSegments",rows.size());
  }
  Map<String,String> store=new HashMap<>();OrgSubtitles api=new OrgSubtitles(new OrgSubtitles.Store(){public String get(String key){return store.getOrDefault(key,"");}public void put(String key,String value){store.put(key,value);}});
  List<SubtitleQuery.Result> rows=api.search("tt10919420",1,1,"Squid Game","en","",0);if(rows.isEmpty())throw new AssertionError("No Korean-series English subtitles");
  java.io.File file=api.download(rows.get(0),test.getTargetContext().getCacheDir());if(!new String(java.nio.file.Files.readAllBytes(file.toPath()),java.nio.charset.StandardCharsets.UTF_8).contains("-->"))throw new AssertionError("No timed subtitle cues");result.putInt("koreanEnglishSubtitles",rows.size());
  result.putString("result","PASS: native anonymous anime/series timestamp selection and Korean English subtitle download; actual English dub not asserted");return result;
 }
}
