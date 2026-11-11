package app.streamnest;
import org.libtorrent4j.SessionManager;
/** Guard the pinned library's session=null / alertsLoop.pop_alerts shutdown race only. */
final class SafeTorrentSession extends SessionManager {
 volatile boolean stopping,shutdownGuardInstalled;
 @Override protected void onAfterStart(){super.onAfterStart();try{java.lang.reflect.Field f=SessionManager.class.getDeclaredField("alertsLoop");f.setAccessible(true);Thread loop=(Thread)f.get(this);Thread.UncaughtExceptionHandler previous=loop.getUncaughtExceptionHandler();loop.setUncaughtExceptionHandler((thread,error)->{if(stopping&&EngineStartup.shutdownRace(error)){android.util.Log.w("StreamNest","Torrent alert loop finished during session shutdown");return;}previous.uncaughtException(thread,error);});shutdownGuardInstalled=true;}catch(ReflectiveOperationException e){throw new IllegalStateException("Torrent shutdown guard unavailable",e);}}
 @Override public synchronized void stop(){stopping=true;try{super.stop();}finally{stopping=false;}}
}
