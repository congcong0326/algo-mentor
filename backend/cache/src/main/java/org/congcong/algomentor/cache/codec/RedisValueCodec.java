package org.congcong.algomentor.cache.codec;

/** Redis value 的显式编码契约；实现不得返回 null 或空字节数组。 */
public interface RedisValueCodec<V> {

  byte[] encode(V value);

  V decode(byte[] bytes);
}
