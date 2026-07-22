package org.congcong.algomentor.cache.config;

import io.micrometer.core.instrument.MeterRegistry;
import org.congcong.algomentor.cache.caffeine.BypassLocalCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.BypassSharedCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.CaffeineLocalCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.CaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.invalidation.SpringCacheInvalidationExecutor;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.metrics.MicrometerCacheMetrics;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@EnableConfigurationProperties(CacheProperties.class)
public class CacheAutoConfiguration {

  @Bean
  @ConditionalOnMissingBean
  public CacheMetrics cacheMetrics(CacheProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    if (!properties.isMetricsEnabled() || registry == null) {
      return new NoopCacheMetrics();
    }
    return new MicrometerCacheMetrics(registry);
  }

  @Bean
  @ConditionalOnMissingBean
  public CacheRegionRegistry cacheRegionRegistry() {
    return new CacheRegionRegistry();
  }

  @Bean
  @ConditionalOnMissingBean
  public LocalCacheRegionFactory localCacheRegionFactory(
      CacheProperties properties,
      CacheRegionRegistry registry,
      CacheMetrics metrics) {
    if (!properties.isEnabled()) {
      return new BypassLocalCacheRegionFactory(registry);
    }
    return new CaffeineLocalCacheRegionFactory(registry, metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public SharedCacheRegionFactory sharedCacheRegionFactory(
      CacheProperties properties,
      CacheRegionRegistry registry,
      CacheMetrics metrics) {
    if (!properties.isEnabled()) {
      return new BypassSharedCacheRegionFactory(registry);
    }
    return new CaffeineSharedCacheRegionFactory(registry, metrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public CacheInvalidationExecutor cacheInvalidationExecutor() {
    return new SpringCacheInvalidationExecutor();
  }
}
