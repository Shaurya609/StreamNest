package app.streamnest;

/** Request-local notices: background refreshes must not change an unrelated catalog screen. */
final class CatalogStatus {
  private static final ThreadLocal<Boolean> saved = ThreadLocal.withInitial(() -> false);

  static void begin() {
    saved.set(false);
  }

  static void cached() {
    saved.set(true);
  }

  static String note() {
    return Boolean.TRUE.equals(saved.get()) ? " · Saved catalog · tap to refresh" : "";
  }
}
