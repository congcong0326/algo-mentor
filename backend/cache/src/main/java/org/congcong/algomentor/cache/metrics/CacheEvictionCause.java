package org.congcong.algomentor.cache.metrics;

public enum CacheEvictionCause {
  COLLECTED,
  EXPLICIT,
  EXPIRED,
  REPLACED,
  SIZE;

  public String tagValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
