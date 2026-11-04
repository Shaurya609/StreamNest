package app.streamnest;

import static org.junit.Assert.*;

import java.io.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class FolderPolicyTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void acceptsRootAndNestedFoldersButRejectsSiblingAndTraversal() throws Exception {
    File root = temp.newFolder("drive");
    assertTrue(FolderPolicy.inside(root, root));
    assertTrue(FolderPolicy.inside(root, new File(root, "Shows/cache")));
    assertFalse(FolderPolicy.inside(root, new File(root, "../drive2")));
    for (String name : new String[] {"..", "../other", "A/B", "A\\B", ""}) {
      try {
        FolderPolicy.child(root, name);
        fail("Invalid name accepted");
      } catch (IOException expected) {
      }
    }
    assertEquals(new File(root, "My cache"), FolderPolicy.child(root, "My cache"));
  }
}
