package org.congcong.algomentor.cache.metrics;

/** Redis 缓存允许记录的固定命令类型。 */
public enum RedisCacheOperation {
  GET("get"),
  SET("set"),
  DEL("del");

  private final String tagValue;

  RedisCacheOperation(String tagValue) { this.tagValue = tagValue; }

  public String tagValue() { return tagValue; }
}
