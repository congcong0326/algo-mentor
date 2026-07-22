CREATE TABLE cache_invalidation_event (
  id BIGSERIAL PRIMARY KEY,
  cache_name VARCHAR(100) NOT NULL,
  namespace VARCHAR(100) NOT NULL,
  schema_version INTEGER NOT NULL,
  key_token VARCHAR(200) NULL,
  event_type VARCHAR(24) NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT ck_cache_invalidation_event_type
    CHECK (event_type IN ('KEY_INVALIDATE', 'REGION_INVALIDATE')),
  CONSTRAINT ck_cache_invalidation_event_key
    CHECK (
      (event_type = 'KEY_INVALIDATE' AND key_token IS NOT NULL)
      OR (event_type = 'REGION_INVALIDATE' AND key_token IS NULL)
    )
);

CREATE INDEX idx_cache_invalidation_event_created_at
  ON cache_invalidation_event(created_at);
