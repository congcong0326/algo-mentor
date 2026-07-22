package org.congcong.algomentor.cache.coherence;

public enum SharedInvalidationDispatchResult {
  SUCCESS,
  UNKNOWN_CACHE,
  SCHEMA_MISMATCH;

  public String tagValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
