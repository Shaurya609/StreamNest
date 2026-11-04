package app.streamnest;

import java.lang.ref.WeakReference;
import java.util.*;
import java.util.function.Consumer;

final class CatalogUpdates {
  private static final List<WeakReference<Consumer<String>>> listeners = new ArrayList<>();

  static synchronized void add(Consumer<String> listener) {
    remove(listener);
    listeners.add(new WeakReference<>(listener));
  }

  static synchronized void remove(Consumer<String> listener) {
    listeners.removeIf(ref -> ref.get() == null || ref.get() == listener);
  }

  static void notify(String path) {
    List<Consumer<String>> copy = new ArrayList<>();
    synchronized (CatalogUpdates.class) {
      for (WeakReference<Consumer<String>> ref : listeners)
        if (ref.get() != null) copy.add(ref.get());
    }
    for (Consumer<String> listener : copy) listener.accept(path);
  }
}
