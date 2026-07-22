package org.congcong.algomentor.cache.coherence.postgres;

import static org.congcong.algomentor.cache.coherence.postgres.CacheCoherenceDatabaseSchema.COLUMN_CACHE_NAME;
import static org.congcong.algomentor.cache.coherence.postgres.CacheCoherenceDatabaseSchema.COLUMN_CREATED_AT;
import static org.congcong.algomentor.cache.coherence.postgres.CacheCoherenceDatabaseSchema.COLUMN_EVENT_TYPE;
import static org.congcong.algomentor.cache.coherence.postgres.CacheCoherenceDatabaseSchema.COLUMN_ID;
import static org.congcong.algomentor.cache.coherence.postgres.CacheCoherenceDatabaseSchema.COLUMN_KEY_TOKEN;
import static org.congcong.algomentor.cache.coherence.postgres.CacheCoherenceDatabaseSchema.COLUMN_NAMESPACE;
import static org.congcong.algomentor.cache.coherence.postgres.CacheCoherenceDatabaseSchema.COLUMN_SCHEMA_VERSION;
import static org.congcong.algomentor.cache.coherence.postgres.CacheCoherenceDatabaseSchema.EVENT_TABLE;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import org.congcong.algomentor.cache.coherence.CacheInvalidationEventType;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEvent;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEventStore;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

/** Spring JDBC implementation that participates in the caller's active transaction. */
public final class JdbcSharedCacheInvalidationEventStore implements SharedCacheInvalidationEventStore {

  private static final String INSERT_KEY_EVENT = "INSERT INTO " + EVENT_TABLE + " ("
      + COLUMN_CACHE_NAME + ", " + COLUMN_NAMESPACE + ", " + COLUMN_SCHEMA_VERSION + ", "
      + COLUMN_KEY_TOKEN + ", " + COLUMN_EVENT_TYPE + ") VALUES (?, ?, ?, ?, ?)";
  private static final String SELECT_AFTER = "SELECT "
      + COLUMN_ID + ", " + COLUMN_CACHE_NAME + ", " + COLUMN_NAMESPACE + ", "
      + COLUMN_SCHEMA_VERSION + ", " + COLUMN_KEY_TOKEN + ", " + COLUMN_EVENT_TYPE + ", "
      + COLUMN_CREATED_AT + " FROM " + EVENT_TABLE + " WHERE " + COLUMN_ID + " > ? ORDER BY "
      + COLUMN_ID + " ASC LIMIT ?";
  private static final String SELECT_MAX_ID = "SELECT COALESCE(MAX(" + COLUMN_ID + "), 0) FROM "
      + EVENT_TABLE;
  private static final String SELECT_MIN_ID = "SELECT MIN(" + COLUMN_ID + ") FROM " + EVENT_TABLE;
  private static final String DELETE_EXPIRED = "DELETE FROM " + EVENT_TABLE + " WHERE "
      + COLUMN_CREATED_AT + " < ?";

  private final JdbcTemplate jdbcTemplate;

  public JdbcSharedCacheInvalidationEventStore(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
  }

  @Override
  public void appendKeyInvalidation(SharedTtlCacheSpec specification, String keyToken) {
    Objects.requireNonNull(specification, "specification must not be null");
    String token = SharedCacheKeyCodec.requireValidToken(keyToken);
    jdbcTemplate.update(INSERT_KEY_EVENT,
        specification.name().value(),
        specification.namespace(),
        specification.schemaVersion(),
        token,
        CacheInvalidationEventType.KEY_INVALIDATE.name());
  }

  @Override
  public List<SharedCacheInvalidationEvent> findAfter(long cursor, int batchSize) {
    if (cursor < 0) {
      throw new IllegalArgumentException("cursor must not be negative");
    }
    if (batchSize <= 0) {
      throw new IllegalArgumentException("batchSize must be greater than zero");
    }
    return jdbcTemplate.query(SELECT_AFTER, rowMapper(), cursor, batchSize);
  }

  @Override
  public long findHighWatermark() {
    Long maximum = jdbcTemplate.queryForObject(SELECT_MAX_ID, Long.class);
    return maximum == null ? 0 : maximum;
  }

  @Override
  public OptionalLong findLowestId() {
    Long minimum = jdbcTemplate.queryForObject(SELECT_MIN_ID, Long.class);
    return minimum == null ? OptionalLong.empty() : OptionalLong.of(minimum);
  }

  @Override
  public int deleteCreatedBefore(Instant cutoff) {
    Instant nonNullCutoff = Objects.requireNonNull(cutoff, "cutoff must not be null");
    return jdbcTemplate.update(
        DELETE_EXPIRED,
        new Object[] {nonNullCutoff.atOffset(ZoneOffset.UTC)},
        new int[] {Types.TIMESTAMP_WITH_TIMEZONE});
  }

  private static RowMapper<SharedCacheInvalidationEvent> rowMapper() {
    return JdbcSharedCacheInvalidationEventStore::mapEvent;
  }

  private static SharedCacheInvalidationEvent mapEvent(ResultSet resultSet, int rowNum) throws SQLException {
    return new SharedCacheInvalidationEvent(
        resultSet.getLong(COLUMN_ID),
        new CacheRegionName(resultSet.getString(COLUMN_CACHE_NAME)),
        resultSet.getString(COLUMN_NAMESPACE),
        resultSet.getInt(COLUMN_SCHEMA_VERSION),
        resultSet.getString(COLUMN_KEY_TOKEN),
        CacheInvalidationEventType.valueOf(resultSet.getString(COLUMN_EVENT_TYPE)),
        resultSet.getTimestamp(COLUMN_CREATED_AT).toInstant());
  }
}
