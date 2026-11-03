package app.streamnest;

import java.util.*;

final class BrowseState {
  final List<Catalog.Title> rows = new ArrayList<>();
  final Set<String> ids = new HashSet<>();
  int revision, page, pending;
  boolean loading, hasMore = true, failed;

  void reset(int revision) {
    this.revision = revision;
    page = 0;
    pending = 0;
    loading = false;
    hasMore = true;
    failed = false;
    rows.clear();
    ids.clear();
  }

  boolean begin(int revision, int page) {
    if (revision != this.revision || loading || !hasMore || page != this.page + 1) return false;
    loading = true;
    failed = false;
    pending = page;
    return true;
  }

  boolean commit(int revision, int page, List<Catalog.Title> incoming, boolean more) {
    if (revision != this.revision || !loading || pending != page) return false;
    for (Catalog.Title t : incoming) if (ids.add(t.kind + ":" + t.id)) rows.add(t);
    this.page = page;
    hasMore = more && !incoming.isEmpty();
    loading = false;
    failed = false;
    return true;
  }

  boolean fail(int revision, int page) {
    if (revision != this.revision || pending != page) return false;
    loading = false;
    failed = true;
    return true;
  }
}
