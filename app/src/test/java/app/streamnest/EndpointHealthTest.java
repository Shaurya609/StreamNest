package app.streamnest;

import static org.junit.Assert.*;

import java.util.*;
import org.junit.Test;

public class EndpointHealthTest {
  @Test
  public void failedHostsCoolDownAndRecoverWithoutHammering() {
    long[] now = {1000};
    EndpointHealth health = new EndpointHealth(() -> now[0]);
    List<String> hosts = Arrays.asList("a", "b");
    health.failed("a");
    assertEquals(Collections.singletonList("b"), health.candidates(hosts, "a"));
    now[0] += 30001;
    assertEquals("a", health.candidates(hosts, "a").get(0));
    health.succeeded("a");
    health.failed("a");
    now[0] += 30001;
    assertTrue(health.candidates(hosts, "a").contains("a"));
  }

  @Test
  public void allCoolingHostsStaySkippedUntilRetryWindow() {
    long[] now = {0};
    EndpointHealth health = new EndpointHealth(() -> now[0]);
    List<String> hosts = Arrays.asList("a", "b");
    health.failed("a");
    health.failed("b");
    assertTrue(health.candidates(hosts, "a").isEmpty());
    health.reset();
    assertEquals(2, health.candidates(hosts, "a").size());
  }
}
