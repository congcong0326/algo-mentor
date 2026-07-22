package org.congcong.algomentor.cache.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.congcong.algomentor.cache.caffeine.BypassLocalCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.BypassSharedCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.CaffeineLocalCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.CaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.coherence.LocalSharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.coherence.SharedCacheInvalidationCoordinator;
import org.congcong.algomentor.cache.coherence.postgres.PostgresCoherentCaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.coherence.postgres.PostgresSharedInvalidationPoller;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.SharedTtlCacheSpec;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.MicrometerCacheMetrics;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import javax.sql.DataSource;

class CacheAutoConfigurationTest {

  private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
      .withConfiguration(AutoConfigurations.of(CacheAutoConfiguration.class));

  @Test
  void providesCaffeineFactoriesAndNoopMetricsWithoutMeterRegistry() {
    contextRunner.run(context -> {
      assertThat(context).hasSingleBean(LocalCacheRegionFactory.class);
      assertThat(context).hasSingleBean(SharedCacheRegionFactory.class);
      assertThat(context.getBean(LocalCacheRegionFactory.class)).isInstanceOf(CaffeineLocalCacheRegionFactory.class);
      assertThat(context.getBean(SharedCacheRegionFactory.class)).isInstanceOf(CaffeineSharedCacheRegionFactory.class);
      assertThat(context.getBean(org.congcong.algomentor.cache.metrics.CacheMetrics.class))
          .isInstanceOf(NoopCacheMetrics.class);
    });
  }

  @Test
  void createsBypassFactoriesWhenCacheIsDisabled() {
    contextRunner.withPropertyValues(CacheConfigurationKeys.ENABLED + "=false").run(context -> {
      assertThat(context.getBean(LocalCacheRegionFactory.class)).isInstanceOf(BypassLocalCacheRegionFactory.class);
      assertThat(context.getBean(SharedCacheRegionFactory.class)).isInstanceOf(BypassSharedCacheRegionFactory.class);
    });
  }

  @Test
  void usesMicrometerWhenRegistryAndMetricsAreEnabled() {
    contextRunner.withBean(MeterRegistry.class, SimpleMeterRegistry::new).run(context ->
        assertThat(context.getBean(org.congcong.algomentor.cache.metrics.CacheMetrics.class))
            .isInstanceOf(MicrometerCacheMetrics.class));
  }

  @Test
  void rejectsUnsupportedSharedProvider() {
    contextRunner.withPropertyValues(CacheConfigurationKeys.SHARED_PROVIDER + "=redis").run(context ->
        assertThat(context).hasFailed());
  }

  @Test
  void configuresPostgresCoherentProviderWhenDataSourceAndSpringJdbcAreAvailable() {
    DataSource dataSource = new DriverManagerDataSource("jdbc:invalid:cache-test");
    contextRunner
        .withPropertyValues(
            CacheConfigurationKeys.SHARED_PROVIDER + "=postgres-coherent-caffeine",
            CacheConfigurationKeys.COHERENCE_ENABLED + "=false")
        .withBean(DataSource.class, () -> dataSource)
        .run(context -> {
          assertThat(context.getBean(SharedCacheRegionFactory.class))
              .isInstanceOf(PostgresCoherentCaffeineSharedCacheRegionFactory.class);
          assertThat(context.getBean(SharedCacheInvalidationCoordinator.class))
              .isInstanceOf(LocalSharedCacheInvalidationCoordinator.class);
          assertThat(context).doesNotHaveBean(PostgresSharedInvalidationPoller.class);
        });
  }

  @Test
  void failsFastWhenPostgresCoherentProviderHasNoDataSource() {
    contextRunner
        .withPropertyValues(CacheConfigurationKeys.SHARED_PROVIDER + "=postgres-coherent-caffeine")
        .run(context -> assertThat(context).hasFailed());
  }

  @Test
  void rejectsSharedTtlThatIsNotCoveredByEventRetention() {
    DataSource dataSource = new DriverManagerDataSource("jdbc:invalid:cache-test");
    contextRunner
        .withPropertyValues(
            CacheConfigurationKeys.SHARED_PROVIDER + "=postgres-coherent-caffeine",
            CacheConfigurationKeys.COHERENCE_ENABLED + "=false",
            CacheConfigurationKeys.COHERENCE_EVENT_RETENTION + "=30s")
        .withBean(DataSource.class, () -> dataSource)
        .run(context -> {
          SharedCacheRegionFactory factory = context.getBean(SharedCacheRegionFactory.class);
          org.assertj.core.api.Assertions.assertThatThrownBy(() -> factory.createTtl(
              new SharedTtlCacheSpec(
                  new CacheRegionName("retention-check"), "retention-check", 1, 1,
                  java.time.Duration.ofSeconds(30)),
              (String key) -> key))
              .isInstanceOf(IllegalArgumentException.class)
              .hasMessageContaining("eventRetention");
        });
  }
}
