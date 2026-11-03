package app.streamnest;

final class ByteRange {
  final long start, end;
  final boolean partial;

  private ByteRange(long s, long e, boolean p) {
    start = s;
    end = e;
    partial = p;
  }

  static ByteRange parse(String header, long size) {
    if (size <= 0) throw new IllegalArgumentException("Empty file");
    if (header == null) return new ByteRange(0, size - 1, false);
    if (!header.startsWith("bytes=") || header.contains(","))
      throw new IllegalArgumentException("Unsupported range");
    String[] p = header.substring(6).split("-", -1);
    if (p.length != 2) throw new IllegalArgumentException("Bad range");
    long s, e;
    if (p[0].isEmpty()) {
      long suffix = Long.parseLong(p[1]);
      if (suffix <= 0) throw new IllegalArgumentException("Bad suffix");
      s = Math.max(0, size - suffix);
      e = size - 1;
    } else {
      s = Long.parseLong(p[0]);
      e = p[1].isEmpty() ? size - 1 : Math.min(size - 1, Long.parseLong(p[1]));
    }
    if (s < 0 || s >= size || e < s) throw new IllegalArgumentException("Range outside file");
    return new ByteRange(s, e, true);
  }
}
