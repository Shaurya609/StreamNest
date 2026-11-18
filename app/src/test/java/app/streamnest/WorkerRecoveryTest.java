package app.streamnest;
import static org.junit.Assert.*;import org.junit.Test;
public class WorkerRecoveryTest {
 @Test public void nativeHangExpiresEvenWithFreshControlSnapshots(){assertEquals("Torrent startup timed out",WorkerRecovery.failure(46000000000L,0,45900000000L,0,true,true,0,45000));}
 @Test public void aBlockedNativePollCannotMasqueradeAsFreshProgress(){assertEquals("Torrent engine operation stalled",WorkerRecovery.failure(21000000000L,0,20900000000L,0,true,false,0,45000));}
 @Test public void healthyMetadataWaitAndStartupDoNotExpire(){assertEquals("",WorkerRecovery.failure(90000000000L,0,89900000000L,89800000000L,true,false,0,45000));assertEquals("",WorkerRecovery.failure(44000000000L,0,43900000000L,0,true,true,0,45000));}
 @Test public void deadBinderAndMissingHandshakeAreBounded(){assertEquals("Torrent worker stopped responding",WorkerRecovery.failure(11000000000L,0,0,10000000000L,true,false,0,45000));assertEquals("Torrent worker connection timed out",WorkerRecovery.failure(10000000000L,0,0,0,false,true,0,45000));}
}
