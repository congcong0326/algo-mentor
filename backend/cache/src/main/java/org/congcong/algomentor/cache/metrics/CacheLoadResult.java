package org.congcong.algomentor.cache.metrics;

public enum CacheLoadResult {
  SUCCESS,
  FAILURE;

  public String tagValue() {
    return name().toLowerCase(java.util.Locale.ROOT);
  }
}
