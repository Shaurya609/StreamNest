package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class BrowseStateTest {
  Catalog.Title title(String id) {
    Catalog.Title t = new Catalog.Title();
    t.id = id;
    t.kind = "shows";
    return t;
  }

  @Test
  public void appendRetainsEarlierItemsAndIgnoresDuplicates() {
    BrowseState s = new BrowseState();
    s.reset(1);
    assertTrue(s.begin(1, 1));
    assertFalse(s.begin(1, 1));
    s.commit(1, 1, Arrays.asList(title("tt1"), title("tt2")), true);
    assertTrue(s.begin(1, 2));
    s.commit(1, 2, Arrays.asList(title("tt2"), title("tt3")), true);
    assertEquals(3, s.rows.size());
    assertEquals("tt1", s.rows.get(0).id);
    assertEquals(2, s.page);
  }

  @Test
  public void filterResetRejectsOldResponsesWithoutDisturbingNewLoad() {
    BrowseState s = new BrowseState();
    s.reset(1);
    s.begin(1, 1);
    s.reset(2);
    s.begin(2, 1);
    assertFalse(s.commit(1, 1, Arrays.asList(title("wrong")), true));
    assertTrue(s.loading);
    assertTrue(s.commit(2, 1, Arrays.asList(title("correct")), true));
    assertEquals("correct", s.rows.get(0).id);
  }

  @Test
  public void retryDoesNotSkipPageAndEndDoesNotRefetch() {
    BrowseState s = new BrowseState();
    s.reset(1);
    s.begin(1, 1);
    s.commit(1, 1, Arrays.asList(title("tt1")), true);
    s.begin(1, 2);
    s.fail(1, 2);
    assertEquals(1, s.page);
    assertTrue(s.begin(1, 2));
    s.commit(1, 2, Collections.emptyList(), false);
    assertFalse(s.hasMore);
    assertFalse(s.begin(1, 3));
  }
}
