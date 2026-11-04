package app.streamnest;

final class StartupRecovery {
  static boolean reopen(long now, long opened, int retries, boolean failed) {
    return retries < 2 && (failed || now - opened >= 45000);
  }
}
