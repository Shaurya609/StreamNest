package app.streamnest;
import static org.junit.Assert.*;
import org.junit.Test;
public class PreloadStorageTest {
  @Test public void reserveIsKeptAndHugeSizesCannotOverflow(){
    assertTrue(PreloadStorage.canFit(100,PreloadStorage.RESERVE+100));
    assertFalse(PreloadStorage.canFit(101,PreloadStorage.RESERVE+100));
    assertFalse(PreloadStorage.canFit(0,Long.MAX_VALUE));
    assertFalse(PreloadStorage.canFit(Long.MAX_VALUE,Long.MAX_VALUE));
    assertFalse(PreloadStorage.canFit(1,-1));
  }
}
