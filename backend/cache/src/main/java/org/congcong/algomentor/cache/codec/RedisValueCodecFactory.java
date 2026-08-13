package org.congcong.algomentor.cache.codec;

/** 创建受限 JSON 和标量 Redis value codec 的工厂。 */
public interface RedisValueCodecFactory {

  <V> RedisValueCodec<V> json(Class<V> valueType);

  RedisValueCodec<Boolean> booleanAsZeroOrOne();
}
