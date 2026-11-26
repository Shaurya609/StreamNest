package app.streamnest;

import static org.junit.Assert.*;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class UsbCacheProbeTest {
  @Rule public TemporaryFolder temporary = new TemporaryFolder();

  @Test public void randomAccessChecksBothOffsetsAndRemovesOnlyTheScratchFile() throws Exception {
    File folder = temporary.newFolder();
    File existing = new File(folder, "existing-video");
    byte[] content = {3, 6, 9};
    Files.write(existing.toPath(), content);
    List<String> phases = new ArrayList<>();
    UsbCacheProbe.run(folder, (phase, state, elapsed) -> {
      assertTrue(elapsed >= 0);
      if (state.equals("completed")) phases.add(phase);
    });
    assertEquals(List.of("create", "open", "size", "write", "sync", "read", "close", "cleanup"), phases);
    assertArrayEquals(content, Files.readAllBytes(existing.toPath()));
    assertArrayEquals(new String[] {"existing-video"}, folder.list());
  }

  @Test public void identifiesFailedCreateBeforeOtherOperations() throws Exception {
    List<String> phases = new ArrayList<>();
    try {
      UsbCacheProbe.run(temporary.newFile(), (phase, state, elapsed) -> phases.add(phase + ":" + state));
      fail("Non-directory should fail the USB check");
    } catch (IOException expected) {
      assertEquals(List.of("create:started", "create:failed"), phases);
    }
  }
}
