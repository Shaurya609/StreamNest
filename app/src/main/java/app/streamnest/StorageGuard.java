package app.streamnest;
import android.content.Context;
import java.io.*;

/** Video on USB still needs writable internal app state. No credentials/history are purged. */
final class StorageGuard {
 static final long MB=1048576,START_RESERVE=256*MB,INTERNAL_STOP=128*MB,CACHE_STOP=64*MB;
 static volatile long internalFreeMb=-1,cacheFreeMb=-1;
 static class StorageException extends IOException {StorageException(String message){super(message);}}
 static String problem(long internal,long cache,boolean starting){
  if(internal<(starting?START_RESERVE:INTERNAL_STOP))return "Projector/device internal storage is nearly full. USB video storage still needs internal space for app records. Stop playback and clear old StreamNest video cache in Playback & storage, then retry.";
  if(cache<(starting?START_RESERVE:CACHE_STOP))return "The selected video cache is nearly full or unavailable. Stop playback, free space or choose a connected USB folder in Playback & storage, then retry.";
  return "";
 }
 static void check(Context c,File cache,boolean starting)throws IOException{
  long internal=CacheLocation.freeSpace(c.getFilesDir()),free=CacheLocation.freeSpace(cache);String issue=problem(internal,free,starting);
  internalFreeMb=internal/MB;cacheFreeMb=free/MB;
  if(!issue.isEmpty()){
   try{Diagnostics.event("cache.failed",new org.json.JSONObject().put("service","storage").put("operation","cache").put("reason","storage").put("cacheFreeMb",free/MB).put("internalFreeMb",internal/MB));}catch(Exception ignored){}
   throw new StorageException(issue);
  }
 }
 static boolean isStorageFailure(Throwable error){for(int i=0;i<5&&error!=null;i++,error=error.getCause())if(error instanceof StorageException)return true;return false;}
}
