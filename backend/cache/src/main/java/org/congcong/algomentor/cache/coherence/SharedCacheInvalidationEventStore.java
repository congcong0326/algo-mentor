package org.congcong.algomentor.cache.coherence;

import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;

/** Shared cache event persistence port. Implementations must join the caller's transaction. */
public interface SharedCacheInvalidationEventStore {

  void appendKeyInvalidation(SharedTtlCacheSpec specification, String keyToken);

  List<SharedCacheInvalidationEvent> findAfter(long cursor, int batchSize);

  long findHighWatermark();

  OptionalLong findLowestId();

  int deleteCreatedBefore(Instant cutoff);
}
