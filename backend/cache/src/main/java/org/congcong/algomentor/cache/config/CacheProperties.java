package org.congcong.algomentor.cache.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = CacheConfigurationKeys.PREFIX)
public class CacheProperties {

  private boolean enabled = true;
  private boolean metricsEnabled = true;
  private SharedCacheProvider sharedProvider = SharedCacheProvider.CAFFEINE;

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(boolean enabled) {
    this.enabled = enabled;
  }

  public boolean isMetricsEnabled() {
    return metricsEnabled;
  }

  public void setMetricsEnabled(boolean metricsEnabled) {
    this.metricsEnabled = metricsEnabled;
  }

  public SharedCacheProvider getSharedProvider() {
    return sharedProvider;
  }

  public void setSharedProvider(SharedCacheProvider sharedProvider) {
    this.sharedProvider = sharedProvider;
  }
}
