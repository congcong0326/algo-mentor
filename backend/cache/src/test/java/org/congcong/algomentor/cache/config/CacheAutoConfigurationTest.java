package org.congcong.algomentor.cache.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.congcong.algomentor.cache.caffeine.BypassLocalCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.BypassSharedCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.CaffeineLocalCacheRegionFactory;
import org.congcong.algomentor.cache.caffeine.CaffeineSharedCacheRegionFactory;
import org.congcong.algomentor.cache.factory.LocalCacheRegionFactory;
import org.congcong.algomentor.cache.factory.SharedCacheRegionFactory;
import org.congcong.algomentor.cache.metrics.MicrometerCacheMetrics;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

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
}
