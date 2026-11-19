package app.streamnest;

import java.util.BitSet;
import org.junit.Test;
import static org.junit.Assert.*;

public class PieceAvailabilityTest {
  @Test public void restoresCachedPiecesWithoutAssumingSparseFileIsDownloaded() {
    PieceAvailability p=new PieceAvailability();BitSet s=new BitSet();s.set(3);
    p.snapshot(s,p.generation());s.set(4);
    assertTrue(p.has(3));assertFalse(p.has(4));assertFalse(p.has(-1));
  }
  @Test public void staleSnapshotCannotUndoVerificationOrResurrectFailedHash() {
    PieceAvailability p=new PieceAvailability();long before=p.generation();
    p.finished(2);p.snapshot(new BitSet(),before);assertTrue(p.has(2));
    BitSet old=new BitSet();old.set(2);before=p.generation();p.failed(2);
    p.snapshot(old,before);assertFalse(p.has(2));
    p.snapshot(old,p.generation());assertTrue(p.has(2));
  }
  @Test public void startupRequiresVerifiedHeadAndTail() {
    PieceAvailability p=new PieceAvailability();p.finished(0);
    assertFalse(StartupBuffer.ready(4*1024*1024,0,1024*1024,p::has));
    p.finished(3);assertTrue(StartupBuffer.ready(4*1024*1024,0,1024*1024,p::has));
    p.failed(3);assertFalse(StartupBuffer.ready(4*1024*1024,0,1024*1024,p::has));
  }
}
