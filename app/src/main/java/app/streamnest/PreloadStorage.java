package app.streamnest;
final class PreloadStorage {
  static final long RESERVE=256L*1024*1024;
  static boolean canFit(long size,long free){return size>0&&free>=RESERVE&&size<=free-RESERVE;}
}
