package app.streamnest;
/** Container clocks can stop a few milliseconds short without delivering EndReached. */
final class EpisodeCompletion {
 long position=-1,since;boolean fired;
 void reset(){position=-1;since=0;fired=false;}
 boolean sample(long now,long at,long length,boolean eligible){
  if(!eligible||length<10000||at<0||at>length+1000||length-at>100){position=at;since=now;fired=false;return false;}
  if(position<0||Math.abs(at-position)>20){position=at;since=now;fired=false;return false;}
  position=at;if(!fired&&now-since>=3000){fired=true;return true;}return false;
 }
}
