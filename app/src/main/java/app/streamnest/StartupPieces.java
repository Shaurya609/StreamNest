package app.streamnest;
import java.util.*;
/** Header and footer first; the saved timestamp is only an approximate byte-priority hint. */
final class StartupPieces {
 static List<int[]> ranges(long size,long offset,int piece,double fraction){
  List<int[]> out=new ArrayList<>();if(size<=0||offset<0||piece<=0)return out;
  add(out,size,offset,piece,0,Math.min(size,1048576),0);add(out,size,offset,piece,Math.max(0,size-1048576),1048576,0);
  if(Double.isFinite(fraction)&&fraction>0&&fraction<0.98)add(out,size,offset,piece,(long)(size*fraction),4194304,1000);
  return out;
 }
 static void add(List<int[]> out,long size,long offset,int piece,long at,long bytes,int delay){int first=(int)((offset+at)/piece),last=(int)((offset+Math.min(size-1,at+bytes-1))/piece);if(out.stream().noneMatch(r->r[0]==first&&r[1]==last))out.add(new int[]{first,last,delay});}
}
