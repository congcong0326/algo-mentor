package org.congcong.algomentor.cache.coherence.postgres;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import org.congcong.algomentor.cache.coherence.CacheInvalidationEventType;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEvent;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEventStore;
import org.congcong.algomentor.cache.coherence.SharedCacheRegionDescriptor;
import org.congcong.algomentor.cache.config.CacheProperties;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.registry.SharedCacheInvalidationTargetRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.junit.jupiter.api.Test;

class PostgresSharedInvalidationPollerTest {

  @Test
  void dispatchesExactKeysAndClearsAllRegionsWhenItsCursorHasARetentionGap() {
    InMemoryEventStore eventStore = new InMemoryEventStore();
    SharedCacheInvalidationTargetRegistry targets = new SharedCacheInvalidationTargetRegistry();
    RecordingTarget target = new RecordingTarget();
    targets.register(target);
    CacheProperties.Coherence properties = new CacheProperties.Coherence();
    properties.setBatchSize(10);
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    try {
      PostgresSharedInvalidationPoller poller = new PostgresSharedInvalidationPoller(
          eventStore,
          targets,
          CacheCoherenceMetrics.noop(),
          properties,
          Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC),
          executor);

      poller.pollOnce();
      eventStore.add(event(1, "user-42", 1));
      poller.pollOnce();

      assertThat(target.invalidatedKeyTokens).containsExactly("user-42");
      assertThat(target.regionInvalidations).isZero();
      assertThat(poller.cursor()).isEqualTo(1);

      eventStore.replaceWith(event(5, "user-99", 1));
      poller.pollOnce();

      assertThat(target.regionInvalidations).isEqualTo(1);
      assertThat(target.invalidatedKeyTokens).containsExactly("user-42");
      assertThat(poller.cursor()).isEqualTo(5);
    } finally {
      executor.shutdownNow();
    }
  }

  @Test
  void skipsSchemaMismatchesWithoutInvalidatingTheRegisteredRegion() {
    InMemoryEventStore eventStore = new InMemoryEventStore();
    SharedCacheInvalidationTargetRegistry targets = new SharedCacheInvalidationTargetRegistry();
    RecordingTarget target = new RecordingTarget();
    targets.register(target);
    ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    try {
      PostgresSharedInvalidationPoller poller = new PostgresSharedInvalidationPoller(
          eventStore, targets, CacheCoherenceMetrics.noop(), new CacheProperties.Coherence(),
          Clock.systemUTC(), executor);
      poller.pollOnce();
      eventStore.add(event(1, "user-42", 2));

      poller.pollOnce();

      assertThat(target.invalidatedKeyTokens).isEmpty();
      assertThat(target.regionInvalidations).isZero();
      assertThat(poller.cursor()).isEqualTo(1);
    } finally {
      executor.shutdownNow();
    }
  }

  private static SharedCacheInvalidationEvent event(long id, String keyToken, int schemaVersion) {
    return new SharedCacheInvalidationEvent(
        id,
        new CacheRegionName("shared-poller-cache"),
        "shared-poller",
        schemaVersion,
        keyToken,
        CacheInvalidationEventType.KEY_INVALIDATE,
        Instant.parse("2026-07-22T00:00:00Z"));
  }

  private static final class RecordingTarget implements SharedCacheRegionDescriptor<String, String> {
    private final SharedTtlCacheSpec specification = new SharedTtlCacheSpec(
        new CacheRegionName("shared-poller-cache"), "shared-poller", 1, 10, Duration.ofMinutes(1));
    private final List<String> invalidatedKeyTokens = new ArrayList<>();
    private int regionInvalidations;

    @Override
    public SharedTtlCacheSpec specification() {
      return specification;
    }

    @Override
    public String encodeKey(String key) {
      return key;
    }

    @Override
    public void invalidateKeyToken(String keyToken) {
      invalidatedKeyTokens.add(keyToken);
    }

    @Override
    public void invalidateAllLocal() {
      regionInvalidations++;
    }
  }

  private static final class InMemoryEventStore implements SharedCacheInvalidationEventStore {
    private final List<SharedCacheInvalidationEvent> events = new ArrayList<>();

    @Override
    public void appendKeyInvalidation(SharedTtlCacheSpec specification, String keyToken) {
      long id = events.stream().mapToLong(SharedCacheInvalidationEvent::id).max().orElse(0) + 1;
      add(new SharedCacheInvalidationEvent(
          id,
          specification.name(),
          specification.namespace(),
          specification.schemaVersion(),
          keyToken,
          CacheInvalidationEventType.KEY_INVALIDATE,
          Instant.now()));
    }

    @Override
    public List<SharedCacheInvalidationEvent> findAfter(long cursor, int batchSize) {
      return events.stream()
          .filter(event -> event.id() > cursor)
          .sorted(Comparator.comparingLong(SharedCacheInvalidationEvent::id))
          .limit(batchSize)
          .toList();
    }

    @Override
    public long findHighWatermark() {
      return events.stream().mapToLong(SharedCacheInvalidationEvent::id).max().orElse(0);
    }

    @Override
    public OptionalLong findLowestId() {
      return events.stream().mapToLong(SharedCacheInvalidationEvent::id).min();
    }

    @Override
    public int deleteCreatedBefore(Instant cutoff) {
      int before = events.size();
      events.removeIf(event -> event.createdAt().isBefore(cutoff));
      return before - events.size();
    }

    void add(SharedCacheInvalidationEvent event) {
      events.add(event);
    }

    void replaceWith(SharedCacheInvalidationEvent event) {
      events.clear();
      events.add(event);
    }
  }
}
