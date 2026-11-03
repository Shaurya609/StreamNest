package app.streamnest;

import static org.junit.Assert.*;

import java.io.*;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

public class CacheLocationTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void createsAndChecksChosenFolderWithoutTouchingOtherFiles() throws Exception {
    File parent = temp.newFolder("storage");
    File existing = new File(parent, "keep.txt");
    try (FileWriter w = new FileWriter(existing)) {
      w.write("keep");
    }
    File child = new File(parent, "cache");
    assertEquals(child.getCanonicalFile(), CacheLocation.writable(child));
    assertTrue(existing.isFile());
    assertEquals(0, child.list().length);
  }

  @Test(expected = IOException.class)
  public void rejectsRelativePaths() throws Exception {
    CacheLocation.writable(new File("relative/cache"));
  }

  @Test(expected = IOException.class)
  public void rejectsAFileAsAFolder() throws Exception {
    CacheLocation.writable(temp.newFile("not-a-directory"));
  }
}
