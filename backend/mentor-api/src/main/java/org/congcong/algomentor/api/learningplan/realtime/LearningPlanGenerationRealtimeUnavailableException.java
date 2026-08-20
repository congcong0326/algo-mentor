package org.congcong.algomentor.api.learningplan.realtime;

/** Redis Stream 不可用、过期或无法安全解码时的受控降级异常。 */
public final class LearningPlanGenerationRealtimeUnavailableException extends RuntimeException {

  public LearningPlanGenerationRealtimeUnavailableException(String message) {
    super(message);
  }

  public LearningPlanGenerationRealtimeUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
