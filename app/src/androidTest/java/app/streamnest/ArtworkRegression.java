package app.streamnest;
import android.app.Instrumentation;import android.os.Bundle;import android.widget.ImageView;import org.json.JSONObject;
final class ArtworkRegression {
 static Bundle run(Instrumentation test)throws Exception{
  PosterLoader loader=new PosterLoader(test.getTargetContext());ImageView[] view=new ImageView[1];test.runOnMainSync(()->view[0]=new ImageView(test.getTargetContext()));
  try{
   Catalog.Title title=Catalog.parse(new JSONObject().put("imdb","tt11280740").put("title","Severance"),"shows");test.runOnMainSync(()->loader.load(title,view[0]));waitForImage(test,view[0]);
   title.raw.put("poster_big","https://streamnest-poster-test.invalid/missing.jpg");test.runOnMainSync(()->loader.load(title,view[0]));waitForImage(test,view[0]);
   if(!String.valueOf(view[0].getTag()).equals("https://images.metahub.space/poster/medium/tt11280740/img"))throw new AssertionError("Broken original did not fall back to the IMDb poster");
   Bundle result=new Bundle();result.putString("result","PASS: native poster with missing metadata; broken original retries cached canonical artwork");return result;
  }finally{loader.close();}
 }
 static void waitForImage(Instrumentation test,ImageView view)throws Exception{boolean[] ready={false};for(int n=0;n<80&&!ready[0];n++){Thread.sleep(200);test.runOnMainSync(()->ready[0]=view.getDrawable()!=null);}if(!ready[0])throw new AssertionError("Poster did not decode and attach to the native image view");}
}
