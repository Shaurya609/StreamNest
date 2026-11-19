package app.streamnest;

import java.util.BitSet;

/** Local, verified-piece view. A concurrent alert invalidates an older status snapshot. */
final class PieceAvailability {
  private BitSet pieces = new BitSet();
  private long generation;
  synchronized long generation() { return generation; }
  synchronized boolean has(int piece) { return piece >= 0 && pieces.get(piece); }
  synchronized void finished(int piece) { if(piece >= 0) { pieces.set(piece); generation++; } }
  synchronized void failed(int piece) { if(piece >= 0) { pieces.clear(piece); generation++; } }
  synchronized void snapshot(BitSet verified, long before) {
    if(generation == before) pieces = (BitSet)verified.clone();
  }
}
