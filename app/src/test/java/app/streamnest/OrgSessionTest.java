package app.streamnest;

import static org.junit.Assert.*;

import java.io.IOException;
import java.util.*;
import org.junit.Test;

public class OrgSessionTest {
  static class Memory implements OrgSubtitles.Store {
    Map<String, String> values = new HashMap<>();

    public String get(String key) {
      return values.getOrDefault(key, "");
    }

    public void put(String key, String value) {
      values.put(key, value);
    }
  }

  static class Service extends OrgSubtitles {
    int logins, requests;
    boolean reject, fail;
    List<String> used = new ArrayList<>();

    Service(Memory store) {
      super(store);
    }

    @Override
    Map<String, Object> rpc(String method, Object... args) throws Exception {
      Map<String, Object> out = new HashMap<>();
      if (method.equals("LogIn")) {
        logins++;
        out.put("token", "token" + logins);
      } else if (!method.equals("LogOut")) {
        requests++;
        used.add(String.valueOf(args[0]));
        if (fail || reject && requests == 1)
          throw new IOException("OpenSubtitles.org: 406 No session");
        out.put("data", Collections.emptyList());
      }
      return out;
    }
  }

  @Test
  public void launchRenewsSelectedAnonymousModeButNotSignedOutMode() throws Exception {
    Memory store = new Memory();
    Service api = new Service(store);
    api.ensureAnonymous(true);
    assertEquals(0, api.logins);
    api.anonymous();
    api.ensureAnonymous(true);
    assertEquals(2, api.logins);
    api.logout();
    api.ensureAnonymous(true);
    assertEquals(2, api.logins);
    assertFalse(api.signedIn());
  }

  @Test
  public void searchReauthenticatesOnceAfterServerExpiresSession() throws Exception {
    Memory store = new Memory();
    Service api = new Service(store);
    api.anonymous();
    api.reject = true;
    assertTrue(api.search("tt123", 0, 0, "Title", "en", "").isEmpty());
    assertEquals(Arrays.asList("token1", "token2"), api.used);
    assertEquals(2, api.logins);
  }

  @Test
  public void expiredDownloadSessionRenewsAndPersistentFailureIsBounded() throws Exception {
    Memory store = new Memory();
    Service api = new Service(store);
    api.anonymous();
    store.put("orgAnonymousUntil", "0");
    api.sessionRpc("DownloadSubtitles", Collections.singletonList("1"));
    assertEquals(2, api.logins);
    assertEquals("token2", api.used.get(0));
    api.fail = true;
    try {
      api.sessionRpc("DownloadSubtitles", Collections.singletonList("1"));
      fail("Expected failure");
    } catch (IOException expected) {
      assertEquals(3, api.logins);
      assertEquals(3, api.requests);
    }
  }

  @Test
  public void missingAnonymousTokenStillCountsAsConfiguredForRecovery() throws Exception {
    Memory store = new Memory();
    store.put("orgAnonymous", "true");
    Service api = new Service(store);
    assertTrue(api.signedIn());
    api.ensureAnonymous(false);
    assertEquals(1, api.logins);
  }
}
