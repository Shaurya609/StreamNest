package app.streamnest;

final class EpisodeRecovery {
  static boolean exhausted(boolean mediaSet,boolean played,boolean paused,boolean lastSource,boolean starting,boolean blocked,long now,long started,long progress,boolean failed){
    return !mediaSet&&!played&&!paused&&lastSource&&!starting&&!blocked&&retry(now,started,progress,failed);
  }
  static boolean retry(long now, long started, long lastProgress, boolean failed) {
    return failed || now - lastProgress >= 45000 || now - started >= 180000;
  }
}
