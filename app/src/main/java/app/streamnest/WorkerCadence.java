package app.streamnest;
/** Status production is 1Hz; keep faster startup feedback and slower speculative reports. */
final class WorkerCadence {
  static int delay(boolean startup, boolean preload) { return startup ? 500 : preload ? 2000 : 1000; }
}
