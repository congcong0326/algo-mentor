package org.congcong.algomentor.api.practice.realtime;

/** Redis Stream 不可用；调用方只可回退到 PostgreSQL 最终状态。 */
public final class PracticeRealtimeUnavailableException extends RuntimeException {

  public PracticeRealtimeUnavailableException(String message) {
    super(message);
  }

  public PracticeRealtimeUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
