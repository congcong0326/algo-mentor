package org.congcong.algomentor.cache.redis;

import java.util.Objects;
import org.congcong.algomentor.cache.spec.RedisTtlCacheSpec;
import org.congcong.algomentor.cache.spec.SharedCacheKeyCodec;

/** Redis 物理 key 的唯一构造入口，调用方不得记录返回值或其中的 token。 */
public final class RedisCacheKeyBuilder {

  private static final String PREFIX = "algo-mentor:cache:v";

  public String physicalKey(RedisTtlCacheSpec spec, String keyToken) {
    Objects.requireNonNull(spec, "spec must not be null");
    keyToken = SharedCacheKeyCodec.requireValidToken(keyToken);
    return PREFIX + spec.schemaVersion() + ':' + spec.namespace() + ':' + keyToken;
  }
}
