package app.streamnest;
import org.junit.Test;
import static org.junit.Assert.*;
public class WorkerIdentityTest {
 @Test public void onlyExactProcessAndAllUidFieldsMatch(){
   assertTrue(WorkerIdentity.matches("app.streamnest:torrent_a\0", "Name: worker\nUid:\t1001\t1001\t1001\t1001\n", "app.streamnest:torrent_a",1001));
   assertFalse(WorkerIdentity.matches("app.streamnest:torrent_b\0", "Uid: 1001 1001 1001 1001", "app.streamnest:torrent_a",1001));
   assertFalse(WorkerIdentity.matches("app.streamnest:torrent_a\0", "Uid: 1001 1002 1001 1001", "app.streamnest:torrent_a",1001));
   assertFalse(WorkerIdentity.matches("app.streamnest:torrent_a\0", "Uid: nope", "app.streamnest:torrent_a",1001));
   assertFalse(WorkerIdentity.matches("app.streamnest:torrent_a\0", "", "app.streamnest:torrent_a",1001));
 }
}
