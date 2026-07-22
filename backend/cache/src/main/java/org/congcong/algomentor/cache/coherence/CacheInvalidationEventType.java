package org.congcong.algomentor.cache.coherence;

public enum CacheInvalidationEventType {
  KEY_INVALIDATE,
  REGION_INVALIDATE;

  public String tagValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
