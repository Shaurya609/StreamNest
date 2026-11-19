package app.streamnest;
import java.util.*;import java.util.function.LongSupplier;import org.json.*;
/** Bounded local range state. Uploads contain percentages/counts, never paths or absolute positions. */
final class RangeTrace {
 final LongSupplier clock;final LinkedHashMap<Long,Read> reads=new LinkedHashMap<>();long sequence,requests;Read last;
 static final class Read{long id,pos,end,size,at,waitAt=-1,waitMs,served;String state="requested";}
 RangeTrace(LongSupplier clock){this.clock=clock;}
 synchronized long open(long start,long end,long size){Read r=new Read();r.id=++sequence;r.pos=start;r.end=end;r.size=size;r.at=clock.getAsLong();reads.put(r.id,r);last=r;requests++;while(reads.size()>8)reads.remove(reads.keySet().iterator().next());return r.id;}
 synchronized void waiting(long id,long pos){Read r=reads.get(id);if(r==null)return;long now=clock.getAsLong();if(r.waitAt<0)r.waitAt=now;r.pos=pos;r.at=now;r.state="waiting";last=r;}
 synchronized void state(long id,String state){Read r=reads.get(id);if(r==null)return;if(r.waitAt>=0){r.waitMs=Math.max(0,(clock.getAsLong()-r.waitAt)/1000000);r.waitAt=-1;}r.at=clock.getAsLong();r.state=state;last=r;}
 synchronized void served(long id,long pos,int bytes){Read r=reads.get(id);if(r==null)return;r.pos=pos;r.served+=bytes;r.at=clock.getAsLong();r.state="serving";last=r;}
 synchronized void close(long id){Read r=reads.remove(id);if(r!=null){if(r.waitAt>=0){r.waitMs=Math.max(0,(clock.getAsLong()-r.waitAt)/1000000);r.waitAt=-1;}if(!r.state.equals("timeout"))r.state="closed";r.at=clock.getAsLong();last=r;}}
 synchronized JSONObject snapshot(){JSONObject d=new JSONObject();try{Read r=reads.values().stream().max(Comparator.comparingLong(x->x.at)).orElse(last);d.put("rangeRequests",requests).put("rangeActiveReaders",reads.size());if(r!=null){long wait=r.waitAt<0?r.waitMs:Math.max(0,(clock.getAsLong()-r.waitAt)/1000000);d.put("rangeState",r.state).put("rangeWaitMs",wait).put("rangeStateAgeMs",Math.max(0,(clock.getAsLong()-r.at)/1000000)).put("rangeServedBytes",r.served).put("rangeStartPercent",percent(r.pos,r.size)).put("rangeEndPercent",percent(r.end,r.size));}}catch(JSONException ignored){}return d;}
 static int percent(long pos,long size){return size<=0?0:(int)Math.max(0,Math.min(100,(double)pos*100/size));}
}
