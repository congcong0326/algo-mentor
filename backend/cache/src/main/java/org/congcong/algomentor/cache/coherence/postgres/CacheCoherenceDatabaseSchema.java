package org.congcong.algomentor.cache.coherence.postgres;

/** PostgreSQL shared cache coherence table and field names. */
final class CacheCoherenceDatabaseSchema {

  static final String EVENT_TABLE = "cache_invalidation_event";
  static final String COLUMN_ID = "id";
  static final String COLUMN_CACHE_NAME = "cache_name";
  static final String COLUMN_NAMESPACE = "namespace";
  static final String COLUMN_SCHEMA_VERSION = "schema_version";
  static final String COLUMN_KEY_TOKEN = "key_token";
  static final String COLUMN_EVENT_TYPE = "event_type";
  static final String COLUMN_CREATED_AT = "created_at";

  private CacheCoherenceDatabaseSchema() {
  }
}
