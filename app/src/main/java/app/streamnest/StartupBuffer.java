package app.streamnest;

import java.util.function.IntPredicate;

final class StartupBuffer {
  static boolean ready(long size, long offset, int pieceLength, IntPredicate present) {
    if (size <= 0 || offset < 0 || pieceLength <= 0) return false;
    long window = Math.min(size, 1024 * 1024);
    for (long edge : new long[] {0, size - window}) {
      int first = (int) ((offset + edge) / pieceLength);
      int last = (int) ((offset + edge + window - 1) / pieceLength);
      for (int piece = first; piece <= last; piece++) if (!present.test(piece)) return false;
    }
    return true;
  }
}
