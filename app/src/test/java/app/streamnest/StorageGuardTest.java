package app.streamnest;
import org.junit.Test;
import static org.junit.Assert.*;
public class StorageGuardTest {
 @Test public void usbSpaceDoesNotHideFullInternalStorage(){assertTrue(StorageGuard.problem(98*StorageGuard.MB,14933*StorageGuard.MB,true).contains("internal"));}
 @Test public void reservesStopBeforeRecordsAndVideoCannotBeWritten(){assertFalse(StorageGuard.problem(127*StorageGuard.MB,14933*StorageGuard.MB,false).isEmpty());assertFalse(StorageGuard.problem(500*StorageGuard.MB,63*StorageGuard.MB,false).isEmpty());assertEquals("",StorageGuard.problem(256*StorageGuard.MB,256*StorageGuard.MB,true));}
 @Test public void startupAndRunningBudgetsAreDistinct(){assertFalse(StorageGuard.problem(200*StorageGuard.MB,200*StorageGuard.MB,true).isEmpty());assertEquals("",StorageGuard.problem(200*StorageGuard.MB,200*StorageGuard.MB,false));}
 @Test public void onlyTypedStorageFailuresDisableSourceRecovery(){assertTrue(StorageGuard.isStorageFailure(new java.io.IOException(new StorageGuard.StorageException("safe"))));assertFalse(StorageGuard.isStorageFailure(new java.io.IOException("network")));}
}
