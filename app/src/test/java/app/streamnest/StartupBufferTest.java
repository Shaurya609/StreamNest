package app.streamnest;

import static org.junit.Assert.*;

import org.junit.Test;

public class StartupBufferTest {
  @Test
  public void eightyPercentDownloadedDoesNotReplaceMissingHeadersOrFooter() {
    assertFalse(StartupBuffer.ready(10 * 1024 * 1024, 0, 1024 * 1024, p -> p > 0 && p < 9));
    assertFalse(StartupBuffer.ready(10 * 1024 * 1024, 0, 1024 * 1024, p -> p < 9));
    assertTrue(StartupBuffer.ready(10 * 1024 * 1024, 0, 1024 * 1024, p -> p == 0 || p == 9));
  }

  @Test
  public void seasonPackMemberUsesUnalignedTorrentOffsetAndSmallFileBounds() {
    assertFalse(StartupBuffer.ready(100, 1020, 1024, p -> p == 0));
    assertTrue(StartupBuffer.ready(100, 1020, 1024, p -> p == 0 || p == 1));
    assertFalse(StartupBuffer.ready(0, 0, 1024, p -> true));
  }
}
