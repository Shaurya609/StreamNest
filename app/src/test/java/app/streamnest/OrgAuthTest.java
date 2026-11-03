package app.streamnest;

import static org.junit.Assert.*;

import org.junit.Test;

public class OrgAuthTest {
  @Test
  public void matchesDesktopMd5PasswordFormat() {
    assertEquals("5f4dcc3b5aa765d61d8327deb882cf99", OrgAuth.digest("password"));
  }

  @Test
  public void preservesSignificantPasswordSpaces() {
    assertNotEquals(OrgAuth.digest("password"), OrgAuth.digest(" password "));
  }

  @Test
  public void rejectsAreNotReportedAsExpiredSessions() {
    assertTrue(OrgAuth.loginError("401 Unauthorized").contains("API login"));
    assertFalse(OrgAuth.loginError("401 Unauthorized").contains("session expired"));
  }
}
