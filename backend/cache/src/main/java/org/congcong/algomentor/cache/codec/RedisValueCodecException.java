package org.congcong.algomentor.cache.codec;

/** 表示可丢弃 Redis 副本无法安全编码或解码，不代表业务数据错误。 */
public final class RedisValueCodecException extends RuntimeException {

  public RedisValueCodecException(String message, Throwable cause) {
    super(message, cause);
  }

  public RedisValueCodecException(String message) {
    super(message);
  }
}
