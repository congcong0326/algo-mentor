package org.congcong.algomentor.cache.metrics;

public enum CacheInvalidationType {
  KEY,
  ALL;

  public String tagValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
