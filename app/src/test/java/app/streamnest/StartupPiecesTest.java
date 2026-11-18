package app.streamnest;
import static org.junit.Assert.*;import org.junit.Test;import java.util.*;
public class StartupPiecesTest {
 @Test public void prioritizesHeadersBeforeApproximateResumeInPack(){List<int[]> r=StartupPieces.ranges(20L*1048576,3L*1048576,1048576,0.5);assertEquals(3,r.size());assertArrayEquals(new int[]{3,3,0},r.get(0));assertArrayEquals(new int[]{22,22,0},r.get(1));assertArrayEquals(new int[]{13,16,1000},r.get(2));}
 @Test public void smallFileAndBoundaryRemainValid(){List<int[]> r=StartupPieces.ranges(100,1020,1024,0);assertEquals(1,r.size());assertArrayEquals(new int[]{0,1,0},r.get(0));assertTrue(StartupPieces.ranges(0,0,1024,0).isEmpty());}
}
