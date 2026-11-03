package app.streamnest;

import static org.junit.Assert.*;

import org.junit.Test;

public class EpisodeRecoveryTest {
  @Test
  public void deadSourceGets45SecondsBeforeTryingAnother() {
    assertFalse(EpisodeRecovery.retry(44000, 0, 0, false));
    assertTrue(EpisodeRecovery.retry(45000, 0, 0, false));
  }

  @Test
  public void downloadingSourceGetsMoreTimeButCannotBlockForever() {
    assertFalse(EpisodeRecovery.retry(90000, 0, 89000, false));
    assertTrue(EpisodeRecovery.retry(180000, 0, 179000, false));
  }

  @Test
  public void playbackErrorCanTryNextImmediately() {
    assertTrue(EpisodeRecovery.retry(100, 0, 0, true));
  }
}
