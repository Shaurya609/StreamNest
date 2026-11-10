package app.streamnest;
import android.app.*;
import android.os.*;
import org.json.*;
import java.util.*;
final class DiscoveryRegression {
 static Bundle run()throws Exception{
  JSONObject row=new JSONObject().put("season",1).put("episode",1).put("absolute",1).put("animeMapped",true);
  List<Catalog.Source> primary=AlternateEpisodes.find("tt7441658",1,1);
  if(primary.isEmpty())throw new Exception("Live indexed Black Clover episode missing");
  List<Catalog.Source> independent=NyaaEpisodeSources.find("Black Clover",row,System.nanoTime()+45000000000L);
  if(independent.isEmpty())throw new Exception("Live independent Black Clover episode missing");
  for(Catalog.Source source:independent)if(source.fileIndex<0||!EpisodeFiles.matches(source.filename,1,1,1))throw new Exception("Wrong batch member "+source.filename);
  Bundle result=new Bundle();result.putString("result","PASS: live Black Clover indexed="+primary.size()+" independent="+independent.size()+" exact="+independent.get(0).filename);return result;
 }
}
