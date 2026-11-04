package app.streamnest;

import static org.junit.Assert.*;

import org.junit.Test;

public class StartupRecoveryTest {
  @Test
  public void waitsForStartupButRetriesErrorsImmediately() {
    assertFalse(StartupRecovery.reopen(44999, 0, 0, false));
    assertTrue(StartupRecovery.reopen(45000, 0, 0, false));
    assertTrue(StartupRecovery.reopen(100, 0, 0, true));
  }

  @Test
  public void retriesSingleSourceOnlyTwice() {
    assertTrue(StartupRecovery.reopen(90000, 45000, 1, false));
    assertFalse(StartupRecovery.reopen(180000, 0, 2, true));
  }
}
