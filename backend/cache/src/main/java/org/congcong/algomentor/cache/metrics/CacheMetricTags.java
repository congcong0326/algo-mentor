package org.congcong.algomentor.cache.metrics;

/** 缓存模块对外暴露的稳定 Micrometer 标签名。 */
public final class CacheMetricTags {

  public static final String CACHE = "cache";
  public static final String RESULT = "result";
  public static final String TYPE = "type";
  public static final String CAUSE = "cause";

  private CacheMetricTags() {
  }
}
