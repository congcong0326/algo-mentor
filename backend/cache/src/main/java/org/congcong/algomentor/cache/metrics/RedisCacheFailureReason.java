package org.congcong.algomentor.cache.metrics;

/** Redis 缓存旁路的固定、低基数故障原因。 */
public enum RedisCacheFailureReason {
  TIMEOUT("timeout"),
  CONNECTION("connection"),
  CODEC("codec"),
  COMMAND("command"),
  OVERSIZE("oversize");

  private final String tagValue;

  RedisCacheFailureReason(String tagValue) { this.tagValue = tagValue; }

  public String tagValue() { return tagValue; }
}
