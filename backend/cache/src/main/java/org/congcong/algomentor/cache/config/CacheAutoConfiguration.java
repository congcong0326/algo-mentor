package org.congcong.algomentor.cache.config;

import io.micrometer.core.instrument.MeterRegistry;
import javax.sql.DataSource;
import org.congcong.algomentor.cache.caffeine.BypassLocalCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.BypassSharedCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.CaffeineLocalCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.CaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.coherence.LocalSharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationEventStore;
import org.congcong.algomentor.cache.coherence.TransactionalSharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.coherence.postgres.JdbcSharedCacheInvalidationEventStore;
import org.congcong.algomentor.cache.coherence.postgres.PostgresCoherentCaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.coherence.postgres.PostgresSharedInvalidationPoller;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.invalidation.CacheInvalidationExecutor;
import org.congcong.algomentor.cache.invalidation.SpringCacheInvalidationExecutor;
import org.congcong.algomentor.cache.metrics.CacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.CacheMetrics;
import org.congcong.algomentor.cache.metrics.MicrometerCacheCoherenceMetrics;
import org.congcong.algomentor.cache.metrics.MicrometerCacheMetrics;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.registry.SharedCacheInvalidationTargetRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

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
  public CacheCoherenceMetrics cacheCoherenceMetrics(
      CacheProperties properties,
      ObjectProvider<MeterRegistry> meterRegistry) {
    MeterRegistry registry = meterRegistry.getIfAvailable();
    if (!properties.isMetricsEnabled() || registry == null) {
      return CacheCoherenceMetrics.noop();
    }
    return new MicrometerCacheCoherenceMetrics(registry);
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
  @ConditionalOnProperty(
      name = CacheConfigurationKeys.SHARED_PROVIDER,
      havingValue = "caffeine",
      matchIfMissing = true)
  public SharedCacheRegionFactory caffeineSharedCacheRegionFactory(
      CacheProperties properties,
      CacheRegionRegistry registry,
      CacheMetrics metrics,
      CacheCoherenceMetrics coherenceMetrics) {
    if (!properties.isEnabled()) {
      return new BypassSharedCacheRegionFactory(registry);
    }
    return new CaffeineSharedCacheRegionFactory(registry, metrics, coherenceMetrics);
  }

  @Bean
  @ConditionalOnMissingBean
  public CacheInvalidationExecutor cacheInvalidationExecutor() {
    return new SpringCacheInvalidationExecutor();
  }

  @Bean
  @ConditionalOnMissingBean
  @ConditionalOnProperty(
      name = CacheConfigurationKeys.SHARED_PROVIDER,
      havingValue = "caffeine",
      matchIfMissing = true)
  public SharedCacheInvalidationCoordinator localSharedCacheInvalidationCoordinator(
      CacheInvalidationExecutor invalidationExecutor) {
    return new LocalSharedCacheInvalidationCoordinator(invalidationExecutor);
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnProperty(
      name = CacheConfigurationKeys.SHARED_PROVIDER,
      havingValue = "postgres-coherent-caffeine")
  @ConditionalOnMissingBean(DataSource.class)
  static class MissingPostgresDataSourceConfiguration {

    @Bean
    @ConditionalOnMissingBean(SharedCacheRegionFactory.class)
    SharedCacheRegionFactory missingPostgresDataSourceSharedCacheRegionFactory() {
      throw new IllegalStateException(
          "shared-provider=postgres-coherent-caffeine requires a DataSource and Spring JDBC");
    }
  }

  @Configuration(proxyBeanMethods = false)
  @ConditionalOnClass(JdbcTemplate.class)
  @ConditionalOnBean(DataSource.class)
  @ConditionalOnProperty(
      name = CacheConfigurationKeys.SHARED_PROVIDER,
      havingValue = "postgres-coherent-caffeine")
  static class PostgresCoherenceConfiguration {

    @Bean
    @ConditionalOnMissingBean
    SharedCacheInvalidationTargetRegistry sharedCacheInvalidationTargetRegistry() {
      return new SharedCacheInvalidationTargetRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    SharedCacheInvalidationEventStore sharedCacheInvalidationEventStore(DataSource dataSource) {
      return new JdbcSharedCacheInvalidationEventStore(new JdbcTemplate(dataSource));
    }

    @Bean
    @ConditionalOnMissingBean(SharedCacheRegionFactory.class)
    SharedCacheRegionFactory postgresSharedCacheRegionFactory(
        CacheProperties properties,
        CacheRegionRegistry registry,
        SharedCacheInvalidationTargetRegistry invalidationTargets,
        CacheMetrics metrics,
        CacheCoherenceMetrics coherenceMetrics) {
      if (!properties.isEnabled()) {
        return new BypassSharedCacheRegionFactory(registry, invalidationTargets);
      }
      return new PostgresCoherentCaffeineSharedCacheRegionFactory(
          registry, invalidationTargets, metrics, coherenceMetrics);
    }

    @Bean
    @ConditionalOnMissingBean(SharedCacheInvalidationCoordinator.class)
    SharedCacheInvalidationCoordinator postgresSharedCacheInvalidationCoordinator(
        CacheProperties properties,
        SharedCacheInvalidationEventStore eventStore,
        CacheInvalidationExecutor invalidationExecutor,
        CacheCoherenceMetrics metrics) {
      if (!properties.getCoherence().isEnabled()) {
        return new LocalSharedCacheInvalidationCoordinator(invalidationExecutor);
      }
      return new TransactionalSharedCacheInvalidationCoordinator(
          eventStore, invalidationExecutor, metrics);
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(
        name = CacheConfigurationKeys.COHERENCE_ENABLED,
        havingValue = "true",
        matchIfMissing = true)
    PostgresSharedInvalidationPoller postgresSharedInvalidationPoller(
        SharedCacheInvalidationEventStore eventStore,
        SharedCacheInvalidationTargetRegistry invalidationTargets,
        CacheCoherenceMetrics metrics,
        CacheProperties properties) {
      return new PostgresSharedInvalidationPoller(
          eventStore, invalidationTargets, metrics, properties.getCoherence());
    }
  }
}
