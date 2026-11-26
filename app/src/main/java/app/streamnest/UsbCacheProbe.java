package app.streamnest;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

/** A small, disposable sample of the random access used by torrent storage. */
final class UsbCacheProbe {
  interface Reporter {
    void report(String phase, String state, long elapsedMs);
  }

  private static final class Trace {
    final Reporter reporter;
    String phase;
    long started;
    Trace(Reporter reporter) { this.reporter = reporter; }
    void start(String next) {
      phase = next;
      reporter.report(phase, "started", 0);
      started = System.nanoTime();
    }
    void completed() { reporter.report(phase, "completed", elapsed(started)); }
    void failed() { reporter.report(phase, "failed", elapsed(started)); }
  }

  private static final long FAR_OFFSET = 8L * 1024 * 1024;
  private static final int BLOCK_SIZE = 128 * 1024;

  static void run(File folder, Reporter reporter) throws IOException {
    byte[] first = new byte[BLOCK_SIZE];
    byte[] last = new byte[BLOCK_SIZE];
    SecureRandom random = new SecureRandom();
    random.nextBytes(first);
    random.nextBytes(last);
    File probe = null;
    Trace trace = new Trace(reporter);
    try {
      trace.start("create");
      probe = Files.createTempFile(folder.toPath(), ".streamnest-usb-", ".probe").toFile();
      trace.completed();
      trace.start("open");
      try (RandomAccessFile file = new RandomAccessFile(probe, "rw")) {
        trace.completed();
        trace.start("size");
        file.setLength(FAR_OFFSET + BLOCK_SIZE);
        trace.completed();
        trace.start("write");
        file.write(first);
        file.seek(FAR_OFFSET);
        file.write(last);
        trace.completed();
        trace.start("sync");
        file.getFD().sync();
        trace.completed();
        trace.start("read");
        verify(file, 0, first);
        verify(file, FAR_OFFSET, last);
        trace.completed();
        trace.start("close");
      }
      trace.completed();
    } catch (IOException | SecurityException error) {
      trace.failed();
      throw error;
    } finally {
      if (probe != null) {
        trace.start("cleanup");
        if (probe.delete()) trace.completed();
        else trace.failed();
      }
    }
  }

  private static void verify(RandomAccessFile file, long offset, byte[] expected) throws IOException {
    byte[] actual = new byte[expected.length];
    file.seek(offset);
    file.readFully(actual);
    if (!Arrays.equals(expected, actual)) throw new IOException("USB cache readback differed");
  }

  private static long elapsed(long started) {
    return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
  }
}
