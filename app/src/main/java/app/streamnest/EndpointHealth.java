package app.streamnest;

import java.util.*;
import java.util.function.LongSupplier;

/** Small circuit breaker shared by compatible endpoint adapters. */
final class EndpointHealth {
  private static final class Failure {
    int count;
    long retryAt;
  }

  private final Map<String, Failure> failures = new HashMap<>();
  final LongSupplier clock;

  EndpointHealth() {
    this(System::currentTimeMillis);
  }

  EndpointHealth(LongSupplier clock) {
    this.clock = clock;
  }

  synchronized List<String> candidates(List<String> hosts, String preferred) {
    List<String> ready = new ArrayList<>();
    long now = clock.getAsLong();
    for (String host : hosts) {
      Failure f = failures.get(host);
      if (f == null || f.retryAt <= now) ready.add(host);
    }
    if (ready.remove(preferred)) ready.add(0, preferred);
    return ready;
  }

  synchronized void failed(String host) {
    Failure f = failures.computeIfAbsent(host, k -> new Failure());
    f.count = Math.min(5, f.count + 1);
    f.retryAt = clock.getAsLong() + Math.min(300000L, 15000L * (1L << f.count));
  }

  synchronized void succeeded(String host) {
    failures.remove(host);
  }

  synchronized void reset() {
    failures.clear();
  }
}
