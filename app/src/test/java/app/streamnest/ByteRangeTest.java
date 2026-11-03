package app.streamnest;

import static org.junit.Assert.*;

import org.junit.Test;

public class ByteRangeTest {
  @Test
  public void fullFile() {
    ByteRange r = ByteRange.parse(null, 1000);
    assertEquals(0, r.start);
    assertEquals(999, r.end);
    assertFalse(r.partial);
  }

  @Test
  public void seekRange() {
    ByteRange r = ByteRange.parse("bytes=200-399", 1000);
    assertEquals(200, r.start);
    assertEquals(399, r.end);
    assertTrue(r.partial);
  }

  @Test
  public void openEnd() {
    assertEquals(999, ByteRange.parse("bytes=900-", 1000).end);
  }

  @Test
  public void suffix() {
    ByteRange r = ByteRange.parse("bytes=-100", 1000);
    assertEquals(900, r.start);
    assertEquals(999, r.end);
  }

  @Test
  public void clipsEnd() {
    assertEquals(999, ByteRange.parse("bytes=900-9999", 1000).end);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsPastEnd() {
    ByteRange.parse("bytes=1000-", 1000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsReversed() {
    ByteRange.parse("bytes=400-200", 1000);
  }

  @Test(expected = IllegalArgumentException.class)
  public void rejectsMultiRange() {
    ByteRange.parse("bytes=1-3,7-9", 1000);
  }
}
