package org.congcong.algomentor.cache.metrics;

public enum CacheRequestResult {
  HIT,
  MISS;

  public String tagValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
