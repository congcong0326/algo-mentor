package org.congcong.algomentor.cache.config;

/** 缓存模块公共配置键。 */
public final class CacheConfigurationKeys {

  public static final String PREFIX = "algo-mentor.cache";
  public static final String ENABLED = PREFIX + ".enabled";
  public static final String METRICS_ENABLED = PREFIX + ".metrics-enabled";
  public static final String SHARED_PROVIDER = PREFIX + ".shared-provider";

  private CacheConfigurationKeys() {
  }
}
