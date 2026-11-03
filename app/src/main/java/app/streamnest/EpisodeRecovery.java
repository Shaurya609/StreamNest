package app.streamnest;

final class EpisodeRecovery {
  static boolean retry(long now, long started, long lastProgress, boolean failed) {
    return failed || now - lastProgress >= 45000 || now - started >= 180000;
  }
}
