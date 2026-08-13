package org.congcong.algomentor.cache.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import org.congcong.algomentor.cache.api.RedisTtlCacheRegion;
import org.congcong.algomentor.cache.config.CacheProperties;
import org.congcong.algomentor.cache.metrics.NoopCacheMetrics;
import org.congcong.algomentor.cache.metrics.RedisCacheMetrics;
import org.congcong.algomentor.cache.redis.codec.JacksonRedisValueCodecFactory;
import org.congcong.algomentor.cache.registry.CacheRegionRegistry;
import org.congcong.algomentor.cache.spec.CacheRegionName;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
class LettuceRedisTtlCacheRegionIT {

  @Container
  static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine"))
      .withExposedPorts(6379);

  @Test
  void storesValuesWithTtlAndBypassesOversizeValues() throws InterruptedException {
    CacheProperties.Redis properties = new CacheProperties.Redis();
    properties.setHost(REDIS.getHost());
    properties.setPort(REDIS.getMappedPort(6379));
    try (RedisConnectionManager connectionManager = new RedisConnectionManager(properties)) {
      LettuceRedisCacheRegionFactory factory = new LettuceRedisCacheRegionFactory(
          new CacheRegionRegistry(), connectionManager, 16, new NoopCacheMetrics(), RedisCacheMetrics.noop());
      JacksonRedisValueCodecFactory codecs = JacksonRedisValueCodecFactory.standalone();
      RedisTtlCacheRegion<String, String> region = factory.createTtl(
          new RedisTtlCacheSpec(new CacheRegionName("redis-it"), "redis-it", 1,
              Duration.ofMillis(100)),
          key -> key,
          codecs.json(String.class));
      AtomicInteger loads = new AtomicInteger();

      assertThat(region.get("key", ignored -> "value-" + loads.incrementAndGet())).isEqualTo("value-1");
      assertThat(region.get("key", ignored -> "value-" + loads.incrementAndGet())).isEqualTo("value-1");
      Thread.sleep(200);
      assertThat(region.get("key", ignored -> "value-" + loads.incrementAndGet())).isEqualTo("value-2");

      connectionManager.commands().set(
          "algo-mentor:cache:v1:redis-it:bad".getBytes(),
          "not-json".getBytes(),
          io.lettuce.core.SetArgs.Builder.px(Duration.ofSeconds(1).toMillis()));
      assertThat(region.get("bad", ignored -> "reloaded")).isEqualTo("reloaded");

      region.put("oversize", "this value exceeds sixteen bytes");
      assertThat(region.getIfPresent("oversize")).isEmpty();
      region.invalidate("key");
      assertThat(region.getIfPresent("key")).isEmpty();
    }
  }
}
