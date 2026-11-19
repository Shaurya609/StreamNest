package app.streamnest;
/** Limits disk workers, not playback features or peer discovery, on low-memory devices. */
final class EngineBudget {
  static int diskThreads(boolean lite, boolean preload) { return lite ? 2 : preload ? 4 : 10; }
}
