package app.streamnest;

import static org.junit.Assert.*;

import java.io.*;
import java.nio.file.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class CachePurgerTest {
  @Rule public TemporaryFolder temp = new TemporaryFolder();

  @Test
  public void removesRecordedVideoAndResumeFilesButPreservesOtherEntries() throws Exception {
    File root = temp.newFolder("cache");
    File video = new File(root, "123abc");
    assertTrue(new File(video, "nested").mkdirs());
    Files.write(new File(video, "nested/video.mkv").toPath(), new byte[] {1});
    Files.write(new File(video, "resume.dat").toPath(), new byte[] {2});
    File other = new File(root, "personal-file.txt");
    Files.write(other.toPath(), new byte[] {3});
    CachePurger.deleteEntry(root, video);
    assertFalse(video.exists());
    assertTrue(other.exists());
    CachePurger.deleteEntry(root, video); // safe to retry
  }

  @Test
  public void rejectsRootAndEntriesOutsideCache() throws Exception {
    File root = temp.newFolder("cache");
    File outside = temp.newFile("keep.txt");
    for (File target : new File[] {root, outside}) {
      try {
        CachePurger.deleteEntry(root, target);
        fail("Must reject outside entry");
      } catch (IOException expected) {
      }
    }
    assertTrue(root.exists());
    assertTrue(outside.exists());
  }
}
