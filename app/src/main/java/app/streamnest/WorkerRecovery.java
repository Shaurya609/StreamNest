package app.streamnest;
/** All times are monotonic nanoseconds; snapshots and worker progress have independent budgets. */
final class WorkerRecovery {
 static String failure(long now,long begin,long snapshot,long pulse,boolean attached,boolean starting,long stage,long limitMs){
  if(!attached)return now-begin>=10000000000L?"Torrent worker connection timed out":"";
  if(now-snapshot>=10000000000L)return "Torrent worker stopped responding";
  if(starting&&now-stage>=limitMs*1000000L)return "Torrent startup timed out";
  if(!starting&&now-pulse>=20000000000L)return "Torrent engine operation stalled";
  return "";
 }
}
